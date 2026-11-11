package com.downloader.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.downloader.app.auth.CookieJarStore
import com.downloader.app.data.Library
import com.downloader.app.data.SettingsStore
import com.downloader.app.engine.DownloadManager
import com.downloader.app.engine.Net
import com.downloader.app.engine.YtDlp
import com.downloader.app.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        CookieJarStore.init(this)
        DownloadService.ensureChannels(this)
        DownloadManager.init(this)
        appScope.launch(Dispatchers.IO) {
            YtDlp.init(this@App)
            autoUpdateEngine()
        }
    }

    /**
     * النسخة المدمجة من yt-dlp تتقادم سريعاً، لذا نحدّثها تلقائياً بصمت عند أول تشغيل
     * ثم مرة كل 3 أيام. (طلب واحد لصفحة إصدارات yt-dlp الرسمية — بلا أي تتبع.)
     */
    private suspend fun autoUpdateEngine() {
        val settings = SettingsStore(this)
        val last = settings.flow.first().lastEngineUpdate
        if (System.currentTimeMillis() - last < 3L * 24 * 3600 * 1000 || !Net.isOnline(this)) return
        runCatching { YtDlp.update(this) }.onSuccess { settings.setEngineUpdated(System.currentTimeMillis()) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
