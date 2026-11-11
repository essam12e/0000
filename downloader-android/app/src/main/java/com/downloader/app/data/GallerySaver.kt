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

    /** مجلد مخصص اختاره المستخدم من الإعدادات (null = المكان الافتراضي). يُحدَّث من App. */
    @Volatile var customTree: Uri? = null

    private val subtitleExts = setOf("srt", "vtt", "ass", "lrc")

    /** ملفات الترجمة تُحفظ بجانب الفيديو (وبالاسم نفسه) حتى تلتقطها المشغّلات تلقائياً. */
    fun folderOf(item: LibraryItem): String =
        if (File(item.path).extension.lowercase() in subtitleExts) folderOf(MediaKind.VIDEO) else folderOf(item.kind)

    fun folderOf(kind: MediaKind): String = when (kind) {
        MediaKind.VIDEO -> "فيديو"
        MediaKind.AUDIO -> "صوتيات"
        MediaKind.IMAGE -> "صور"
        MediaKind.OTHER -> "أخرى"
    }

    /** المسار كما يراه المستخدم في تطبيق الملفات. */
    fun displayPath(context: Context, item: LibraryItem): String = "${rootLabel(context)} ‹ ${folderOf(item)}"

    /** اسم المكان الحالي: المجلد المخصص أو «التنزيلات ‹ داونلودر». */
    fun rootLabel(context: Context): String = customTree?.let { tree ->
        runCatching { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)?.name }.getOrNull() ?: "المجلد المختار"
    } ?: "التنزيلات ‹ $ROOT"

    suspend fun saveAndRemove(context: Context, item: LibraryItem): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(item.path)
            if (!src.exists()) throw IOException("الملف غير موجود")
            val tree = customTree
            val uri = when {
                tree != null -> saveToTree(context, tree, item, src)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveScoped(context, item, src)
                else -> saveLegacy(context, item, src)
            }
            // لم نصل هنا إلا بعد نجاح النسخ والتحقق من الحجم → الآن فقط نحذف من التطبيق.
            Library.delete(item.id)
            uri
        }
    }

    /** الحفظ في مجلد اختاره المستخدم (Storage Access Framework) بالتقسيم نفسه: فيديو/صوتيات/صور/أخرى. */
    private fun saveToTree(context: Context, tree: Uri, item: LibraryItem, src: File): Uri {
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)
        if (root == null || !root.exists() || !root.canWrite()) {
            throw IOException("تعذّر الوصول إلى المجلد المختار — اختره من جديد من الإعدادات")
        }
        val folder = folderOf(item)
        val dir = root.findFile(folder)?.takeIf { it.isDirectory } ?: root.createDirectory(folder)
            ?: throw IOException("تعذّر إنشاء مجلد $folder")
        val safe = safeFileName(src.name)
        var name = safe
        var i = 1
        while (dir.findFile(name) != null) {
            name = "${safe.substringBeforeLast('.')} ($i).${safe.substringAfterLast('.', "")}"
            i++
        }
        // نوع عام حتى لا يضيف مزوّد الملفات امتداداً ثانياً إلى الاسم.
        val doc = dir.createFile("application/octet-stream", name) ?: throw IOException("تعذّر إنشاء الملف")
        try {
            var copied = 0L
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
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
            return doc.uri
        } catch (t: Throwable) {
            runCatching { doc.delete() }
            throw t
        }
    }

    private fun saveScoped(context: Context, item: LibraryItem, src: File): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName(src.name))
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/${folderOf(item)}")
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
        val dir = File(File(base, ROOT), folderOf(item))
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
