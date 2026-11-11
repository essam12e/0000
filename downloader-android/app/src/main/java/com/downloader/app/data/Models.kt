package com.downloader.app.data

import kotlinx.serialization.Serializable

enum class MediaKind { VIDEO, AUDIO, IMAGE, OTHER }

/** خيار جودة واحد يظهر للمستخدم بعد تحليل الرابط. */
data class QualityOption(
    val id: String,
    val label: String,
    val badge: String?,
    val detail: String,
    val sizeBytes: Long?,
    val sizeApprox: Boolean,
    val formatSelector: String,
    val shortSide: Int,
)

/** مسار صوتي بلغة معيّنة (فيديوهات متعددة الدبلجة). */
data class AudioTrack(val lang: String, val original: Boolean)

/** ترجمة متاحة: يدوية من صاحب المحتوى أو مولّدة آلياً. */
data class SubtitleTrack(val lang: String, val auto: Boolean)

data class PlaylistItem(
    val index: Int,
    val title: String,
    val thumbnail: String?,
    val durationSec: Long?,
)

/** ملف مباشر (صورة/فيديو/صوت/أي ملف) يُحمَّل بدون yt-dlp. */
data class DirectFile(
    val url: String,
    val fileName: String,
    val mime: String?,
    val sizeBytes: Long?,
    val kind: MediaKind,
)

data class MediaInfo(
    val sourceUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val platform: String?,
    val videoOptions: List<QualityOption> = emptyList(),
    val hasAudio: Boolean = true,
    val audioSizeBytes: Long? = null,
    val isPlaylist: Boolean = false,
    val items: List<PlaylistItem> = emptyList(),
    val directFiles: List<DirectFile> = emptyList(),
    /** النتيجة من gallery-dl: العناصر ملفات أصلية (صور/فيديو) بلا خيارات جودة. */
    val viaGallery: Boolean = false,
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** معرّف ثابت للمحتوى (المنصة + المعرّف) لكشف التكرار مهما اختلف شكل الرابط. */
    val mediaKey: String? = null,
) {
    val isDirect get() = directFiles.isNotEmpty()
}

@Serializable
enum class DownloadMode { VIDEO, AUDIO, DIRECT, GALLERY }

@Serializable
data class DownloadSpec(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val mode: DownloadMode,
    val formatSelector: String? = null,
    val qualityLabel: String = "",
    val audioFormat: String = "mp3",
    val playlistItems: List<Int>? = null,
    val directName: String? = null,
    val expectedBytes: Long? = null,
    val durationSec: Long? = null,
    val audioLang: String? = null,
    val subLang: String? = null,
    val subAuto: Boolean = false,
    val subEmbed: Boolean = true,
    val trimStart: Long? = null,
    val trimEnd: Long? = null,
)

@Serializable
enum class TaskState { QUEUED, RUNNING, PAUSED, FAILED }

@Serializable
data class DownloadTask(
    val id: String,
    val spec: DownloadSpec,
    val state: TaskState = TaskState.QUEUED,
    /** من 0 إلى 1، أو قيمة سالبة عندما لا تُعرف النسبة. */
    val progress: Float = 0f,
    val speed: String? = null,
    val etaSec: Long? = null,
    val stage: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class LibraryItem(
    val id: String,
    val path: String,
    val title: String,
    val kind: MediaKind,
    val mime: String,
    val size: Long,
    val durationSec: Long? = null,
    val thumbUrl: String? = null,
    val sourceUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class ErrorType { UNSUPPORTED, PRIVATE, NO_INTERNET, ENGINE, GENERIC }

class AppError(val type: ErrorType, val detail: String? = null) : Exception(detail)

fun ErrorType.message(): String = when (this) {
    ErrorType.UNSUPPORTED -> "هذا الرابط غير مدعوم"
    ErrorType.PRIVATE -> "محتوى خاص يحتاج تسجيل الدخول"
    ErrorType.NO_INTERNET -> "لا يوجد اتصال بالإنترنت"
    ErrorType.ENGINE -> "تعذّر تشغيل محرك التحميل"
    ErrorType.GENERIC -> "تعذّر إتمام العملية"
}

fun ErrorType.hint(): String = when (this) {
    ErrorType.UNSUPPORTED -> "تأكد أن الرابط يشير مباشرة إلى فيديو أو صورة أو مقطع صوتي، ثم حاول مجدداً. قد يفيد تحديث المحرك من الإعدادات."
    ErrorType.PRIVATE -> "هذا المحتوى لا يظهر إلا لصاحب حساب مسجّل. سجّل الدخول من الإعدادات ثم أعد المحاولة."
    ErrorType.NO_INTERNET -> "تحقق من الواي فاي أو بيانات الجوال ثم أعد المحاولة."
    ErrorType.ENGINE -> "أعد فتح التطبيق، وإن استمرت المشكلة امسح الكاش من الإعدادات."
    ErrorType.GENERIC -> "حاول مرة أخرى بعد قليل. إن تكرر الخطأ فحدّث المحرك من الإعدادات."
}

@Serializable
data class HistoryEntry(
    val id: String,
    val url: String,
    val key: String? = null,
    val title: String,
    val thumbnail: String? = null,
    val label: String = "",
    val platform: String? = null,
    val at: Long = System.currentTimeMillis(),
)
