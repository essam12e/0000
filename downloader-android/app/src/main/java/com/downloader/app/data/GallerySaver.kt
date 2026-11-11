package com.downloader.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * «حفظ في الجهاز»: ينسخ الملف إلى مجلد واحد منظّم داخل التنزيلات:
 *
 *     Download/داونلودر/فيديو   ·   صوتيات   ·   صور   ·   أخرى
 *
 * ثم — بعد التحقق من نجاح النسخ فقط — يحذفه من مساحة التطبيق. أي فشل يُبقي الملف الأصلي كما هو.
 * يُستخدم MediaStore.Downloads لأنه المكان الوحيد الذي يقبل كل الأنواع في مجلد واحد
 * بدون أي صلاحية تخزين على أندرويد 10+.
 */
object GallerySaver {
    const val ROOT = "داونلودر"

    fun folderOf(kind: MediaKind): String = when (kind) {
        MediaKind.VIDEO -> "فيديو"
        MediaKind.AUDIO -> "صوتيات"
        MediaKind.IMAGE -> "صور"
        MediaKind.OTHER -> "أخرى"
    }

    /** المسار كما يراه المستخدم في تطبيق الملفات. */
    fun displayPath(kind: MediaKind): String = "التنزيلات ‹ $ROOT ‹ ${folderOf(kind)}"

    suspend fun saveAndRemove(context: Context, item: LibraryItem): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(item.path)
            if (!src.exists()) throw IOException("الملف غير موجود")
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(context, item, src)
            else saveLegacy(context, item, src)
            // لم نصل هنا إلا بعد نجاح النسخ والتحقق من الحجم → الآن فقط نحذف من التطبيق.
            Library.delete(item.id)
            uri
        }
    }

    private fun saveScoped(context: Context, item: LibraryItem, src: File): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName(src.name))
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/${folderOf(item.kind)}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("تعذّر إنشاء الملف")
        try {
            var copied = 0L
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                    }
                    out.flush()
                }
            } ?: throw IOException("تعذّر فتح الملف للكتابة")
            if (copied != src.length()) throw IOException("النسخ غير مكتمل")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, item: LibraryItem, src: File): Uri {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(File(base, ROOT), folderOf(item.kind))
        if (!dir.exists() && !dir.mkdirs()) throw IOException("تعذّر إنشاء مجلد الحفظ")
        val name = safeFileName(src.name)
        var dest = File(dir, name)
        var i = 1
        while (dest.exists()) {
            dest = File(dir, "${name.substringBeforeLast('.')} ($i).${name.substringAfterLast('.', "")}")
            i++
        }
        try {
            src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it, 256 * 1024) } }
            if (dest.length() != src.length()) throw IOException("النسخ غير مكتمل")
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        MediaScannerConnection.scanFile(context, arrayOf(dest.path), arrayOf(item.mime), null)
        return Uri.fromFile(dest)
    }
}
