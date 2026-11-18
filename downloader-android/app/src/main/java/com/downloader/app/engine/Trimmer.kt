package com.downloader.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.downloader.app.data.AppError
import com.downloader.app.data.ErrorType
import com.downloader.app.data.Fmt
import com.downloader.app.data.Library
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/** قصّ مقطع من فيديو موجود في المكتبة باستخدام FFmpeg المدمج — بلا إعادة ترميز (سريع ولا يغيّر الجودة). */
object Trimmer {
    /** مدة الفيديو بالثواني من الملف نفسه، أو null إن تعذّرت قراءتها. */
    suspend fun durationOf(path: String): Long? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { (it + 500) / 1000 }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** لقطات مصغّرة موزّعة على طول الفيديو لشريط القصّ. */
    suspend fun frames(path: String, count: Int, durationSec: Long): List<Bitmap> = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        val out = ArrayList<Bitmap>()
        try {
            r.setDataSource(path)
            for (i in 0 until count) {
                val us = ((i + 0.5) / count * durationSec * 1_000_000L).toLong()
                val bmp = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { b ->
                    val k = 160f / maxOf(b.width, b.height)
                    if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
                }
                if (bmp != null) out += bmp
            }
        } catch (t: Throwable) {
            // شريط بلا لقطات أفضل من فشل الشاشة.
        } finally {
            runCatching { r.release() }
        }
        out
    }

    /**
     * يقصّ [startSec, endSec] من [item] ويضيف الناتج للمكتبة كمقطع جديد. الأصل لا يُمسّ.
     * القصّ دقيق بالثانية (يُعاد ترميز المقطع المقصوص فقط)؛ وإن تعذّر ذلك على الجهاز يُقصّ بلا إعادة ترميز.
     */
    suspend fun cut(context: Context, item: LibraryItem, startSec: Long, endSec: Long, onProgress: (Float) -> Unit = {}): LibraryItem = withContext(Dispatchers.IO) {
        val src = File(item.path)
        if (!src.exists() || endSec <= startSec) throw AppError(ErrorType.GENERIC, "تعذّر قراءة الملف")
        val id = UUID.randomUUID().toString().take(12)
        val dir = File(Library.root, id).apply { mkdirs() }
        val length = endSec - startSec
        val title = "${item.title} (${Fmt.clock(startSec)}–${Fmt.clock(endSec)})"
        val name = safeFileName(title).take(80)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath
        val head = listOf(ffmpeg, "-y", "-loglevel", "error", "-progress", "pipe:1", "-nostats", "-ss", startSec.toString(), "-i", src.absolutePath, "-t", length.toString())

        // 1) دقيق: إعادة ترميز المقطع فقط.
        var out = File(dir, "$name.mp4")
        var log = run(context, head + listOf("-map", "0:v:0", "-map", "0:a?", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", out.absolutePath), length, onProgress)
        // 2) احتياطي: بلا إعادة ترميز (قد تبدأ اللقطة أبكر قليلاً).
        if (log != null || out.length() < 1024) {
            android.util.Log.e("Downloader", "accurate trim failed, falling back to stream copy: $log")
            out.delete()
            out = File(dir, name + "." + src.extension.ifEmpty { "mp4" })
            log = run(context, head + listOf("-map", "0", "-c", "copy", "-avoid_negative_ts", "make_zero", "-movflags", "+faststart", out.absolutePath), length, onProgress)
        }
        if (log != null || out.length() < 1024) {
            android.util.Log.e("Downloader", "trim failed: $log")
            dir.deleteRecursively()
            throw AppError(ErrorType.GENERIC, "تعذّر قصّ المقطع" + log?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let { "\n" + it.take(160) }.orEmpty())
        }
        val mime = if (out.extension.equals("mp4", true)) "video/mp4" else item.mime
        val clip = LibraryItem(
            id = id, path = out.absolutePath, title = title, kind = item.kind, mime = mime, size = out.length(),
            durationSec = length, thumbUrl = item.thumbUrl, sourceUrl = item.sourceUrl, clip = true,
        )
        Library.add(listOf(clip))
        clip
    }

    /** يشغّل FFmpeg ويتابع تقدّمه. يعيد null عند النجاح، أو نص الخطأ. */
    private fun run(context: Context, cmd: List<String>, lengthSec: Long, onProgress: (Float) -> Unit): String? {
        val base = File(context.noBackupFilesDir, "youtubedl-android")
        val log = StringBuilder()
        return try {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).apply {
                environment()["LD_LIBRARY_PATH"] = "${base.absolutePath}/packages/ffmpeg/usr/lib:${base.absolutePath}/packages/python/usr/lib"
            }.start()
            val reader = thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        // سطور التقدّم: out_time_us=1234567 (وفي إصدارات أقدم out_time_ms بالقيمة نفسها)
                        val us = line.substringAfter("out_time_us=", "").ifEmpty { line.substringAfter("out_time_ms=", "") }.trim().toLongOrNull()
                        if (us != null) { if (lengthSec > 0) onProgress((us / 1_000_000f / lengthSec).coerceIn(0f, 1f)) }
                        else if (!line.contains('=')) log.appendLine(line)
                    }
                }
            }
            val exit = process.waitFor()
            reader.join(2000)
            if (exit == 0) null else log.toString().ifBlank { "exit $exit" }
        } catch (t: Throwable) {
            t.message ?: "error"
        }
    }
}
