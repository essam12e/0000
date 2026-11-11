package com.downloader.app.data

import android.webkit.MimeTypeMap
import java.util.Locale

/** أدوات تنسيق: أرقام لاتينية دائماً حتى تبقى الأحجام والجودات ثابتة العرض. */
object Fmt {
    /** يعزل المقطع اتجاهياً حتى لا تتبعثر الأرقام واللاتينية داخل السطر العربي. */
    fun iso(s: String): String = "\u2068$s\u2069"

    private fun plural(n: Int, one: String, two: String, few: String, many: String): String = when {
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "$n $few"
        else -> "$n $many"
    }

    fun files(n: Int) = plural(n, "ملف واحد", "ملفان", "ملفات", "ملفاً")
    fun elements(n: Int) = plural(n, "عنصر واحد", "عنصران", "عناصر", "عنصراً")
    fun options(n: Int) = plural(n, "خيار واحد", "خياران", "خيارات", "خياراً")

    fun bytes(b: Long?): String {
        if (b == null || b <= 0) return "—"
        val kb = b / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f ج.ب", gb)
            mb >= 100 -> String.format(Locale.US, "%.0f م.ب", mb)
            mb >= 1 -> String.format(Locale.US, "%.1f م.ب", mb)
            else -> String.format(Locale.US, "%.0f ك.ب", kb.coerceAtLeast(1.0))
        }
    }

    fun duration(sec: Long?): String? {
        if (sec == null || sec <= 0) return null
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun eta(sec: Long?): String? {
        if (sec == null || sec <= 0) return null
        return when {
            sec >= 3600 -> "باقي ${sec / 3600} س ${(sec % 3600) / 60} د"
            sec >= 60 -> "باقي ${sec / 60} د ${sec % 60} ث"
            else -> "باقي $sec ث"
        }
    }

    /** «1:02:03» أو «2:15» أو «90» → ثوانٍ. يعيد null إذا كان النص غير صالح. */
    fun parseTime(text: String): Long? {
        val parts = text.trim().split(':').map { it.trim().toLongOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3 || parts.any { it < 0 }) return null
        return parts.fold(0L) { acc, v -> acc * 60 + v }
    }

    fun clock(sec: Long): String = duration(sec) ?: "0:00"

    fun date(millis: Long): String =
        java.text.SimpleDateFormat("d MMM yyyy · HH:mm", Locale("ar")).format(java.util.Date(millis))
            .map { c -> if (c in '\u0660'..'\u0669') '0' + (c - '\u0660') else c }.joinToString("")

    /** اسم اللغة بالعربية من رمزها (ar → العربية، en-US → الإنجليزية (الولايات المتحدة)). */
    fun language(code: String): String {
        val clean = code.removeSuffix("-orig")
        val name = runCatching { Locale.forLanguageTag(clean).getDisplayName(Locale("ar")) }.getOrNull()
        return if (name.isNullOrBlank() || name.equals(clean, true)) code else name
    }

    fun percent(p: Float): String = String.format(Locale.US, "%d%%", (p * 100).toInt().coerceIn(0, 100))

    /** يحوّل سرعة yt-dlp مثل «1.20MiB/s» إلى «1.2 م.ب/ث». */
    fun speed(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val m = Regex("""([\d.]+)\s*([KMG]?)i?B/s""").find(raw) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = when (m.groupValues[2]) {
            "G" -> "ج.ب"; "M" -> "م.ب"; "K" -> "ك.ب"; else -> "بايت"
        }
        return String.format(Locale.US, "%.1f %s/ث", v, unit)
    }

    fun speedOf(bytesPerSec: Double): String = when {
        bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f م.ب/ث", bytesPerSec / 1048576)
        else -> String.format(Locale.US, "%.0f ك.ب/ث", bytesPerSec / 1024)
    }
}

object Mime {
    private val extra = mapOf(
        "m4a" to "audio/mp4", "mp3" to "audio/mpeg", "opus" to "audio/ogg", "ogg" to "audio/ogg",
        "aac" to "audio/aac", "wav" to "audio/x-wav", "flac" to "audio/flac", "weba" to "audio/webm",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "3gp" to "video/3gpp", "ts" to "video/mp2t", "flv" to "video/x-flv",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
        "srt" to "application/x-subrip", "vtt" to "text/vtt", "ass" to "text/x-ssa", "lrc" to "text/plain",
        "gif" to "image/gif", "heic" to "image/heic", "avif" to "image/avif", "bmp" to "image/bmp",
    )

    fun ofExtension(ext: String): String {
        val e = ext.lowercase(Locale.US)
        return extra[e] ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(e) ?: "application/octet-stream"
    }

    fun extensionOf(mime: String?): String? {
        val m = mime?.substringBefore(';')?.trim()?.lowercase(Locale.US) ?: return null
        return extra.entries.firstOrNull { it.value == m }?.key
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(m)
    }

    fun kindOf(mime: String?): MediaKind {
        val m = mime?.lowercase(Locale.US) ?: return MediaKind.OTHER
        return when {
            m.startsWith("video/") -> MediaKind.VIDEO
            m.startsWith("audio/") -> MediaKind.AUDIO
            m.startsWith("image/") -> MediaKind.IMAGE
            else -> MediaKind.OTHER
        }
    }

    val mediaExtensions = extra.keys
}

private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** يستخرج أول رابط من نص (مفيد لنصوص المشاركة التي تحتوي عنواناً + رابطاً). */
fun extractUrl(text: String?): String? =
    text?.let { urlRegex.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '؟', '،') }

/** أسماء ملفات آمنة للتخزين. */
fun safeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.').take(120).ifBlank { "file" }

/** صيغة موحّدة للرابط لكشف التكرار: بلا وسوم تتبّع ولا شرطة أخيرة. */
fun normalizeUrl(url: String): String {
    val noFragment = url.trim().substringBefore('#')
    val base = noFragment.substringBefore('?').trimEnd('/')
    val query = noFragment.substringAfter('?', "").split('&')
        .filter { it.isNotBlank() }
        .filterNot { q ->
            val k = q.substringBefore('=').lowercase()
            k.startsWith("utm_") || k in setOf("si", "igshid", "igsh", "fbclid", "feature", "share", "ref", "ref_src", "s", "t", "pp")
        }.sorted()
    val schemeless = base.replaceFirst(Regex("^https?://(www\\.|m\\.)?", RegexOption.IGNORE_CASE), "").lowercase()
        .let { if (it.startsWith("youtu.be/")) "youtube.com/watch?v=" + it.removePrefix("youtu.be/") else it }
    return if (query.isEmpty() || schemeless.contains("?")) schemeless else schemeless + "?" + query.joinToString("&")
}
