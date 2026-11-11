package com.downloader.app.engine

import android.content.Context
import android.net.Uri
import com.downloader.app.data.AppError
import com.downloader.app.data.DirectFile
import com.downloader.app.data.ErrorType
import com.downloader.app.data.MediaInfo
import com.downloader.app.data.MediaKind
import com.downloader.app.data.Mime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/** التحميل المباشر للروابط التي تشير إلى ملف (صورة/فيديو/صوت/أي ملف) + استخراج وسائط الصفحة كحل احتياطي. */
object Direct {
    private val calls = ConcurrentHashMap<String, Call>()

    /** منصات معروفة تذهب مباشرة إلى yt-dlp بدون فحص HEAD. */
    private val platformHosts = listOf(
        "youtube.com", "youtu.be", "instagram.com", "tiktok.com", "twitter.com", "x.com", "facebook.com", "fb.watch",
        "fb.com", "snapchat.com", "pinterest.", "pin.it", "vimeo.com", "dailymotion.com", "twitch.tv", "reddit.com",
        "soundcloud.com", "threads.net", "threads.com", "likee.video", "kwai.com", "t.co",
    )

    private fun isPlatform(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        return platformHosts.any { host == it || host.endsWith(".$it") || (it.endsWith(".") && host.contains(it)) }
    }

    private fun request(url: String) = Request.Builder().url(url)
        .header("User-Agent", Net.BROWSER_UA).header("Accept", "*/*")

    private fun fileNameOf(url: String, response: Response?, mime: String?): String {
        val cd = response?.header("Content-Disposition").orEmpty()
        val fromCd = Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
        var name = fromCd ?: Uri.parse(url).lastPathSegment.orEmpty()
        name = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().takeLast(90)
        if (name.isBlank()) name = "file-${System.currentTimeMillis()}"
        if (!name.contains('.') || name.substringAfterLast('.').length > 5) {
            Mime.extensionOf(mime)?.let { name = "$name.$it" }
        }
        return name
    }

    /** يفحص إن كان الرابط ملفاً مباشراً. يعيد null إن كان صفحة ويب (يُترك لـ yt-dlp). */
    suspend fun probe(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        if (isPlatform(url)) return@withContext null
        val ext = Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
        val response = try {
            var r = Net.client.newCall(request(url).head().build()).execute()
            if (!r.isSuccessful) {
                r.close()
                r = Net.client.newCall(request(url).header("Range", "bytes=0-0").build()).execute()
            }
            r
        } catch (e: IOException) {
            return@withContext null
        }
        response.use { r ->
            if (!r.isSuccessful) return@withContext null
            val mime = r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            val byExt = ext in Mime.mediaExtensions
            val kind = Mime.kindOf(mime).takeIf { it != MediaKind.OTHER } ?: if (byExt) Mime.kindOf(Mime.ofExtension(ext)) else MediaKind.OTHER
            val isFile = kind != MediaKind.OTHER || r.header("Content-Disposition")?.contains("attachment", true) == true ||
                (mime != null && (mime == "application/octet-stream" || mime == "application/pdf" || mime == "application/zip"))
            if (!isFile || mime == "application/vnd.apple.mpegurl" || mime == "application/x-mpegurl" || ext == "m3u8" || ext == "mpd") {
                return@withContext null
            }
            val size = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: r.header("Content-Length")?.toLongOrNull()?.takeIf { it > 1 }
            val effectiveMime = mime?.takeIf { Mime.kindOf(it) != MediaKind.OTHER } ?: if (byExt) Mime.ofExtension(ext) else mime
            val name = fileNameOf(r.request.url.toString(), r, effectiveMime)
            MediaInfo(
                sourceUrl = url,
                title = name,
                uploader = Uri.parse(url).host,
                thumbnail = if (kind == MediaKind.IMAGE) url else null,
                durationSec = null,
                platform = "رابط مباشر",
                hasAudio = false,
                directFiles = listOf(DirectFile(url, name, effectiveMime, size, kind)),
            )
        }
    }

    /**
     * حل احتياطي عندما لا يدعم yt-dlp الصفحة (غالباً منشورات الصور): يقرأ وسوم og:video / og:image.
     */
    suspend fun scrapeOpenGraph(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        val html = try {
            Net.client.newCall(request(url).header("Accept", "text/html").build()).execute().use { r ->
                if (!r.isSuccessful || r.header("Content-Type")?.contains("html") != true) return@withContext null
                r.peekBody(3L * 1024 * 1024).string()
            }
        } catch (e: IOException) {
            return@withContext null
        }
        fun meta(prop: String): List<String> {
            val p = Regex.escape(prop)
            val a = Regex("""<meta[^>]+(?:property|name)=["']$p["'][^>]*?content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val b = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*?(?:property|name)=["']$p["']""", RegexOption.IGNORE_CASE)
            return (a.findAll(html) + b.findAll(html)).map { it.groupValues[1].replace("&amp;", "&") }.distinct().toList()
        }
        val videos = (meta("og:video:secure_url") + meta("og:video:url") + meta("og:video")).distinct()
            .filter { it.startsWith("http") && !it.contains("/embed") }
        val images = (meta("og:image:secure_url") + meta("og:image")).distinct().filter { it.startsWith("http") }
        val title = meta("og:title").firstOrNull() ?: Uri.parse(url).host.orEmpty()
        val files = when {
            videos.isNotEmpty() -> videos.take(1).map { DirectFile(it, fileNameOf(it, null, "video/mp4"), "video/mp4", null, MediaKind.VIDEO) }
            images.isNotEmpty() -> images.take(1).map { DirectFile(it, fileNameOf(it, null, "image/jpeg"), "image/jpeg", null, MediaKind.IMAGE) }
            else -> return@withContext null
        }
        MediaInfo(
            sourceUrl = url, title = title, uploader = Uri.parse(url).host, thumbnail = images.firstOrNull(),
            durationSec = null, platform = "وسائط الصفحة", hasAudio = false, directFiles = files,
        )
    }

    /** تحميل مباشر (حاجز) مع استكمال عبر Range. */
    fun download(context: Context, id: String, url: String, dest: File, onProgress: (YtDlp.Progress) -> Unit) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var existing = if (part.exists()) part.length() else 0L
        val builder = request(url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        val call = Net.client.newCall(builder.build())
        calls[id] = call
        try {
            call.execute().use { r ->
                if (r.code == 416 && existing > 0) { part.delete(); throw IOException("retry") }
                if (!r.isSuccessful) throw AppError(if (r.code in listOf(401, 403)) ErrorType.PRIVATE else ErrorType.GENERIC, "HTTP ${r.code}")
                if (r.code != 206) existing = 0
                val body = r.body
                val total = body.contentLength().takeIf { it > 0 }?.plus(existing)
                RandomAccessFile(part, "rw").use { raf ->
                    raf.setLength(existing)
                    raf.seek(existing)
                    val buf = ByteArray(128 * 1024)
                    var done = existing
                    var windowStart = System.nanoTime()
                    var windowBytes = 0L
                    var speed: String? = null
                    var eta: Long? = null
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            done += n
                            windowBytes += n
                            val dt = (System.nanoTime() - windowStart) / 1e9
                            if (dt >= 0.5) {
                                val bps = windowBytes / dt
                                speed = com.downloader.app.data.Fmt.speedOf(bps)
                                eta = total?.let { ((it - done) / bps.coerceAtLeast(1.0)).toLong() }
                                windowStart = System.nanoTime(); windowBytes = 0
                            }
                            onProgress(YtDlp.Progress(total?.let { done.toFloat() / it } ?: -1f, speed, eta, "تحميل مباشر"))
                        }
                    }
                }
            }
            if (!part.renameTo(dest)) throw IOException("تعذّر حفظ الملف")
        } catch (e: AppError) {
            throw e
        } catch (e: IOException) {
            if (call.isCanceled()) throw InterruptedException("canceled")
            throw AppError(if (Net.isOnline(context)) ErrorType.GENERIC else ErrorType.NO_INTERNET, e.message)
        } finally {
            calls.remove(id)
        }
    }

    fun cancel(id: String) {
        calls[id]?.cancel()
    }
}
