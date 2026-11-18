package com.downloader.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.downloader.app.BuildConfig
import com.downloader.app.auth.CookieJarStore
import com.downloader.app.data.AppError
import com.downloader.app.data.DirectFile
import com.downloader.app.data.ErrorType
import com.downloader.app.data.MediaInfo
import com.downloader.app.data.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Request
import java.math.BigInteger
import kotlin.coroutines.resume

/**
 * ريلز ومنشورات انستجرام العامة بلا تسجيل دخول.
 *
 * انستجرام يرفض الطلبات التي لا تأتي من متصفح حقيقي (يفحص بصمة الاتصال)، ولهذا يفشل yt-dlp على الجوال
 * بدون حساب. الحل هنا هو ما تفعله التطبيقات التي «تعمل طبيعي»: نفتح صفحة المنشور في WebView غير مرئي
 * (محرك كروم نفسه الموجود في الجهاز)، ومن داخل الصفحة نطلب بيانات المنشور من انستجرام نفسه،
 * ثم نقرأ منها رابط ملف الفيديو/الصورة ونحمّله مباشرة. لا خادم وسيط ولا طرف ثالث.
 */
object Instagram {
    private const val HOST = "https://www.instagram.com"
    private const val APP_ID = "936619743392459"
    private const val DOC_ID = "27130156389949648"
    private const val QUERY_NAME = "PolarisLoggedOutDesktopWWWPostRootContentQuery"
    /** المحاكي بلا تسريع بطيء جداً في تشغيل WebView، فمهلته أطول في نسخة الاختبار. */
    private val TIMEOUT_MS = if (BuildConfig.EMULATOR_TEST) 900_000L else 35_000L
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** للاختبار على المحاكي فقط: عنوان خادم محلي يحاكي صفحات انستجرام. */
    @Volatile
    var baseOverride: String? = null

    /** آخر سبب فشل (يُرفق بالملاحظات لتشخيص المشكلة). */
    @Volatile
    var lastDiagnosis: String? = null
        private set

    private val postRegex = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")

    /** معرّف المنشور (shortcode) من رابط ريل/منشور، أو null إن لم يكن الرابط منشوراً. */
    fun shortcodeOf(url: String): String? = postRegex.find(url)?.groupValues?.get(1)?.takeIf { it != "audio" }

    /** روابط المشاركة القصيرة (instagram.com/share/…) تحتاج تتبّع التحويل أولاً. */
    private fun isShareLink(url: String) = Regex("""instagram\.com/share/""").containsMatchIn(url)

    /** الرقم الداخلي للمنشور من معرّفه النصي. المعرّفات الأطول من 28 خانة تخص منشورات خاصة. */
    fun mediaPk(shortcode: String): String {
        val code = if (shortcode.length > 28) shortcode.dropLast(28) else shortcode
        var n = BigInteger.ZERO
        val base = BigInteger.valueOf(64)
        for (c in code) {
            val i = ALPHABET.indexOf(c)
            if (i < 0) throw IllegalArgumentException("bad shortcode")
            n = n.multiply(base).add(BigInteger.valueOf(i.toLong()))
        }
        return n.toString()
    }

    /**
     * يحلّل رابط منشور/ريل. يعيد null إن لم يكن الرابط لانستجرام أو تعذّر الوصول بهذه الطريقة
     * (فيُترك للمسارات الأخرى). يرمي [AppError] من نوع PRIVATE إن كان المنشور يتطلب حساباً.
     */
    suspend fun resolve(context: Context, rawUrl: String): MediaInfo? {
        lastDiagnosis = null
        var url = rawUrl
        if (isShareLink(url)) url = followRedirect(url) ?: url
        val code = shortcodeOf(url) ?: return null
        val pk = runCatching { mediaPk(code) }.getOrNull() ?: return null
        val loggedIn = CookieJarStore.loggedIn.value
        val raw = withTimeoutOrNull(TIMEOUT_MS) { fetchInWebView(context.applicationContext, code, pk, loggedIn) }
        if (raw == null) {
            lastDiagnosis = "انستجرام: انتهت المهلة قبل تحميل الصفحة"
            return null
        }
        val result = runCatching { parse(raw, url, code) }.getOrElse {
            if (it is AppError) throw it
            lastDiagnosis = "انستجرام: تعذّرت قراءة الرد (${it.javaClass.simpleName})"
            null
        }
        return result?.let { withSizes(it) }
    }

    private suspend fun followRedirect(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Net.client.newCall(Request.Builder().url(url).header("User-Agent", Net.BROWSER_UA).build()).execute().use { it.request.url.toString() }
        }.getOrNull()
    }

    // ------------------------------------------------------------------ WebView

    private class Bridge(val onDone: (String) -> Unit) {
        @JavascriptInterface
        fun done(payload: String) = onDone(payload)
    }

    /** يحمّل صفحة المنشور في WebView غير مرئي وينفّذ طلبات البيانات من داخلها. يعيد JSON فيه ما وُجد. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchInWebView(context: Context, code: String, pk: String, loggedIn: Boolean): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                var web: WebView? = null
                var finished = false
                fun finish(value: String?) = main.post {
                    if (finished) return@post
                    finished = true
                    web?.let { w -> runCatching { w.stopLoading(); w.destroy() } }
                    web = null
                    if (cont.isActive) cont.resume(value)
                }
                val view = try {
                    WebView(context)
                } catch (t: Throwable) {
                    lastDiagnosis = "انستجرام: WebView غير متاح على هذا الجهاز"
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                web = view
                val base = baseOverride ?: HOST
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true
                    mediaPlaybackRequiresUserGesture = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // بدون حساب نظهر كمتصفح كروم على الكمبيوتر (نفس إصدار المحرك الفعلي)؛
                    // مع حساب نُبقي هوية المتصفح التي سُجّل الدخول بها حتى لا تُرفض الجلسة.
                    if (!loggedIn) {
                        val chrome = Regex("""Chrome/([\d.]+)""").find(userAgentString.orEmpty())?.groupValues?.get(1) ?: "131.0.0.0"
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
                    }
                }
                view.addJavascriptInterface(Bridge { finish(it) }, "DLBridge")
                var injected = false
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView, pageUrl: String?) {
                        if (injected || finished) return
                        // لا ننفّذ شيئاً خارج نطاق انستجرام (أو خادم الاختبار).
                        if (pageUrl == null || !pageUrl.startsWith(base)) return
                        injected = true
                        v.evaluateJavascript(script(pk, loggedIn), null)
                    }

                    // تعطّل محرك المتصفح لا يجب أن يُسقط التطبيق: نُنهي المحاولة ويُكمل المسار البديل.
                    override fun onRenderProcessGone(v: WebView, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        lastDiagnosis = "انستجرام: تعطّل محرك المتصفح"
                        finish(null)
                        return true
                    }

                    override fun onReceivedError(v: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame && !injected) {
                            lastDiagnosis = "انستجرام: تعذّر فتح الصفحة (${error.description})"
                            finish(null)
                        }
                    }
                }
                cont.invokeOnCancellation { finish(null) }
                view.loadUrl("$base/p/$code/")
            }
        }

    /** ما يُنفَّذ داخل الصفحة: (1) بيانات المنشور المضمّنة فيها، (2) مع حساب: واجهة معلومات المنشور، (3) بلا حساب: استعلام الزائر. */
    private fun script(pk: String, loggedIn: Boolean): String = """
        (async () => {
          const out = { page: location.pathname };
          try {
            const sjs = [];
            document.querySelectorAll('script[data-sjs]').forEach((s) => { const t = s.textContent || ''; if (t.indexOf('xig_polaris_media') >= 0) sjs.push(t); });
            if (sjs.length) out.sjs = sjs;
            let lsd = null;
            try { lsd = JSON.parse(document.getElementById('__eqmc').textContent).l || null; } catch (e) {}
            if (!lsd) { const m = document.documentElement.innerHTML.match(/\["LSD",\[\],\{"token":"([^"]+)"/); if (m) lsd = m[1]; }
            out.hasLsd = !!lsd;
            const H = { 'X-IG-App-ID': '$APP_ID', 'X-ASBD-ID': '359341', 'X-IG-WWW-Claim': '0', 'Accept': '*/*' };
            if ($loggedIn) {
              try {
                const r = await fetch('/api/v1/media/$pk/info/', { headers: H, credentials: 'include' });
                out.infoStatus = r.status;
                if (r.ok) out.info = await r.text();
              } catch (e) { out.infoError = String(e); }
            }
            if (!out.info && !out.sjs) {
              try {
                const r = await fetch('/api/v1/web/get_ruling_for_content/?content_type=MEDIA&target_id=$pk', { headers: H, credentials: 'include' });
                out.rulingStatus = r.status;
                out.ruling = (await r.text()).slice(0, 2000);
              } catch (e) {}
              const csrf = (document.cookie.match(/csrftoken=([^;]+)/) || [])[1];
              const body = new URLSearchParams({ lsd: lsd || '', fb_api_caller_class: 'RelayModern', fb_api_req_friendly_name: '$QUERY_NAME',
                server_timestamps: 'true', variables: JSON.stringify({ media_id: '$pk' }), doc_id: '$DOC_ID' });
              const h = Object.assign({}, H, { 'X-FB-Friendly-Name': '$QUERY_NAME', 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/x-www-form-urlencoded' });
              if (csrf) h['X-CSRFToken'] = csrf;
              if (lsd) h['X-FB-LSD'] = lsd;
              const r = await fetch('/api/graphql', { method: 'POST', headers: h, body: body.toString(), credentials: 'include' });
              out.gqlStatus = r.status;
              out.gql = await r.text();
            }
          } catch (e) { out.error = String(e); }
          DLBridge.done(JSON.stringify(out));
        })();
    """.trimIndent()

    // ------------------------------------------------------------------ قراءة الرد

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

    private fun parseJson(text: String?): JsonElement? {
        val t = text?.trim()?.removePrefix("for (;;);")?.trim()?.takeIf { it.startsWith("{") || it.startsWith("[") } ?: return null
        return runCatching { json.parseToJsonElement(t) }.getOrNull()
    }

    /** يبحث في أي عمق عن أول كائن تحت المفتاح [key]. */
    private fun find(el: JsonElement?, key: String, depth: Int = 0): JsonObject? {
        if (el == null || depth > 40) return null
        when (el) {
            is JsonObject -> {
                el[key].obj()?.let { return it }
                for (v in el.values) find(v, key, depth + 1)?.let { return it }
            }
            is JsonArray -> for (v in el) find(v, key, depth + 1)?.let { return it }
            else -> Unit
        }
        return null
    }

    /** يحوّل ما جمعته الصفحة إلى [MediaInfo]. مكشوف للاختبار. */
    fun parse(raw: String, sourceUrl: String, code: String): MediaInfo? {
        val out = parseJson(raw).obj() ?: return null
        // 1) واجهة معلومات المنشور (مع حساب)
        var product: JsonObject? = (parseJson(out.str("info")).obj()?.get("items") as? JsonArray)?.firstOrNull().obj()
        // 2) استعلام الزائر
        val gql = parseJson(out.str("gql"))
        if (product == null) product = find(gql, "if_not_gated_logged_out")
        // 3) البيانات المضمّنة في الصفحة
        if (product == null) {
            for (s in (out["sjs"] as? JsonArray).orEmpty()) {
                product = find(parseJson((s as? JsonPrimitive)?.contentOrNull), "if_not_gated_logged_out")
                if (product != null) break
            }
        }
        if (product == null) {
            val ruling = parseJson(out.str("ruling")).obj()
            val reason = listOfNotNull(ruling?.str("title"), ruling?.str("description")).joinToString(": ")
            val loginWall = out.str("page").orEmpty().contains("/accounts/login") || reason.contains("Restricted", true) ||
                code.length > 28 || out.int("infoStatus") == 401 || out.int("infoStatus") == 403
            lastDiagnosis = "انستجرام: لا بيانات (page=${out.str("page")}, lsd=${out["hasLsd"]}, gql=${out.int("gqlStatus")}, " +
                "info=${out.int("infoStatus")}, ruling=${out.int("rulingStatus")}${if (reason.isNotEmpty()) ", $reason" else ""}" +
                "${out.str("error")?.let { ", $it" } ?: ""})"
            if (loginWall) throw AppError(ErrorType.PRIVATE, reason.ifEmpty { null })
            return null
        }
        val user = product["user"].obj()?.str("username") ?: product["owner"].obj()?.str("username")
        val caption = product["caption"].obj()?.str("text")?.trim().orEmpty()
        val carousel = (product["carousel_media"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
        val parts = if (carousel.isNotEmpty()) carousel else listOf(product)
        val base = listOfNotNull(user, code).joinToString("-")
        val files = parts.mapIndexedNotNull { i, m -> fileOf(m, if (parts.size > 1) "$base-${i + 1}" else base) }
        if (files.isEmpty()) {
            lastDiagnosis = "انستجرام: المنشور بلا ملفات قابلة للتحميل"
            return null
        }
        val title = caption.lineSequence().firstOrNull { it.isNotBlank() }?.take(90)
            ?: if (files.first().kind == MediaKind.VIDEO) "ريل ${user ?: code}" else "منشور ${user ?: code}"
        return MediaInfo(
            sourceUrl = sourceUrl,
            title = title,
            uploader = user,
            thumbnail = bestImage(parts.first()),
            durationSec = (product["video_duration"] as? JsonPrimitive)?.doubleOrNull?.toLong(),
            platform = "Instagram",
            hasAudio = false,
            directFiles = files,
            mediaKey = "Instagram:$code",
        )
    }

    private fun bestOf(list: JsonArray?): JsonObject? = list.orEmpty().mapNotNull { it.obj() }
        .filter { !it.str("url").isNullOrBlank() }
        .maxByOrNull { (it.int("width") ?: 0).toLong() * (it.int("height") ?: 0) }

    private fun bestImage(m: JsonObject): String? = bestOf(m["image_versions2"].obj()?.get("candidates") as? JsonArray)?.str("url")

    private fun fileOf(m: JsonObject, name: String): DirectFile? {
        bestOf(m["video_versions"] as? JsonArray)?.str("url")?.let { return DirectFile(it, "$name.mp4", "video/mp4", null, MediaKind.VIDEO) }
        bestImage(m)?.let { url ->
            val ext = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "jpg").lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic") } ?: "jpg"
            return DirectFile(url, "$name.$ext", if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/$ext", null, MediaKind.IMAGE)
        }
        return null
    }

    /** يسأل الخادم عن حجم كل ملف (طلب HEAD) ليظهر للمستخدم قبل التحميل. الفشل لا يهم. */
    private suspend fun withSizes(info: MediaInfo): MediaInfo = withContext(Dispatchers.IO) {
        info.copy(directFiles = info.directFiles.map { f ->
            val size = runCatching {
                Net.client.newCall(Request.Builder().url(f.url).head().header("User-Agent", Net.BROWSER_UA).build()).execute()
                    .use { r -> r.header("Content-Length")?.toLongOrNull()?.takeIf { r.isSuccessful && it > 0 } }
            }.getOrNull()
            f.copy(sizeBytes = size)
        })
    }
}
