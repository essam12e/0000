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
 * «حفظ للمعرض»: ينسخ الملف إلى المعرض عبر MediaStore ثم — بعد التحقق من نجاح النسخ فقط —
 * يحذفه من مساحة التطبيق. أي فشل يُبقي الملف الأصلي كما هو.
 */
object GallerySaver {
    private const val FOLDER = "Downloader"

    private fun publicDir(kind: MediaKind): String = when (kind) {
        MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES
        MediaKind.IMAGE -> Environment.DIRECTORY_PICTURES
        MediaKind.AUDIO -> Environment.DIRECTORY_MUSIC
        MediaKind.OTHER -> Environment.DIRECTORY_DOWNLOADS
    }

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
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection = when (item.kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
            MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(volume)
            MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
            MediaKind.OTHER -> MediaStore.Downloads.getContentUri(volume)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${publicDir(item.kind)}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("تعذّر إنشاء الملف في المعرض")
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
            } ?: throw IOException("تعذّر فتح الملف في المعرض")
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
        val dir = File(Environment.getExternalStoragePublicDirectory(publicDir(item.kind)), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("تعذّر إنشاء مجلد الحفظ")
        var dest = File(dir, src.name)
        var i = 1
        while (dest.exists()) {
            dest = File(dir, "${src.nameWithoutExtension} ($i).${src.extension}")
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
