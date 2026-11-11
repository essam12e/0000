package com.downloader.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.downloader.app.auth.CookieJarStore
import com.downloader.app.ui.components.GoldProgress
import com.downloader.app.ui.components.RoundIconButton
import com.downloader.app.ui.theme.AppTheme

/**
 * تسجيل دخول اختياري إلى انستجرام داخل WebView. كلمة المرور تُكتب في صفحة انستجرام نفسها
 * ولا يراها التطبيق؛ نحتفظ فقط بالكوكيز الناتجة محلياً لتمريرها إلى yt-dlp.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onClose: () -> Unit, onLoggedIn: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun check(view: WebView) {
        if (!done && CookieJarStore.webViewHasSession() && CookieJarStore.saveFromWebView(view.context)) {
            done = true
            onLoggedIn()
        }
    }

    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }
    DisposableEffect(Unit) { onDispose { webView?.apply { stopLoading(); destroy() } } }

    Column(Modifier.fillMaxSize().pointerInput(Unit) {}.background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", onClose)
            Spacer(Modifier.width(12.dp))
            Text("تسجيل الدخول إلى انستجرام", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(14.dp),
        ) {
            Icon(Icons.Rounded.Shield, null, tint = AppTheme.extra.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("استخدم حساباً ثانوياً", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "انستجرام قد يقيّد الحسابات التي تُستخدم مع أدوات التحميل. الكوكيز تُحفظ على جهازك فقط، ويمكنك مسحها بتسجيل الخروج من الإعدادات.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (progress < 1f) GoldProgress(progress, Modifier.padding(horizontal = 16.dp), height = 3.dp)
        Box(Modifier.fillMaxSize().padding(top = 6.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                                check(view)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = check(view)
                            override fun onPageFinished(view: WebView, url: String?) = check(view)
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = check(view)
                        }
                        loadUrl("https://www.instagram.com/accounts/login/")
                        webView = this
                    }
                },
            )
        }
    }
}
