package com.downloader.app.engine

import android.content.Context
import com.downloader.app.data.AppError
import com.downloader.app.data.DownloadMode
import com.downloader.app.data.DownloadSpec
import com.downloader.app.data.DownloadTask
import com.downloader.app.data.ErrorType
import com.downloader.app.data.Library
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.MediaKind
import com.downloader.app.data.Mime
import com.downloader.app.data.SettingsStore
import com.downloader.app.data.TaskState
import com.downloader.app.data.message
import com.downloader.app.service.DownloadService
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * طابور التحميلات: تحميلات متعددة بالتوازي (حسب الإعدادات) مع إيقاف مؤقت/استكمال/إلغاء/إعادة محاولة.
 * الحالة تُحفظ على القرص حتى لا تضيع المهام المتوقفة عند إغلاق التطبيق.
 */
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    /** يُبث عند اكتمال مهمة: (عنوان، عدد الملفات). */
    private val _completed = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 16)
    val completed: SharedFlow<Pair<String, Int>> = _completed

    /** تنبيهات غير فاشلة تُعرض للمستخدم (مثل: اكتمل التحميل بلا ترجمة). */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices

    private val jobs = ConcurrentHashMap<String, Job>()
    private val stopReason = ConcurrentHashMap<String, TaskState?>() // PAUSED أو null (إلغاء)
    private val lastEmit = ConcurrentHashMap<String, Long>()
    private lateinit var app: Context
    private lateinit var stateFile: File
    @Volatile private var maxParallel = 3

    fun init(context: Context) {
        app = context.applicationContext
        stateFile = File(app.filesDir, "tasks.json")
        val saved = runCatching {
            json.decodeFromString(ListSerializer(DownloadTask.serializer()), stateFile.readText())
        }.getOrDefault(emptyList())
        // أي مهمة كانت تعمل عند إغلاق التطبيق تصبح «متوقفة» ويمكن استكمالها.
        _tasks.value = saved.map {
            if (it.state == TaskState.RUNNING || it.state == TaskState.QUEUED) it.copy(state = TaskState.PAUSED, speed = null, etaSec = null) else it
        }
        scope.launch {
            SettingsStore(app).flow.collect { maxParallel = it.maxParallel; pump() }
        }
    }

    fun taskDir(id: String) = File(Library.root, id)

    private fun persist() {
        runCatching {
            val tmp = File(stateFile.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(DownloadTask.serializer()), _tasks.value))
            tmp.renameTo(stateFile)
        }
    }

    private fun mutate(id: String, save: Boolean = true, f: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }
        if (save) persist()
    }

    fun enqueue(spec: DownloadSpec): String {
        val task = DownloadTask(id = UUID.randomUUID().toString().take(12), spec = spec)
        _tasks.update { it + task }
        persist()
        pump()
        return task.id
    }

    fun pause(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        when (t.state) {
            TaskState.RUNNING -> { stopReason[id] = TaskState.PAUSED; kill(t) }
            TaskState.QUEUED -> mutate(id) { it.copy(state = TaskState.PAUSED) }
            else -> Unit
        }
    }

    fun pauseAll() = _tasks.value.forEach { pause(it.id) }

    fun resume(id: String) {
        mutate(id) { if (it.state == TaskState.PAUSED || it.state == TaskState.FAILED) it.copy(state = TaskState.QUEUED, error = null, stage = null) else it }
        pump()
    }

    fun retry(id: String) = resume(id)

    fun cancel(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (t.state == TaskState.RUNNING) {
            stopReason.remove(id) // null = إلغاء
            kill(t)
            // التنظيف يتم في نهاية الـ job.
            _tasks.update { list -> list.filterNot { it.id == id } }
            persist()
        } else {
            _tasks.update { list -> list.filterNot { it.id == id } }
            persist()
            cleanup(id)
        }
    }

    fun cancelAll() = _tasks.value.filter { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }.forEach { cancel(it.id) }

    private fun kill(t: DownloadTask) {
        when (t.spec.mode) {
            DownloadMode.DIRECT -> Direct.cancel(t.id)
            DownloadMode.GALLERY -> GalleryDl.cancel(t.id)
            else -> YtDlp.cancel(t.id)
        }
    }

    /** يحذف الملفات الجزئية لمهمة ملغاة (لا يمسّ ملفات المكتبة المكتملة). */
    private fun cleanup(id: String) {
        val dir = taskDir(id)
        val keep = Library.items.value.map { it.path }.toSet()
        dir.listFiles()?.forEach { if (it.path !in keep) it.deleteRecursively() }
        if (dir.list().isNullOrEmpty()) dir.delete()
    }

    val hasActive: Boolean
        get() = _tasks.value.any { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }

    @Synchronized
    private fun pump() {
        if (!::app.isInitialized) return
        val running = _tasks.value.count { it.state == TaskState.RUNNING }
        val slots = maxParallel - running
        if (slots > 0) {
            _tasks.value.filter { it.state == TaskState.QUEUED }.take(slots).forEach { start(it) }
        }
        if (hasActive) DownloadService.start(app)
    }

    private fun start(task: DownloadTask) {
        mutate(task.id) { it.copy(state = TaskState.RUNNING, stage = "جاري التحضير", error = null) }
        stopReason.remove(task.id)
        jobs[task.id] = scope.launch {
            val id = task.id
            val spec = task.spec
            val dir = taskDir(id)
            try {
                if (!Net.isOnline(app)) throw AppError(ErrorType.NO_INTERNET)
                val onProgress: (YtDlp.Progress) -> Unit = { p ->
                    val now = System.currentTimeMillis()
                    if (now - (lastEmit[id] ?: 0) > 250) {
                        lastEmit[id] = now
                        mutate(id, save = false) {
                            if (it.state != TaskState.RUNNING) it
                            else it.copy(
                                // لا نرجع بالنسبة للخلف (yt-dlp يعيد العدّ لكل مرحلة).
                                progress = if (p.fraction < 0) -1f else maxOf(p.fraction, if (it.progress < 0) 0f else it.progress),
                                speed = p.speed ?: it.speed, etaSec = p.etaSec, stage = p.stage ?: it.stage,
                            )
                        }
                    }
                }
                if (spec.mode == DownloadMode.DIRECT) {
                    dir.mkdirs()
                    Direct.download(app, id, spec.url, File(dir, spec.directName ?: "file"), onProgress)
                } else if (spec.mode == DownloadMode.GALLERY) {
                    GalleryDl.download(app, id, spec, dir, onProgress)
                } else {
                    val written = YtDlp.download(app, id, spec, dir, onProgress)
                    if (spec.subLang != null) {
                        val ok = if (spec.subUrl != null) {
                            onProgress(YtDlp.Progress(0.99f, null, null, "جلب الترجمة"))
                            Subtitles.fetchAndApply(app, spec.subUrl, spec.subLang, spec.subEmbed, dir)
                        } else written > 0
                        if (!ok) _notices.tryEmit("اكتمل التحميل، لكن المنصة لم تُتح الترجمة المطلوبة")
                        else if (spec.playlistItems != null && written < spec.playlistItems.size) {
                            _notices.tryEmit("الترجمة توفرت لـ $written من ${spec.playlistItems.size} فيديوهات")
                        }
                    }
                }
                val files = YtDlp.outputFiles(dir).filter { f ->
                    // في وضع الصوت: صورة غلاف لم تُضمَّن ليست ملفاً للمستخدم.
                    val ext = f.extension.lowercase()
                    val leftoverCover = spec.mode == DownloadMode.AUDIO && Mime.kindOf(Mime.ofExtension(ext)) == MediaKind.IMAGE
                    // ترجمة طُلب دمجها داخل الفيديو: ملفها المؤقت ليس ملفاً للمستخدم.
                    val leftoverSub = spec.subLang != null && spec.subEmbed && ext in setOf("vtt", "srt", "ass", "srv3", "ttml", "json3")
                    !leftoverCover && !leftoverSub
                }
                if (files.isEmpty()) throw AppError(ErrorType.GENERIC, "لم ينتج عن التحميل أي ملف")
                Library.add(files.map { f ->
                    val mime = Mime.ofExtension(f.extension)
                    val kind = Mime.kindOf(mime).let { k ->
                        if (k == MediaKind.VIDEO && spec.mode == DownloadMode.AUDIO) MediaKind.AUDIO else k
                    }
                    LibraryItem(
                        id = UUID.randomUUID().toString().take(12),
                        path = f.absolutePath,
                        title = if (files.size == 1) spec.title else f.nameWithoutExtension,
                        kind = kind,
                        mime = if (kind == MediaKind.AUDIO && mime.startsWith("video/")) "audio/mp4" else mime,
                        size = f.length(),
                        durationSec = if (files.size == 1) spec.durationSec else null,
                        thumbUrl = spec.thumbnail,
                        sourceUrl = spec.url,
                    )
                })
                _tasks.update { list -> list.filterNot { it.id == id } }
                persist()
                // تنظيف بقايا مؤقتة إن وُجدت.
                dir.listFiles()?.filter { it !in files }?.forEach { it.deleteRecursively() }
                _completed.tryEmit(spec.title to files.size)
            } catch (t: Throwable) {
                val stillThere = _tasks.value.any { it.id == id }
                val stoppedByUser = t is YoutubeDL.CanceledException || t is InterruptedException ||
                    stopReason.containsKey(id) || !stillThere
                when {
                    !stillThere -> cleanup(id) // أُلغيت
                    stoppedByUser -> mutate(id) { it.copy(state = TaskState.PAUSED, speed = null, etaSec = null, stage = null) }
                    else -> {
                        val err = t as? AppError ?: AppError(ErrorType.GENERIC, t.message)
                        mutate(id) {
                            it.copy(
                                state = TaskState.FAILED, speed = null, etaSec = null, stage = null,
                                error = listOfNotNull(err.type.message(), err.detail?.takeIf { d -> d.isNotBlank() }).joinToString("\n"),
                            )
                        }
                    }
                }
            } finally {
                stopReason.remove(id)
                jobs.remove(id)
                lastEmit.remove(id)
                pump()
            }
        }
    }

    /** حجم الملفات الجزئية لمهام لم تعد موجودة (يُستخدم في «مسح الكاش»). */
    fun orphanDirs(): List<File> {
        val known = _tasks.value.map { it.id }.toSet()
        val used = Library.items.value.mapNotNull { File(it.path).parentFile?.name }.toSet()
        return Library.root.listFiles().orEmpty().filter { it.isDirectory && it.name !in known && it.name !in used }
    }
}
