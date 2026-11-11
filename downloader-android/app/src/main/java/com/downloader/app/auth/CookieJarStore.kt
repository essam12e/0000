package com.downloader.app.auth

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * كوكيز انستجرام: تُؤخذ من WebView بعد تسجيل الدخول وتُحفظ محلياً بصيغة Netscape
 * (cookies.txt) داخل مساحة التطبيق الخاصة فقط، ثم تُمرَّر إلى yt-dlp. لا تُرسل لأي خادم.
 */
object CookieJarStore {
    private const val INSTAGRAM = "https://www.instagram.com"
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    private fun file(context: Context) = File(context.filesDir, "cookies.txt")

    fun init(context: Context) {
        _loggedIn.value = file(context).let { it.exists() && it.readText().contains("\tsessionid\t") }
    }

    /** هل أتمّ WebView تسجيل الدخول؟ (وجود كوكي sessionid) */
    fun webViewHasSession(): Boolean =
        CookieManager.getInstance().getCookie(INSTAGRAM).orEmpty().split(';').any { it.trim().startsWith("sessionid=") }

    /** يحفظ كوكيز WebView الحالية في cookies.txt. */
    fun saveFromWebView(context: Context): Boolean {
        val raw = CookieManager.getInstance().getCookie(INSTAGRAM) ?: return false
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        var hasSession = false
        raw.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            if (name.isEmpty()) return@forEach
            if (name == "sessionid") hasSession = true
            sb.append(".instagram.com\tTRUE\t/\tTRUE\t").append(expiry).append('\t')
                .append(name).append('\t').append(value).append('\n')
        }
        if (!hasSession) return false
        file(context).writeText(sb.toString())
        CookieManager.getInstance().flush()
        _loggedIn.value = true
        return true
    }

    /** نسخة مؤقتة لكل عملية (yt-dlp يعيد كتابة الملف عند الخروج، والتحميلات متوازية). */
    fun copyFor(context: Context, tag: String): File? {
        val src = file(context)
        if (!src.exists()) return null
        val dir = File(context.cacheDir, "cookies").apply { mkdirs() }
        return runCatching { src.copyTo(File(dir, "$tag.txt"), overwrite = true) }.getOrNull()
    }

    /** تسجيل الخروج: يمسح كل الكوكيز وبيانات WebView والملف المحفوظ. */
    fun logout(context: Context) {
        file(context).delete()
        File(context.cacheDir, "cookies").deleteRecursively()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        _loggedIn.value = false
    }
}
