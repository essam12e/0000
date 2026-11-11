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
