package com.downloader.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.downloader.app.ui.AppRoot
import com.downloader.app.ui.Tab
import com.downloader.app.ui.home.HomeViewModel
import com.downloader.app.ui.theme.DownloaderTheme

class MainActivity : ComponentActivity() {
    private val homeVm: HomeViewModel by viewModels()
    private val tab = mutableStateOf(Tab.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            DownloaderTheme { AppRoot(tab, homeVm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> {
                tab.value = Tab.HOME
                homeVm.onShared(intent.getStringExtra(Intent.EXTRA_TEXT))
            }
            intent?.getBooleanExtra(EXTRA_OPEN_LIBRARY, false) == true -> tab.value = Tab.LIBRARY
            // اختبار فقط (نسخة المحاكي): تشغيل محرك الترجمة مباشرة وكتابة النتيجة في السجل.
            BuildConfig.EMULATOR_TEST && intent?.action == "com.downloader.app.TEST_TRANSLATE" -> Thread {
                val out = com.downloader.app.engine.Translator.translate("en-orig", "ar", listOf("Hello, how are you today?", "This video is about cooking.")) {
                    android.util.Log.e("Downloader", "TEST stage: $it")
                }
                android.util.Log.e("Downloader", "TEST translate result: $out")
            }.start()
            // اختبار فقط (نسخة المحاكي): إنهاء الترجمة على فيديو موجود مع ملف SRT بنمط تفريغ يوتيوب المتدحرج.
            BuildConfig.EMULATOR_TEST && intent?.action == "com.downloader.app.TEST_SUBS" -> Thread {
                val dir = java.io.File(intent.getStringExtra("dir") ?: return@Thread)
                val video = dir.listFiles()?.firstOrNull { it.extension == "mp4" } ?: return@Thread
                java.io.File(dir, video.nameWithoutExtension + ".en-orig.srt").writeText(
                    "1\n00:00:01,000 --> 00:00:03,000\nhello and welcome\n\n" +
                        "2\n00:00:03,000 --> 00:00:03,010\nhello and welcome\n\n" +
                        "3\n00:00:03,010 --> 00:00:06,000\nhello and welcome\nto this cooking video\n\n" +
                        "4\n00:00:06,000 --> 00:00:09,000\nto this cooking video\ntoday we make bread\n\n"
                )
                val spec = com.downloader.app.data.DownloadSpec(
                    url = "test", title = "test", mode = com.downloader.app.data.DownloadMode.VIDEO,
                    subLang = "ar", subSrc = "en-orig", subLocal = true, subEmbed = intent.getBooleanExtra("embed", true),
                )
                val r = com.downloader.app.engine.Subtitles.finalize(this, spec, dir) { android.util.Log.e("Downloader", "TEST stage: $it") }
                android.util.Log.e("Downloader", "TEST subs: videos=${r.videos} platform=${r.platform} translated=${r.translated} original=${r.original} notice=${r.notice()} files=${dir.list()?.toList()}")
            }.start()
            // اختبار فقط (نسخة المحاكي): صورة النظام بلا منتقي مجلدات، فنستقبل مجلداً مُنح إذنه عبر adb.
            BuildConfig.EMULATOR_TEST && intent?.action == "com.downloader.app.TEST_SET_TREE" ->
                intent.data?.let { uri ->
                    androidx.lifecycle.ViewModelProvider(this)[com.downloader.app.ui.settings.SettingsViewModel::class.java].setSaveFolder(uri)
                }
        }
    }

    // الحافظة لا تُقرأ إلا والنافذة في المقدمة (قيد أندرويد 10+).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) homeVm.checkClipboard()
    }

    companion object {
        const val EXTRA_OPEN_LIBRARY = "open_library"
    }
}
