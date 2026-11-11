package com.downloader.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downloader.app.data.GallerySaver
import com.downloader.app.data.Library
import com.downloader.app.data.LibraryItem
import com.downloader.app.engine.DownloadManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    val tasks = DownloadManager.tasks
    val items = Library.items

    private val _saving = MutableStateFlow<Set<String>>(emptySet())
    val saving: StateFlow<Set<String>> = _saving
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun pause(id: String) = DownloadManager.pause(id)
    fun resume(id: String) = DownloadManager.resume(id)
    fun cancel(id: String) = DownloadManager.cancel(id)

    /** حفظ للمعرض: الحذف من التطبيق يحدث داخل GallerySaver بعد نجاح النسخ فقط. */
    fun saveToGallery(item: LibraryItem, onDone: (Boolean) -> Unit = {}) {
        if (item.id in _saving.value) return
        _saving.update { it + item.id }
        viewModelScope.launch {
            val result = GallerySaver.saveAndRemove(getApplication(), item)
            _saving.update { it - item.id }
            _messages.tryEmit(
                if (result.isSuccess) "تم الحفظ في المعرض" else "تعذّر الحفظ — الملف ما زال في التطبيق"
            )
            onDone(result.isSuccess)
        }
    }

    fun saveAll() {
        val all = items.value.filter { it.id !in _saving.value }
        if (all.isEmpty()) return
        _saving.update { it + all.map { i -> i.id } }
        viewModelScope.launch {
            var ok = 0
            all.forEach { item ->
                if (GallerySaver.saveAndRemove(getApplication(), item).isSuccess) ok++
                _saving.update { it - item.id }
            }
            _messages.tryEmit(
                if (ok == all.size) "تم حفظ الكل في المعرض (${com.downloader.app.data.Fmt.files(ok)})"
                else "حُفظ $ok من ${all.size} — الباقي ما زال في التطبيق"
            )
        }
    }

    fun delete(item: LibraryItem) {
        Library.delete(item.id)
        _messages.tryEmit("تم الحذف")
    }
}
