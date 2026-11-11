package com.downloader.app.engine

import android.content.Context
import com.downloader.app.auth.CookieJarStore
import com.downloader.app.data.AppError
import com.downloader.app.data.DownloadSpec
import com.downloader.app.data.ErrorType
import com.downloader.app.data.Fmt
import com.downloader.app.data.MediaInfo
import com.downloader.app.data.MediaKind
import com.downloader.app.data.Mime
import com.downloader.app.data.PlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/**
 * gallery-dl (https://github.com/mikf/gallery-dl): يكمّل yt-dlp في منشورات الصور والكاروسيل
 * (انستجرام، تويتر/X، بنترست...). يعمل ببايثون المدمجة نفسها كعملية مستقلة على الجهاز.
 * الحزمة (gallery-dl + requests وتوابعها) موجودة في assets/pytools.zip وتُفكّ عند أول استخدام.
 */
object GalleryDl {
    /** غيّر هذا الرقم عند تحديث assets/pytools.zip حتى يُعاد فكّها. */
    private const val BUNDLE_VERSION = "gallery-dl-1.32.19"
    private const val RESOLVE_TIMEOUT_MS = 180_000L
    private const val MAX_ITEMS = 200

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val processes = ConcurrentHashMap<String, Process>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    private fun toolsDir(context: Context): File {
        val root = File(context.noBackupFilesDir, "pytools")
        val dir = File(root, BUNDLE_VERSION)
        val marker = File(dir, ".ready")
        if (marker.exists()) return dir
        root.deleteRecursively()
        dir.mkdirs()
        context.assets.open("pytools.zip").use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(dir, entry.name)
                    if (!out.canonicalPath.startsWith(dir.canonicalPath + File.separator)) continue
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        marker.createNewFile()
        return dir
    }

    /** يشغّل `python -m gallery_dl` بالبيئة نفسها التي تستخدمها youtubedl-android. */
    private fun start(context: Context, args: List<String>): Process {
        val base = File(context.noBackupFilesDir, "youtubedl-android")
        val pythonHome = File(base, "packages/python/usr")
        val ffmpegLib = File(base, "packages/ffmpeg/usr/lib")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val home = File(context.cacheDir, "gallery-dl").apply { mkdirs() }
        // إضافة yt-dlp إلى المسار تسمح لـ gallery-dl بتفويض مقاطع الفيديو إليه عند الحاجة.
        val ytdlp = File(base, "yt-dlp/yt-dlp")
        val command = listOf(File(nativeDir, "libpython.so").absolutePath, "-m", "gallery_dl",
            "--config-ignore", "--no-colors", "--no-input") + args
        return ProcessBuilder(command).apply {
            environment().apply {
                put("LD_LIBRARY_PATH", "${pythonHome.absolutePath}/lib:${ffmpegLib.absolutePath}")
                put("SSL_CERT_FILE", "${pythonHome.absolutePath}/etc/tls/cert.pem")
                put("PYTHONHOME", pythonHome.absolutePath)
                put("PYTHONPATH", toolsDir(context).absolutePath + File.pathSeparator + ytdlp.absolutePath)
                put("PATH", (System.getenv("PATH") ?: "") + File.pathSeparator + nativeDir)
                put("HOME", home.absolutePath)
                put("TMPDIR", context.cacheDir.absolutePath)
            }
        }.start()
    }

    private class Output(val exit: Int, val out: String, val err: String)

    private fun run(context: Context, id: String, args: List<String>, onLine: ((String) -> Unit)? = null): Output {
        val process = start(context, args)
        processes[id] = process
        val err = StringBuilder()
        val out = StringBuilder()
        val errReader = thread { runCatching { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } } }
        runCatching {
            process.inputStream.bufferedReader().forEachLine { line ->
                if (onLine != null) onLine(line) else out.appendLine(line)
            }
        }
        val exit = process.waitFor()
        errReader.join(2000)
        processes.remove(id)
        return Output(exit, out.toString(), err.toString())
    }

    fun cancel(id: String) {
        cancelled += id
        processes[id]?.destroy()
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        when (val v = this[k]) {
            is JsonPrimitive -> v.content.takeIf { v.isString && it.isNotBlank() }
            is JsonObject -> v.text("name", "nick", "username", "full_name")
            else -> null
        }
    }

    /** يحاول استخراج وسائط الرابط. يعيد null إن لم يجد شيئاً؛ ويرمي AppError إذا كان السبب واضحاً (خاص/غير مدعوم). */
    suspend fun resolve(context: Context, url: String): MediaInfo? = withContext(Dispatchers.IO) {
        YtDlp.awaitEngine()
        val id = "gdl-" + UUID.randomUUID()
        val cookies = CookieJarStore.copyFor(context, id)
        try {
            val args = buildList {
                add("-j"); add("--range"); add("1-$MAX_ITEMS")
                add("-R"); add("2"); add("--http-timeout"); add("30")
                if (cookies != null) { add("--cookies"); add(cookies.absolutePath) }
                add(url)
            }
            val result = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                val job = async(Dispatchers.IO) { run(context, id, args) }
                try {
                    job.await()
                } catch (c: CancellationException) {
                    cancel(id)
                    throw c
                }
            } ?: return@withContext null
            val messages = runCatching { json.parseToJsonElement(result.out.trim()) as JsonArray }.getOrNull()
                ?: throw YtDlp.classify(context, result.err.replace("[error]", "ERROR:"))

            var meta: JsonObject? = null
            val items = mutableListOf<PlaylistItem>()
            var firstImage: String? = null
            var errorText: String? = null
            for (m in messages) {
                val arr = m as? JsonArray ?: continue
                when ((arr.firstOrNull() as? JsonPrimitive)?.intOrNull) {
                    2 -> if (meta == null) meta = arr.getOrNull(1) as? JsonObject
                    3 -> {
                        val fileUrl = (arr.getOrNull(1) as? JsonPrimitive)?.content ?: continue
                        val kw = arr.getOrNull(2) as? JsonObject
                        if (meta == null) meta = kw
                        val ext = kw?.text("extension") ?: fileUrl.substringBefore('?').substringAfterLast('.', "")
                        val kind = Mime.kindOf(Mime.ofExtension(ext))
                        val n = items.size + 1
                        val isImage = kind == MediaKind.IMAGE && fileUrl.startsWith("http")
                        if (isImage && firstImage == null) firstImage = fileUrl
                        items += PlaylistItem(
                            index = n,
                            title = Fmt.iso(
                                when (kind) {
                                    MediaKind.VIDEO -> "فيديو $n"
                                    MediaKind.AUDIO -> "صوت $n"
                                    MediaKind.IMAGE -> "صورة $n"
                                    MediaKind.OTHER -> "ملف $n"
                                }
                            ) + if (ext.isNotBlank() && ext.length <= 5) "  ·  " + Fmt.iso(ext.uppercase()) else "",
                            thumbnail = if (isImage) fileUrl else null,
                            durationSec = null,
                        )
                    }
                    -1 -> errorText = (arr.getOrNull(1) as? JsonObject)?.let { "${it.text("error")}: ${it.text("message")}" }
                }
            }
            if (items.isEmpty()) {
                val all = listOfNotNull(errorText?.let { "ERROR: $it" }, result.err.replace("[error]", "ERROR:")).joinToString("\n")
                if (all.contains("ERROR")) throw YtDlp.classify(context, all)
                return@withContext null
            }
            MediaInfo(
                sourceUrl = url,
                title = (meta?.text("title", "description", "content", "caption") ?: items.first().title)
                    .lineSequence().first().take(90),
                uploader = meta?.text("username", "author", "user", "owner", "uploader", "fullname"),
                thumbnail = firstImage,
                durationSec = null,
                platform = meta?.text("category")?.replaceFirstChar { it.uppercase() },
                hasAudio = false,
                isPlaylist = true,
                items = items,
                viaGallery = true,
            )
        } finally {
            cancelled -= id
            cookies?.delete()
        }
    }

    /** تحميل العناصر المختارة (حاجز). الإيقاف عبر [cancel]؛ الملفات المكتملة تُتخطّى عند الاستكمال. */
    fun download(context: Context, id: String, spec: DownloadSpec, dir: File, onProgress: (YtDlp.Progress) -> Unit) {
        dir.mkdirs()
        val cookies = CookieJarStore.copyFor(context, id)
        val picked = spec.playlistItems.orEmpty()
        val total = picked.size.coerceAtLeast(1)
        var done = 0
        cancelled -= id
        try {
            val args = buildList {
                add("-D"); add(dir.absolutePath)
                if (picked.isNotEmpty()) { add("--range"); add(picked.joinToString(",")) }
                add("--no-mtime"); add("-R"); add("3"); add("--http-timeout"); add("30")
                if (cookies != null) { add("--cookies"); add(cookies.absolutePath) }
                add(spec.url)
            }
            onProgress(YtDlp.Progress(0f, null, null, "جاري التحضير"))
            val result = run(context, id, args) { line ->
                if (line.isNotBlank()) {
                    done++
                    onProgress(YtDlp.Progress((done.toFloat() / total).coerceAtMost(0.99f), null, null, "تحميل ($done/$total)"))
                }
            }
            if (id in cancelled) throw InterruptedException("canceled")
            if (YtDlp.outputFiles(dir).isEmpty()) {
                throw YtDlp.classify(context, result.err.replace("[error]", "ERROR:").ifBlank { "exit ${result.exit}" })
            }
        } finally {
            cancelled -= id
            cookies?.delete()
        }
    }
}
