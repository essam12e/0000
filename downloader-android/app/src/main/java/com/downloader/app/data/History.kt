package com.downloader.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** سجلّ التحميلات: يُحفظ محلياً فقط، ويُستخدم لكشف تكرار تحميل الرابط نفسه. */
object History {
    private const val MAX = 500
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries
    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.filesDir, "history.json")
        _entries.value = runCatching {
            json.decodeFromString(ListSerializer(HistoryEntry.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun persist() {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(HistoryEntry.serializer()), _entries.value))
            tmp.renameTo(file)
        }
    }

    fun record(url: String, key: String?, title: String, thumbnail: String?, label: String, platform: String?) {
        val entry = HistoryEntry(UUID.randomUUID().toString().take(12), url, key, title, thumbnail, label, platform)
        _entries.update { (listOf(entry) + it).take(MAX) }
        persist()
    }

    /** آخر مرة حُمّل فيها هذا المحتوى (بالمعرّف الثابت أو بالرابط الموحّد). */
    fun find(url: String, key: String?): HistoryEntry? {
        val n = normalizeUrl(url)
        return _entries.value.firstOrNull { (key != null && it.key == key) || normalizeUrl(it.url) == n }
    }

    fun remove(id: String) {
        _entries.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }
}
