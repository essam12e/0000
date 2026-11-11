package com.downloader.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * مكتبة «تحميلاتي»: ملفات محفوظة داخل مساحة التطبيق الخاصة (filesDir/downloads)
 * مع فهرس JSON. لا يُحذف منها شيء إلا بطلب صريح أو بعد نجاح الحفظ للمعرض.
 */
object Library {
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items
    private lateinit var indexFile: File
    lateinit var root: File
        private set

    fun init(context: Context) {
        root = File(context.filesDir, "downloads").apply { mkdirs() }
        indexFile = File(context.filesDir, "library.json")
        val loaded = runCatching {
            json.decodeFromString(ListSerializer(LibraryItem.serializer()), indexFile.readText())
        }.getOrDefault(emptyList())
        _items.value = loaded.filter { File(it.path).exists() }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            val tmp = File(indexFile.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(LibraryItem.serializer()), _items.value))
            tmp.renameTo(indexFile)
        }
    }

    fun add(newItems: List<LibraryItem>) {
        if (newItems.isEmpty()) return
        _items.update { newItems + it }
        persist()
    }

    /** يحذف الملف من مساحة التطبيق ومن الفهرس. */
    fun delete(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val f = File(item.path)
        f.delete()
        f.parentFile?.takeIf { it != root && it.list().isNullOrEmpty() }?.delete()
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    /** يغيّر عنوان الملف واسمه على القرص (مع الإبقاء على الامتداد). */
    fun rename(id: String, newTitle: String): Boolean {
        val item = get(id) ?: return false
        val title = newTitle.trim()
        if (title.isEmpty()) return false
        val src = File(item.path)
        val ext = src.extension
        var dest = File(src.parentFile, safeFileName(title) + if (ext.isNotEmpty()) ".$ext" else "")
        var i = 2
        while (dest.exists() && dest != src) {
            dest = File(src.parentFile, safeFileName(title) + " ($i)" + if (ext.isNotEmpty()) ".$ext" else "")
            i++
        }
        if (dest != src && !src.renameTo(dest)) return false
        _items.update { list -> list.map { if (it.id == id) it.copy(title = title, path = dest.absolutePath) else it } }
        persist()
        return true
    }

    fun get(id: String): LibraryItem? = _items.value.firstOrNull { it.id == id }
}
