package com.downloader.app.engine

import android.content.Context
import com.downloader.app.auth.CookieJarStore
import com.downloader.app.data.AppError
import com.downloader.app.data.DownloadMode
import com.downloader.app.data.DownloadSpec
import com.downloader.app.data.ErrorType
import com.downloader.app.data.MediaInfo
import com.downloader.app.data.PlaylistItem
import com.downloader.app.data.QualityOption
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/** غلاف حول yt-dlp + FFmpeg المدمجين (youtubedl-android). كل شيء يعمل على الجهاز. */
object YtDlp {
    private val ready = CompletableDeferred<Unit>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val ANALYZE_TIMEOUT_MS = 240_000L

    /** يُستدعى مرة واحدة من Application على خيط خلفي (يفكّ Python و FFmpeg أول تشغيل). */
    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ready.complete(Unit)
        } catch (t: Throwable) {
            android.util.Log.e("Downloader", "engine init failed", t)
            ready.completeExceptionally(t)
        }
    }

    /** لبقية المحركات التي تعتمد على بايثون المدمجة (gallery-dl). */
    suspend fun awaitEngine() = awaitReady()

    private suspend fun awaitReady() {
        try {
            ready.await()
        } catch (t: Throwable) {
            throw AppError(ErrorType.ENGINE, generateSequence(t) { it.cause }.mapNotNull { it.message }.distinct().joinToString(" ← ").take(220))
        }
    }

    /** ينتظر اكتمال التهيئة ثم يعيد رقم إصدار yt-dlp. */
    suspend fun version(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            ready.await()
            YoutubeDL.getInstance().versionName(context) ?: YoutubeDL.getInstance().version(context)
        }.getOrNull()
    }

    /** يحدّث yt-dlp من الإصدارات الرسمية. يعيد true إذا نُزّل إصدار جديد. */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        if (!Net.isOnline(context)) throw AppError(ErrorType.NO_INTERNET)
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE) == YoutubeDL.UpdateStatus.DONE
        } catch (t: Throwable) {
            throw AppError(ErrorType.GENERIC, t.message)
        }
    }

    private fun YoutubeDLRequest.common(context: Context, cookieCopy: File?) = apply {
        addOption("--no-warnings")
        addOption("--socket-timeout", 30)
        addOption("--cache-dir", File(context.cacheDir, "ytdlp").absolutePath)
        if (cookieCopy != null) addOption("--cookies", cookieCopy.absolutePath)
    }

    // ---------------------------------------------------------------- تحليل الرابط

    suspend fun analyze(context: Context, url: String): MediaInfo = withContext(Dispatchers.IO) {
        awaitReady()
        val cookies = CookieJarStore.copyFor(context, "analyze-" + UUID.randomUUID())
        try {
            val request = YoutubeDLRequest(url).common(context, cookies).apply {
                addOption("--dump-single-json")
                addOption("--flat-playlist")
                addOption("--no-playlist")
            }
            // التنفيذ حاجز؛ نشغّله كمهمة فرعية حتى يُقتل yt-dlp فوراً إذا ألغى المستخدم أو انتهت المهلة.
            val pid = "analyze-" + UUID.randomUUID()
            val startedAt = System.currentTimeMillis()
            val response = try {
                withTimeout(ANALYZE_TIMEOUT_MS) {
                    val run = async(Dispatchers.IO) { YoutubeDL.getInstance().execute(request, pid, null) }
                    try {
                        run.await()
                    } catch (c: CancellationException) {
                        cancel(pid)
                        throw c
                    }
                }
            } catch (t: TimeoutCancellationException) {
                throw AppError(ErrorType.GENERIC, "استغرق تحليل الرابط وقتاً أطول من المتوقع")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // قتل العملية عند انتهاء المهلة قد يظهر كاستثناء من المكتبة نفسها.
                if (System.currentTimeMillis() - startedAt >= ANALYZE_TIMEOUT_MS - 1000) {
                    throw AppError(ErrorType.GENERIC, "استغرق تحليل الرابط وقتاً أطول من المتوقع")
                }
                throw classify(context, t.message)
            }
            val root = runCatching { json.parseToJsonElement(response.out.trim()).jsonObject }.getOrNull()
                ?: throw classify(context, response.err)
            parse(url, root)
        } finally {
            cookies?.delete()
        }
    }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    private fun JsonObject.thumb(): String? =
        str("thumbnail") ?: (this["thumbnails"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("url") }?.lastOrNull()

    private fun parse(url: String, root: JsonObject): MediaInfo {
        val entries = (root["entries"] as? JsonArray)?.mapNotNull { it as? JsonObject }
        if (root.str("_type") == "playlist" && entries != null) {
            if (entries.size == 1 && entries[0]["formats"] is JsonArray) return parseSingle(url, entries[0])
            if (entries.isEmpty()) throw AppError(ErrorType.UNSUPPORTED, "القائمة فارغة")
            val items = entries.mapIndexed { i, e ->
                PlaylistItem(
                    index = i + 1,
                    title = e.str("title") ?: "عنصر ${i + 1}",
                    thumbnail = e.thumb(),
                    durationSec = e.num("duration")?.toLong(),
                )
            }
            val maxSide = entries.flatMap { e -> (e["formats"] as? JsonArray).orEmpty() }
                .mapNotNull { f -> (f as? JsonObject)?.let { shortSide(it) } }.maxOrNull()
            return MediaInfo(
                sourceUrl = url,
                title = root.str("title") ?: items.first().title,
                uploader = root.str("uploader") ?: root.str("channel"),
                thumbnail = root.thumb() ?: items.firstOrNull { it.thumbnail != null }?.thumbnail,
                durationSec = null,
                platform = root.str("extractor_key"),
                videoOptions = presetOptions(maxSide),
                isPlaylist = true,
                items = items,
            )
        }
        return parseSingle(url, root)
    }

    private fun shortSide(f: JsonObject): Int? {
        val h = f.num("height")?.toInt() ?: return null
        val w = f.num("width")?.toInt()
        return if (w != null && w > 0) minOf(w, h) else h
    }

    private fun sizeOf(f: JsonObject, duration: Double?): Pair<Long, Boolean>? {
        f.num("filesize")?.let { if (it > 0) return it.toLong() to false }
        f.num("filesize_approx")?.let { if (it > 0) return it.toLong() to true }
        val tbr = f.num("tbr") ?: f.num("vbr") ?: f.num("abr")
        if (tbr != null && tbr > 0 && duration != null && duration > 0) return (tbr * 125 * duration).toLong() to true
        return null
    }

    private fun codecRank(v: String?): Int = when {
        v == null -> 1
        v.startsWith("avc") || v.startsWith("h264") -> 3
        v.startsWith("vp9") || v.startsWith("vp09") || v.startsWith("hev") || v.startsWith("hvc") -> 2
        else -> 1
    }

    private fun codecName(v: String?): String? = when {
        v == null -> null
        v.startsWith("avc") || v.startsWith("h264") -> "H.264"
        v.startsWith("vp9") || v.startsWith("vp09") -> "VP9"
        v.startsWith("av01") -> "AV1"
        v.startsWith("hev") || v.startsWith("hvc") -> "HEVC"
        else -> null
    }

    private fun labelFor(side: Int, fps: Int?): String {
        val std = listOf(4320, 2160, 1440, 1080, 720, 480, 360, 240, 144)
            .firstOrNull { side >= it * 0.93 } ?: side
        return "${std}p" + if (fps != null && fps > 30) fps.toString() else ""
    }

    private fun badgeFor(side: Int): String? = when {
        side >= 4000 -> "8K"
        side >= 2000 -> "4K"
        side >= 1340 -> "2K"
        side >= 1000 -> "FHD"
        side >= 670 -> "HD"
        else -> null
    }

    private fun parseSingle(url: String, o: JsonObject): MediaInfo {
        val duration = o.num("duration")
        val formats = (o["formats"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("ext") != "mhtml" && it.str("format_note") != "storyboard" }

        fun hasVideo(f: JsonObject) = f.str("vcodec").let { it != null && it != "none" } || (f.str("vcodec") == null && f.num("height") != null)
        fun hasAudio(f: JsonObject) = f.str("acodec").let { it != "none" }
        val audioOnly = formats.filter { !hasVideo(it) && f_isAudio(it) }
        val bestAudio = audioOnly.maxWithOrNull(
            compareBy<JsonObject>({ if (it.str("ext") == "m4a") 1 else 0 }, { it.num("abr") ?: it.num("tbr") ?: 0.0 })
        )
        val bestAudioSize = bestAudio?.let { sizeOf(it, duration) }

        val videos = formats.filter { hasVideo(it) && it.num("height") != null }
        val options = videos.groupBy { labelFor(shortSide(it)!!, null) }.map { (_, group) ->
            val best = group.maxWith(
                compareBy<JsonObject>({ codecRank(it.str("vcodec")) }, { it.num("fps") ?: 0.0 }, { it.num("tbr") ?: 0.0 })
            )
            val side = shortSide(best)!!
            val h = best.num("height")!!.toInt()
            val id = best.str("format_id") ?: "best"
            val combined = hasAudio(best)
            val videoSize = sizeOf(best, duration)
            val total = when {
                videoSize == null -> null
                combined || bestAudio == null -> videoSize
                bestAudioSize != null -> (videoSize.first + bestAudioSize.first) to (videoSize.second || bestAudioSize.second)
                else -> videoSize.first to true
            }
            val selector = if (combined || bestAudio == null) {
                "$id/b[height<=$h]/bv*[height<=$h]+ba/b"
            } else {
                "$id+ba[ext=m4a]/$id+ba/bv*[height<=$h]+ba/b[height<=$h]/b"
            }
            QualityOption(
                id = "v$side-$id",
                label = labelFor(side, best.num("fps")?.toInt()),
                badge = badgeFor(side),
                detail = listOfNotNull("MP4", codecName(best.str("vcodec"))).joinToString(" · "),
                sizeBytes = total?.first,
                sizeApprox = total?.second ?: true,
                formatSelector = selector,
                shortSide = side,
            )
        }.sortedByDescending { it.shortSide }.toMutableList()

        // مواقع لا تعطي أبعاداً (أو رابط وحيد): خيار «أفضل جودة».
        val looksAudioOnly = formats.isNotEmpty() && formats.none { hasVideo(it) } ||
            (formats.isEmpty() && o.str("vcodec") == "none")
        if (options.isEmpty() && !looksAudioOnly) {
            val single = o.num("filesize") ?: o.num("filesize_approx")
            options += QualityOption(
                id = "best", label = "أفضل جودة", badge = null, detail = "MP4",
                sizeBytes = single?.toLong(), sizeApprox = true,
                formatSelector = "bv*+ba/b", shortSide = 0,
            )
        }

        val audioSize = bestAudioSize?.first
            ?: duration?.let { (it * 128 * 125).toLong() }

        return MediaInfo(
            sourceUrl = url,
            title = o.str("title") ?: o.str("fulltitle") ?: "بدون عنوان",
            uploader = o.str("uploader") ?: o.str("channel") ?: o.str("creator"),
            thumbnail = o.thumb(),
            durationSec = duration?.toLong(),
            platform = o.str("extractor_key"),
            videoOptions = options,
            hasAudio = looksAudioOnly || bestAudio != null || formats.any { hasVideo(it) && hasAudio(it) } || formats.isEmpty(),
            audioSizeBytes = audioSize,
        )
    }

    private fun f_isAudio(f: JsonObject) = f.str("acodec").let { it != null && it != "none" } ||
        f.str("resolution") == "audio only" || (f.num("abr") ?: 0.0) > 0

    /** قوائم التشغيل والمنشورات المتعددة: جودات عامة لأن صيغ كل عنصر تُعرف وقت التحميل. */
    private fun presetOptions(maxSide: Int?): List<QualityOption> {
        val best = QualityOption("p-best", "الأعلى المتاح", null, "MP4", null, true, "bv*+ba/b", 100000)
        val presets = listOf(2160, 1440, 1080, 720, 480, 360).filter { maxSide == null || it <= maxSide * 1.07 }
            .map { h ->
                // على الفيديوهات العمودية الارتفاع هو الضلع الأطول، لذلك نقيّد الضلع الأقصر عبر الترتيب -S.
                QualityOption("p-$h", "${h}p", badgeFor(h), "MP4", null, true, "bv*+ba/b", h)
            }
        return if (maxSide != null && presets.size <= 1) listOf(best) else listOf(best) + presets
    }

    // ---------------------------------------------------------------- التحميل

    class Progress(val fraction: Float, val speed: String?, val etaSec: Long?, val stage: String?)

    /**
     * ينفّذ التحميل (حاجز) داخل [dir]. يُلغى عبر [cancel] بنفس [processId]؛ الملفات الجزئية تبقى للاستكمال.
     */
    fun download(context: Context, processId: String, spec: DownloadSpec, dir: File, onProgress: (Progress) -> Unit) {
        runCatching { if (!ready.isCompleted) kotlinx.coroutines.runBlocking { ready.await() } }
            .onFailure { throw AppError(ErrorType.ENGINE, it.message) }
        dir.mkdirs()
        val cookies = CookieJarStore.copyFor(context, processId)
        val playlist = spec.playlistItems
        val request = YoutubeDLRequest(spec.url).common(context, cookies).apply {
            addOption("-P", dir.absolutePath)
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--retries", 5)
            addOption("--fragment-retries", 5)
            addOption("--concurrent-fragments", 4)
            if (playlist != null) {
                addOption("--yes-playlist")
                addOption("--playlist-items", playlist.joinToString(","))
                addOption("--ignore-errors")
                addOption("-o", "%(playlist_index)02d - %(title).70B.%(ext)s")
            } else {
                addOption("--no-playlist")
                addOption("-o", "%(title).80B.%(ext)s")
            }
            if (spec.mode == DownloadMode.AUDIO) {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", spec.audioFormat)
                addOption("--audio-quality", "0")
                // غلاف وبيانات (العنوان/الفنان) داخل ملف الصوت — عبر mutagen المدمجة مع بايثون.
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            } else {
                addOption("-f", spec.formatSelector ?: "bv*+ba/b")
                // قيد الضلع الأقصر لجودات القوائم (يعمل للأفقي والعمودي)، مع تفضيل H.264/AAC للتوافق.
                val cap = Regex("""^(\d{3,4})p""").find(spec.qualityLabel)?.groupValues?.get(1)
                if (playlist != null && cap != null) addOption("-S", "res:$cap,vcodec:h264,acodec:m4a")
                addOption("--merge-output-format", "mp4")
            }
        }

        val total = playlist?.size ?: 1
        var item = 1
        var phase = 0
        val expectTwoPhases = spec.mode == DownloadMode.VIDEO
        val itemRegex = Regex("""Downloading item (\d+) of (\d+)""")
        val speedRegex = Regex("""at\s+([\d.]+\s*[KMG]?i?B/s)""")
        var stage: String? = "جاري التحضير"

        try {
            YoutubeDL.getInstance().execute(request, processId) { percent, eta, line ->
                itemRegex.find(line)?.let { item = it.groupValues[1].toInt(); phase = 0 }
                when {
                    line.startsWith("[download] Destination:") -> {
                        phase++
                        stage = if (spec.mode == DownloadMode.AUDIO) "تحميل الصوت"
                        else if (phase >= 2) "تحميل الصوت" else "تحميل الفيديو"
                    }
                    line.startsWith("[Merger]") -> stage = "دمج الفيديو والصوت"
                    line.startsWith("[ExtractAudio]") -> stage = "تحويل الصوت"
                    line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]") -> stage = "إضافة الغلاف والبيانات"
                    line.startsWith("[VideoRemuxer]") || line.startsWith("[FixupM") -> stage = "معالجة الملف"
                }
                val post = line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") ||
                    line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]") || line.startsWith("[ThumbnailsConvertor]") ||
                    line.startsWith("[VideoRemuxer]") || line.startsWith("[FixupM")
                val p = (percent / 100f).coerceIn(0f, 1f)
                val within = when {
                    post -> 0.99f
                    !expectTwoPhases -> p * 0.97f
                    phase <= 1 -> p * 0.88f
                    else -> 0.88f + p * 0.10f
                }
                val overall = ((item - 1) + within) / total
                val label = if (total > 1) "$stage ($item/$total)" else stage
                onProgress(Progress(overall.coerceIn(0f, 1f), speedRegex.find(line)?.groupValues?.get(1), eta.takeIf { it > 0 && !post }, label))
            }
        } catch (c: YoutubeDL.CanceledException) {
            throw c
        } catch (t: Throwable) {
            // مع --ignore-errors قد تفشل عناصر وتنجح أخرى؛ القرار النهائي للمستدعي حسب الملفات الناتجة.
            if (playlist == null || outputFiles(dir).isEmpty()) throw classify(context, t.message)
        } finally {
            cookies?.delete()
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** الملفات النهائية داخل مجلد المهمة (بدون الأجزاء المؤقتة). */
    fun outputFiles(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { f ->
            f.isFile && f.length() > 0 && f.extension.lowercase() !in setOf("part", "ytdl", "tmp", "temp", "sqlite3") &&
                !f.name.contains(".part-Frag") && !Regex("""\.f\d+\.\w+$""").containsMatchIn(f.name)
        }.sortedBy { it.name }

    // ---------------------------------------------------------------- تصنيف الأخطاء

    fun classify(context: Context, raw: String?): AppError {
        val msg = raw.orEmpty()
        val low = msg.lowercase()
        val detail = msg.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR") }
            ?.removePrefix("ERROR:")?.trim()?.take(220)
            ?: msg.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.take(220)
        val type = when {
            !Net.isOnline(context) -> ErrorType.NO_INTERNET
            listOf("login required", "log in", "sign in", "private", "cookies", "registered users", "not available to you",
                "authentication", "requires authentication", "members-only", "age-restricted", "rate-limit reached",
                "empty media response", "this content isn't available").any { it in low } -> ErrorType.PRIVATE
            Regex("""http error 4(04|10)""").containsMatchIn(low) -> ErrorType.UNSUPPORTED
            listOf("unsupported url", "is not a valid url", "no video formats", "no media found", "no video could be found",
                "there is no video", "no video in this", "unable to extract").any { it in low } -> ErrorType.UNSUPPORTED
            listOf("name resolution", "getaddrinfo", "network is unreachable", "timed out", "connection refused",
                "connection reset", "unable to download webpage", "no address associated", "failed to resolve",
                "transporterror").any { it in low } -> ErrorType.NO_INTERNET
            else -> ErrorType.GENERIC
        }
        return AppError(type, detail)
    }
}
