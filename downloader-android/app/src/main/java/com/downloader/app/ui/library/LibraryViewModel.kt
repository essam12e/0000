package com.downloader.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downloader.app.data.Fmt
import com.downloader.app.data.GallerySaver
import com.downloader.app.data.History
import com.downloader.app.data.Library
import com.downloader.app.data.LibraryItem
import com.downloader.app.data.MediaKind
import com.downloader.app.engine.DownloadManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    NEWEST("الأحدث أولاً"), OLDEST("الأقدم أولاً"), NAME("الاسم"), SIZE("الحجم (الأكبر أولاً)"), KIND("النوع"),
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    val tasks = DownloadManager.tasks
    val history = History.entries

    /** كل الملفات (للعدّ والمشغّل). */
    val allItems = Library.items

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _sort = MutableStateFlow(SortMode.NEWEST)
    val sort: StateFlow<SortMode> = _sort

    /** الملفات بعد البحث والفرز. */
    val items: StateFlow<List<LibraryItem>> = combine(Library.items, _query, _sort) { list, q, sort ->
        val filtered = if (q.isBlank()) list else list.filter { it.title.contains(q.trim(), ignoreCase = true) }
        when (sort) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.addedAt }
            SortMode.OLDEST -> filtered.sortedBy { it.addedAt }
            SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortMode.SIZE -> filtered.sortedByDescending { it.size }
            SortMode.KIND -> filtered.sortedWith(compareBy<LibraryItem>({ it.kind.ordinal }, { -it.addedAt }))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    private val _saving = MutableStateFlow<Set<String>>(emptySet())
    val saving: StateFlow<Set<String>> = _saving
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun setQuery(q: String) { _query.value = q }
    fun setSort(s: SortMode) { _sort.value = s }

    fun toggleSelect(id: String) = _selected.update { if (id in it) it - id else it + id }
    fun selectAll() { _selected.value = items.value.map { it.id }.toSet() }
    fun clearSelection() { _selected.value = emptySet() }
    fun selectedItems(): List<LibraryItem> = Library.items.value.filter { it.id in _selected.value }

    fun pause(id: String) = DownloadManager.pause(id)
    fun resume(id: String) = DownloadManager.resume(id)
    fun cancel(id: String) = DownloadManager.cancel(id)

    /** حفظ في الجهاز: الحذف من التطبيق يحدث داخل GallerySaver بعد نجاح النسخ فقط. */
    fun saveToGallery(item: LibraryItem, onDone: (Boolean) -> Unit = {}) {
        if (item.id in _saving.value) return
        _saving.update { it + item.id }
        viewModelScope.launch {
            val result = GallerySaver.saveAndRemove(getApplication(), item)
            _saving.update { it - item.id }
            _selected.update { it - item.id }
            _messages.tryEmit(
                if (result.isSuccess) "حُفظ في ${GallerySaver.displayPath(getApplication(), item)}"
                else (result.exceptionOrNull()?.message?.takeIf { it.any { c -> c in '\u0600'..'\u06FF' } } ?: "تعذّر الحفظ") + " — الملف ما زال في التطبيق"
            )
            onDone(result.isSuccess)
        }
    }

    /** يحفظ مجموعة ملفات (الكل أو المحدد). */
    fun saveMany(list: List<LibraryItem>) {
        val all = list.filter { it.id !in _saving.value }
        if (all.isEmpty()) return
        _saving.update { it + all.map { i -> i.id } }
        viewModelScope.launch {
            var ok = 0
            all.forEach { item ->
                if (GallerySaver.saveAndRemove(getApplication(), item).isSuccess) ok++
                _saving.update { it - item.id }
                _selected.update { it - item.id }
            }
            _messages.tryEmit(
                if (ok == all.size) "حُفظ ${Fmt.files(ok)} في ${GallerySaver.rootLabel(getApplication())}"
                else "حُفظ $ok من ${all.size} — الباقي ما زال في التطبيق"
            )
        }
    }

    fun delete(item: LibraryItem) {
        Library.delete(item.id)
        _selected.update { it - item.id }
        _messages.tryEmit("تم الحذف")
    }

    fun deleteMany(list: List<LibraryItem>) {
        list.forEach { Library.delete(it.id) }
        _selected.value = emptySet()
        _messages.tryEmit("حُذف ${Fmt.files(list.size)}")
    }

    fun rename(item: LibraryItem, title: String) {
        _messages.tryEmit(if (Library.rename(item.id, title)) "تمت إعادة التسمية" else "تعذّرت إعادة التسمية")
    }

    fun removeHistory(id: String) = History.remove(id)
    fun clearHistory() {
        History.clear()
        _messages.tryEmit("تم مسح السجلّ")
    }

    fun kindCount(kind: MediaKind) = Library.items.value.count { it.kind == kind }
}
