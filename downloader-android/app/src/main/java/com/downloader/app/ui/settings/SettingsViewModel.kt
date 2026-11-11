package com.downloader.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.downloader.app.auth.CookieJarStore
import com.downloader.app.data.AppError
import com.downloader.app.data.AppSettings
import com.downloader.app.data.SettingsStore
import com.downloader.app.data.message
import com.downloader.app.engine.DownloadManager
import com.downloader.app.engine.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    val settings: StateFlow<AppSettings> = store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val loggedIn = CookieJarStore.loggedIn

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion
    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating
    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { _cacheBytes.value = cacheTargets().sumOf { size(it) } }
        viewModelScope.launch { _engineVersion.value = YtDlp.version(getApplication()) }
    }

    private fun size(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { size(it) } ?: 0L

    private fun cacheTargets(): List<File> =
        getApplication<Application>().cacheDir.listFiles().orEmpty().toList() + DownloadManager.orphanDirs()

    fun setQuality(v: Int) = viewModelScope.launch { store.setDefaultQuality(v) }
    fun setAudioFormat(v: String) = viewModelScope.launch { store.setAudioFormat(v) }
    fun setParallel(v: Int) = viewModelScope.launch { store.setMaxParallel(v) }

    fun updateEngine() {
        if (_updating.value) return
        _updating.value = true
        viewModelScope.launch {
            try {
                val updated = YtDlp.update(getApplication())
                store.setEngineUpdated(System.currentTimeMillis())
                _engineVersion.value = YtDlp.version(getApplication())
                _messages.tryEmit(if (updated) "تم تحديث المحرك إلى ${_engineVersion.value ?: "أحدث إصدار"}" else "المحرك محدّث بالفعل")
            } catch (e: AppError) {
                _messages.tryEmit("تعذّر التحديث: ${e.type.message()}")
            } finally {
                _updating.value = false
            }
        }
    }

    fun clearCache() {
        if (DownloadManager.hasActive) {
            _messages.tryEmit("أوقف التحميلات الجارية أولاً ثم امسح الكاش")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val before = cacheTargets().sumOf { size(it) }
            runCatching {
                SingletonImageLoader.get(getApplication()).let { it.memoryCache?.clear(); it.diskCache?.clear() }
            }
            cacheTargets().forEach { it.deleteRecursively() }
            _cacheBytes.value = cacheTargets().sumOf { size(it) }
            _messages.tryEmit("تم مسح ${com.downloader.app.data.Fmt.bytes(before.coerceAtLeast(1024))} — ملفاتك في «تحميلاتي» لم تُمسّ")
        }
    }

    fun logout() {
        CookieJarStore.logout(getApplication())
        _messages.tryEmit("تم تسجيل الخروج ومسح الكوكيز")
    }
}
