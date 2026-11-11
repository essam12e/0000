package com.downloader.app.ui.home

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downloader.app.data.AppError
import com.downloader.app.data.DownloadMode
import com.downloader.app.data.DownloadSpec
import com.downloader.app.data.ErrorType
import com.downloader.app.data.Fmt
import com.downloader.app.data.History
import com.downloader.app.data.HistoryEntry
import com.downloader.app.data.SubtitleTrack
import com.downloader.app.data.MediaInfo
import com.downloader.app.data.QualityOption
import com.downloader.app.data.SettingsStore
import com.downloader.app.data.extractUrl
import com.downloader.app.engine.Direct
import com.downloader.app.engine.GalleryDl
import com.downloader.app.engine.DownloadManager
import com.downloader.app.engine.Net
import com.downloader.app.engine.YtDlp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Idle : HomeState
    data object Loading : HomeState
    data class Failed(val type: ErrorType, val detail: String?) : HomeState
    data class Ready(val info: MediaInfo) : HomeState
}

data class HomeUi(
    val url: String = "",
    val state: HomeState = HomeState.Idle,
    val audioMode: Boolean = false,
    val selectedOptionId: String? = null,
    val selectedItems: Set<Int> = emptySet(),
    val audioFormat: String = "mp3",
    val clipboardUrl: String? = null,
    val audioLang: String? = null,
    val subtitle: SubtitleTrack? = null,
    val subEmbed: Boolean = true,
    val trimStart: Long? = null,
    val trimEnd: Long? = null,
    /** آخر تحميل سابق للمحتوى نفسه إن وُجد. */
    val duplicate: HistoryEntry? = null,
)

sealed interface HomeEvent {
    data class Enqueued(val count: Int) : HomeEvent
    data class ConfirmDuplicate(val previous: HistoryEntry) : HomeEvent
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { settings.flow.collect { s -> _ui.update { it.copy(audioFormat = s.audioFormat) } } }
    }

    fun onUrlChange(v: String) = _ui.update {
        it.copy(url = v, state = if (it.state is HomeState.Failed) HomeState.Idle else it.state)
    }

    fun clear() {
        job?.cancel()
        _ui.update { it.copy(url = "", state = HomeState.Idle, selectedOptionId = null, selectedItems = emptySet()) }
    }

    fun cancelAnalysis() {
        job?.cancel()
        _ui.update { it.copy(state = HomeState.Idle) }
    }

    private fun clipboardText(): String? {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return runCatching {
            if (!cm.hasPrimaryClip()) null
            else cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(getApplication())?.toString()
        }.getOrNull()
    }

    /** زر «لصق من الحافظة»: يلصق ويحلّل مباشرة إن كان رابطاً. */
    fun paste() {
        val text = clipboardText()
        val url = extractUrl(text)
        if (url != null) {
            _ui.update { it.copy(url = url, clipboardUrl = null) }
            analyze()
        } else if (!text.isNullOrBlank()) {
            _ui.update { it.copy(url = text.trim().take(500)) }
        }
    }

    /** عند فتح التطبيق: إن كان في الحافظة رابط جديد نعرضه كاقتراح (لا نلصقه بالقوة). */
    fun checkClipboard() {
        viewModelScope.launch {
            val url = extractUrl(clipboardText()) ?: return@launch
            val seen = settings.flow.first().lastClipboardUrl
            if (url == seen || url == _ui.value.url) return@launch
            _ui.update { it.copy(clipboardUrl = url) }
        }
    }

    fun useClipboardSuggestion() {
        val url = _ui.value.clipboardUrl ?: return
        viewModelScope.launch { settings.setLastClipboardUrl(url) }
        _ui.update { it.copy(url = url, clipboardUrl = null) }
        analyze()
    }

    fun dismissClipboardSuggestion() {
        val url = _ui.value.clipboardUrl ?: return
        viewModelScope.launch { settings.setLastClipboardUrl(url) }
        _ui.update { it.copy(clipboardUrl = null) }
    }

    /** رابط وصل بالمشاركة من تطبيق آخر. */
    fun onShared(text: String?) {
        val url = extractUrl(text) ?: return
        _ui.update { it.copy(url = url, clipboardUrl = null) }
        analyze()
    }

    fun analyze() {
        val raw = _ui.value.url.trim()
        val url = extractUrl(raw)
            ?: raw.takeIf { it.isNotEmpty() && !it.contains(' ') && it.contains('.') }?.let { "https://$it" }
        if (url == null) {
            _ui.update { it.copy(state = HomeState.Failed(ErrorType.UNSUPPORTED, "ألصق رابطاً صحيحاً يبدأ بـ https://")) }
            return
        }
        job?.cancel()
        _ui.update { it.copy(url = url, state = HomeState.Loading, clipboardUrl = null) }
        job = viewModelScope.launch {
            try {
                if (!Net.isOnline(getApplication())) throw AppError(ErrorType.NO_INTERNET)
                val info = Direct.probe(url) ?: try {
                    YtDlp.analyze(getApplication(), url)
                } catch (e: AppError) {
                    // منشورات الصور وما لا يدعمه yt-dlp: نجرّب وسوم الصفحة نفسها.
                    if (e.type != ErrorType.UNSUPPORTED && e.type != ErrorType.GENERIC) throw e
                    // 1) gallery-dl للصور والكاروسيل  2) وسوم الصفحة كحل أخير.
                    val viaGallery = try {
                        GalleryDl.resolve(getApplication(), url)
                    } catch (g: AppError) {
                        if (g.type == ErrorType.PRIVATE) throw g
                        null
                    }
                    viaGallery ?: Direct.scrapeOpenGraph(url) ?: throw e
                }
                val s = settings.flow.first()
                val wantAudio = (s.defaultQuality == -1 && info.hasAudio && !info.isDirect) || (info.videoOptions.isEmpty() && !info.isDirect && !info.viaGallery)
                val selected = pickDefault(info.videoOptions, s.defaultQuality)
                _ui.update {
                    it.copy(
                        state = HomeState.Ready(info), audioMode = wantAudio, selectedOptionId = selected?.id,
                        selectedItems = info.items.map { item -> item.index }.toSet(),
                        audioLang = info.audioTracks.firstOrNull { t -> t.original }?.lang,
                        subtitle = null, trimStart = null, trimEnd = null,
                        duplicate = History.find(info.sourceUrl, info.mediaKey),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                if (isActive) _ui.update { it.copy(state = HomeState.Failed(e.type, e.detail)) }
            } catch (e: Throwable) {
                if (isActive) _ui.update { it.copy(state = HomeState.Failed(ErrorType.GENERIC, e.message)) }
            }
        }
    }

    /** الأعلى افتراضياً؛ أو أقرب جودة لا تتجاوز المفضّلة في الإعدادات. */
    private fun pickDefault(options: List<QualityOption>, preferred: Int): QualityOption? {
        if (options.isEmpty()) return null
        if (preferred <= 0) return options.first()
        return options.firstOrNull { it.shortSide <= preferred * 1.07 } ?: options.last()
    }

    fun setAudioMode(v: Boolean) = _ui.update { it.copy(audioMode = v) }
    fun selectOption(id: String) = _ui.update { it.copy(selectedOptionId = id, audioMode = false) }
    fun toggleItem(index: Int) = _ui.update {
        it.copy(selectedItems = if (index in it.selectedItems) it.selectedItems - index else it.selectedItems + index)
    }

    fun toggleAllItems() = _ui.update {
        val all = (it.state as? HomeState.Ready)?.info?.items?.map { i -> i.index }?.toSet().orEmpty()
        it.copy(selectedItems = if (it.selectedItems.size == all.size) emptySet() else all)
    }

    fun setAudioLang(lang: String?) = _ui.update { it.copy(audioLang = lang) }
    fun setSubtitle(track: SubtitleTrack?, embed: Boolean) = _ui.update { it.copy(subtitle = track, subEmbed = embed) }
    fun setTrim(start: Long?, end: Long?) = _ui.update { it.copy(trimStart = start, trimEnd = end) }

    /** [force] = المستخدم أكّد إعادة تحميل محتوى سبق تحميله. */
    fun download(force: Boolean = false) {
        val ui = _ui.value
        val info = (ui.state as? HomeState.Ready)?.info ?: return
        if (!force && ui.duplicate != null) {
            _events.tryEmit(HomeEvent.ConfirmDuplicate(ui.duplicate))
            return
        }
        var count = 0
        var label = "تحميل مباشر"
        if (info.isDirect) {
            info.directFiles.forEach { f ->
                DownloadManager.enqueue(
                    DownloadSpec(
                        url = f.url, title = if (info.directFiles.size == 1) info.title else f.fileName,
                        thumbnail = info.thumbnail, mode = DownloadMode.DIRECT, qualityLabel = "تحميل مباشر",
                        directName = f.fileName, expectedBytes = f.sizeBytes,
                    )
                )
                count++
            }
        } else if (info.viaGallery) {
            val items = ui.selectedItems.sorted().takeIf { it.isNotEmpty() } ?: return
            DownloadManager.enqueue(
                DownloadSpec(
                    url = info.sourceUrl, title = info.title, thumbnail = info.thumbnail,
                    mode = DownloadMode.GALLERY, qualityLabel = "الجودة الأصلية", playlistItems = items,
                )
            )
            count = items.size
            label = "الجودة الأصلية"
        } else {
            val option = info.videoOptions.firstOrNull { it.id == ui.selectedOptionId } ?: info.videoOptions.firstOrNull()
            val audio = ui.audioMode || option == null
            val items = if (info.isPlaylist) ui.selectedItems.sorted().takeIf { it.isNotEmpty() } ?: return else null
            val single = !info.isPlaylist
            val trim = if (single && ui.trimStart != null && ui.trimEnd != null) ui.trimStart to ui.trimEnd else null
            val fullSize = if (audio) info.audioSizeBytes else option?.sizeBytes
            val fraction = if (trim != null && (info.durationSec ?: 0) > 0) (trim.second - trim.first).toDouble() / info.durationSec!! else 1.0
            label = listOfNotNull(
                if (audio) "صوت ${ui.audioFormat.uppercase()}" else option!!.label,
                trim?.let { "مقطع ${Fmt.clock(it.first)}–${Fmt.clock(it.second)}" },
            ).joinToString("  ·  ") { Fmt.iso(it) }
            DownloadManager.enqueue(
                DownloadSpec(
                    url = info.sourceUrl, title = info.title, thumbnail = info.thumbnail,
                    mode = if (audio) DownloadMode.AUDIO else DownloadMode.VIDEO,
                    formatSelector = option?.formatSelector,
                    qualityLabel = label,
                    audioFormat = ui.audioFormat, playlistItems = items,
                    expectedBytes = fullSize?.let { (it * fraction).toLong() },
                    durationSec = if (trim != null) trim.second - trim.first else info.durationSec,
                    audioLang = ui.audioLang.takeIf { single && info.audioTracks.isNotEmpty() },
                    subLang = ui.subtitle?.lang.takeIf { single && !audio },
                    subAuto = ui.subtitle?.auto == true,
                    subEmbed = ui.subEmbed,
                    trimStart = trim?.first, trimEnd = trim?.second,
                )
            )
            count = items?.size ?: 1
        }
        History.record(info.sourceUrl, info.mediaKey, info.title, info.thumbnail, label, info.platform)
        _ui.update { it.copy(duplicate = History.find(info.sourceUrl, info.mediaKey)) }
        _events.tryEmit(HomeEvent.Enqueued(count))
    }
}
