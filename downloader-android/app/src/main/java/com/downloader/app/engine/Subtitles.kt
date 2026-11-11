package com.downloader.app.engine

import android.content.Context
import okhttp3.Request
import java.io.File
import kotlin.concurrent.thread

/**
 * المسار الاحتياطي للترجمة: يجلب ملف WebVTT من رابط مباشر (ترجمة يوتيوب مع tlang)،
 * يحوّله إلى SRT، ثم يتركه بجانب الفيديو أو يدمجه داخل MP4 بـ FFmpeg المدمج.
 */
object Subtitles {
    /** يعيد true إذا أُضيفت الترجمة. الفشل لا يُفشل التحميل نفسه. */
    fun fetchAndApply(context: Context, url: String, lang: String, embed: Boolean, dir: File): Boolean = runCatching {
        val video = YtDlp.outputFiles(dir).filter { it.extension.lowercase() in setOf("mp4", "mkv", "webm", "mov", "m4v") }
            .maxByOrNull { it.length() } ?: return false
        val vtt = Net.client.newCall(Request.Builder().url(url).header("User-Agent", Net.BROWSER_UA).build()).execute().use { r ->
            if (!r.isSuccessful) return false
            r.body.string()
        }
        val srt = vttToSrt(vtt)
        if (srt.isBlank()) return false
        val srtFile = File(dir, "${video.nameWithoutExtension}.$lang.srt")
        srtFile.writeText(srt)
        if (!embed) return true
        val ok = embed(context, video, srtFile, lang)
        srtFile.delete()
        ok
    }.getOrDefault(false)

    /** تحويل WebVTT إلى SRT: يحذف الترويسة والوسوم ومؤشرات الموضع ويرقّم المقاطع. */
    fun vttToSrt(vtt: String): String {
        val time = Regex("""(?:(\d+):)?(\d{2}):(\d{2})[.,](\d{3})\s*-->\s*(?:(\d+):)?(\d{2}):(\d{2})[.,](\d{3})""")
        val out = StringBuilder()
        var n = 0
        var text = mutableListOf<String>()
        var stamp: String? = null
        var last = ""
        fun flush() {
            val body = text.joinToString("\n").trim()
            // يوتيوب يكرر السطر نفسه في مقاطع متتالية للتفريغ الآلي؛ نتخطى المكرر.
            if (stamp != null && body.isNotEmpty() && body != last) {
                n++
                out.append(n).append('\n').append(stamp).append('\n').append(body).append("\n\n")
                last = body
            }
            text = mutableListOf(); stamp = null
        }
        for (raw in vtt.replace("\r", "").lineSequence()) {
            val m = time.find(raw)
            when {
                m != null -> {
                    flush()
                    val g = m.groupValues
                    fun t(h: String, mm: String, ss: String, ms: String) = "%02d:%s:%s,%s".format(h.toIntOrNull() ?: 0, mm, ss, ms)
                    stamp = t(g[1], g[2], g[3], g[4]) + " --> " + t(g[5], g[6], g[7], g[8])
                }
                raw.isBlank() -> flush()
                stamp != null -> raw.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&nbsp;", " ")
                    .replace("&lt;", "<").replace("&gt;", ">").trim().takeIf { it.isNotEmpty() }?.let { text += it }
            }
        }
        flush()
        return out.toString()
    }

    private val iso3 = mapOf("ar" to "ara", "en" to "eng", "fr" to "fra", "es" to "spa", "de" to "deu", "tr" to "tur")

    /** يدمج الترجمة كمسار mov_text داخل الفيديو بدون إعادة ترميز. */
    private fun embed(context: Context, video: File, srt: File, lang: String): Boolean {
        val base = File(context.noBackupFilesDir, "youtubedl-android")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val out = File(video.parentFile, video.nameWithoutExtension + ".subs.tmp.mp4")
        val cmd = listOf(
            File(nativeDir, "libffmpeg.so").absolutePath, "-y", "-loglevel", "error",
            "-i", video.absolutePath, "-i", srt.absolutePath,
            "-map", "0", "-map", "1", "-c", "copy", "-c:s", "mov_text",
            "-metadata:s:s:0", "language=${iso3[lang.substringBefore('-')] ?: lang.take(3)}", out.absolutePath,
        )
        val process = ProcessBuilder(cmd).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = "${base.absolutePath}/packages/ffmpeg/usr/lib:${base.absolutePath}/packages/python/usr/lib"
        }.start()
        val log = StringBuilder()
        val reader = thread { runCatching { process.inputStream.bufferedReader().forEachLine { log.appendLine(it) } } }
        val exit = process.waitFor()
        reader.join(2000)
        if (exit != 0 || out.length() < video.length() / 2) {
            android.util.Log.e("Downloader", "subtitle embed failed ($exit): $log")
            out.delete()
            return false
        }
        // الناتج MP4 دائماً؛ إن كان الأصل بامتداد آخر نبدّل الامتداد.
        val dest = File(video.parentFile, video.nameWithoutExtension + ".mp4")
        video.delete()
        return out.renameTo(dest)
    }
}
