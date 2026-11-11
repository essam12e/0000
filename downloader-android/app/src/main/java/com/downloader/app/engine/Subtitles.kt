package com.downloader.app.engine

import android.content.Context
import com.downloader.app.data.DownloadSpec
import com.downloader.app.data.Fmt
import java.io.File
import kotlin.concurrent.thread

/**
 * إنهاء الترجمة بعد التحميل. yt-dlp ينزّل ملفات SRT بجانب كل فيديو (المطلوبة إن أتاحتها المنصة،
 * والأصلية كمصدر)، ثم هنا:
 *   1) إن وُجدت الترجمة باللغة المطلوبة تُستخدم كما هي (الأدق).
 *   2) وإلا تُترجم الترجمة الأصلية على الجهاز.
 *   3) وإن تعذّر ذلك تُرفق الأصلية بلغتها مع تنبيه.
 * ثم تُدمج داخل الفيديو بـ FFmpeg المدمج، أو تُترك ملف SRT باسم الفيديو.
 */
object Subtitles {
    val SUB_EXTS = setOf("srt", "vtt", "ass", "srv3", "ttml", "json3", "lrc")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "mov", "m4v")

    class Result(val videos: Int, val platform: Int, val translated: Int, val original: Int, val originalLang: String?) {
        val none get() = videos - platform - translated - original

        /** نص التنبيه للمستخدم، أو null إذا جاءت كل الترجمات من المنصة مباشرة. */
        fun notice(): String? = when {
            videos == 0 -> null
            videos == 1 && translated == 1 -> "تُرجمت الترجمة إلى العربية على جهازك (المنصة لم تُتح ترجمتها)"
            videos == 1 && original == 1 -> "تعذّرت الترجمة للعربية؛ أُرفقت الترجمة الأصلية${originalLang?.let { " (" + Fmt.language(it) + ")" } ?: ""}"
            videos == 1 && none == 1 -> "اكتمل التحميل، لكن لا توجد ترجمة متاحة لهذا الفيديو"
            videos > 1 && (translated + original + none) > 0 ->
                "الترجمة: $platform من المنصة، $translated تُرجمت على الجهاز" +
                    (if (original > 0) "، $original بلغتها الأصلية" else "") + (if (none > 0) "، $none بلا ترجمة" else "")
            else -> null
        }
    }

    private class Cue(val start: Long, val end: Long, val text: String)

    fun finalize(context: Context, spec: DownloadSpec, dir: File, onStage: (String) -> Unit): Result {
        val want = spec.subLang ?: return Result(0, 0, 0, 0, null)
        val wantBase = want.substringBefore('-')
        val videos = YtDlp.outputFiles(dir).filter { it.extension.lowercase() in VIDEO_EXTS }
        var platform = 0; var translated = 0; var original = 0; var origLang: String? = null
        for (video in videos) {
            runCatching {
                val prefix = video.nameWithoutExtension + "."
                // ملفات هذا الفيديو: «الاسم.<رمز اللغة>.srt»
                val subs = dir.listFiles().orEmpty().filter { it.name.startsWith(prefix) && it.extension.lowercase() == "srt" && it.length() > 0 }
                    .associateBy { it.name.removePrefix(prefix).removeSuffix(".srt") }
                val direct = subs[want] ?: subs.entries.firstOrNull { it.key == wantBase || it.key.startsWith("$wantBase-") }?.value
                val final = File(dir, "$prefix$wantBase.srt")
                var finalLang = wantBase
                when {
                    direct != null -> {
                        writeSrt(final, clean(parseSrt(direct.readText()), rolling = false)); platform++
                    }
                    else -> {
                        val srcEntry = spec.subSrc?.let { s -> subs.entries.firstOrNull { it.key == s } }
                            ?: subs.entries.firstOrNull { it.key.startsWith("en") } ?: subs.entries.firstOrNull()
                        if (srcEntry == null) return@runCatching
                        // التفريغ الآلي ليوتيوب يأتي بأسطر متدحرجة مكررة؛ ننظفه ثم ندمج المقاطع القصيرة لتُترجم كجمل.
                        val auto = srcEntry.key.endsWith("-orig")
                        val cues = merge(clean(parseSrt(srcEntry.value.readText()), rolling = auto), enabled = auto)
                        val out = Translator.translate(srcEntry.key, wantBase, cues.map { it.text.replace('\n', ' ') }, onStage)
                        if (out != null) {
                            writeSrt(final, cues.mapIndexed { i, c -> Cue(c.start, c.end, out[i]) }); translated++
                        } else {
                            finalLang = srcEntry.key.substringBefore('-')
                            writeSrt(File(dir, "$prefix$finalLang.final.srt"), cues)
                            original++; origLang = srcEntry.key
                        }
                    }
                }
                val result = if (finalLang == wantBase) final else File(dir, "$prefix$finalLang.final.srt")
                subs.values.filter { it != result }.forEach { it.delete() }
                if (spec.subEmbed) {
                    onStage("دمج الترجمة في الفيديو")
                    embed(context, video, result, finalLang)
                    result.delete()
                } else if (result.name.endsWith(".final.srt")) {
                    result.renameTo(File(dir, "$prefix$finalLang.srt"))
                }
            }.onFailure { android.util.Log.e("Downloader", "subtitle finalize failed", it) }
        }
        return Result(videos.size, platform, translated, original, origLang)
    }

    // ---------------------------------------------------------------- SRT

    private val stamp = Regex("""(\d+):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{3})""")

    private fun parseSrt(text: String): List<Cue> {
        val cues = ArrayList<Cue>()
        var start = -1L; var end = 0L
        val lines = ArrayList<String>()
        fun flush() {
            if (start >= 0 && lines.isNotEmpty()) cues += Cue(start, end, lines.joinToString("\n"))
            start = -1; lines.clear()
        }
        for (raw in text.replace("\r", "").removePrefix("﻿").lineSequence()) {
            val m = stamp.find(raw)
            when {
                m != null -> {
                    flush()
                    val g = m.groupValues.drop(1).map { it.toLong() }
                    start = ((g[0] * 60 + g[1]) * 60 + g[2]) * 1000 + g[3]
                    end = ((g[4] * 60 + g[5]) * 60 + g[6]) * 1000 + g[7]
                }
                raw.isBlank() -> flush()
                start >= 0 -> raw.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&nbsp;", " ")
                    .replace("&lt;", "<").replace("&gt;", ">").trim().takeIf { it.isNotEmpty() }?.let { lines += it }
                // السطر السابق للطابع الزمني هو رقم المقطع؛ يُتجاهل.
            }
        }
        flush()
        return cues
    }

    /** [rolling]: تفريغ يوتيوب الآلي — كل مقطع يكرر السطر السابق ثم يضيف كلمات؛ نأخذ السطر الأخير ونحذف المكرر والمقاطع اللحظية. */
    private fun clean(cues: List<Cue>, rolling: Boolean): List<Cue> {
        val out = ArrayList<Cue>()
        for (c in cues) {
            val text = if (rolling) c.text.lines().last().trim() else c.text.trim()
            if (text.isEmpty() || (rolling && c.end - c.start < 100)) continue
            val last = out.lastOrNull()
            if (last != null && last.text == text) {
                out[out.size - 1] = Cue(last.start, maxOf(last.end, c.end), text)
            } else out += Cue(c.start, c.end, text)
        }
        // منع تداخل الأزمنة بعد التنظيف.
        return out.mapIndexed { i, c -> out.getOrNull(i + 1)?.let { n -> if (c.end > n.start) Cue(c.start, n.start, c.text) else c } ?: c }
    }

    /** يدمج المقاطع القصيرة المتتالية (حتى نحو 70 حرفاً / 6 ثوانٍ) حتى يترجم المحرك جملاً لا كلمات متفرقة. */
    private fun merge(cues: List<Cue>, enabled: Boolean): List<Cue> {
        if (!enabled) return cues
        val out = ArrayList<Cue>()
        var cur: Cue? = null
        for (c in cues) {
            val p = cur
            cur = if (p != null && c.start - p.end < 1500 && p.text.length + c.text.length < 70 && c.end - p.start < 6000 &&
                !p.text.trimEnd().endsWith('.') && !p.text.trimEnd().endsWith('?') && !p.text.trimEnd().endsWith('!')
            ) Cue(p.start, c.end, p.text + " " + c.text) else { p?.let { out += it }; c }
        }
        cur?.let { out += it }
        return out
    }

    private fun writeSrt(file: File, cues: List<Cue>) {
        fun t(ms: Long) = "%02d:%02d:%02d,%03d".format(java.util.Locale.US, ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
        file.writeText(buildString {
            cues.forEachIndexed { i, c -> append(i + 1).append('\n').append(t(c.start)).append(" --> ").append(t(c.end)).append('\n').append(c.text).append("\n\n") }
        })
    }

    // ---------------------------------------------------------------- FFmpeg

    private val iso3 = mapOf("ar" to "ara", "en" to "eng", "fr" to "fra", "es" to "spa", "de" to "deu", "tr" to "tur", "ru" to "rus")

    /** يدمج الترجمة كمسار mov_text داخل الفيديو بدون إعادة ترميز. */
    private fun embed(context: Context, video: File, srt: File, lang: String): Boolean {
        val base = File(context.noBackupFilesDir, "youtubedl-android")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val out = File(video.parentFile, video.nameWithoutExtension + ".subs.tmp.mp4")
        val cmd = listOf(
            File(nativeDir, "libffmpeg.so").absolutePath, "-y", "-loglevel", "error",
            "-i", video.absolutePath, "-i", srt.absolutePath,
            "-map", "0:v", "-map", "0:a?", "-map", "1", "-c", "copy", "-c:s", "mov_text",
            "-metadata:s:s:0", "language=${iso3[lang] ?: lang.take(3)}", "-disposition:s:0", "default", out.absolutePath,
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
        val dest = File(video.parentFile, video.nameWithoutExtension + ".mp4")
        video.delete()
        return out.renameTo(dest)
    }
}
