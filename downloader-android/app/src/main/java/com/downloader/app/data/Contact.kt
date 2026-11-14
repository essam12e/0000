package com.downloader.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.downloader.app.BuildConfig

/** بيانات المطوّر وقناة التواصل والملاحظات (واتساب مباشرة — بلا خادم). */
object Contact {
    const val DEVELOPER = "بابلو"

    /** رقم واتساب بالصيغة الدولية بدون + */
    const val WHATSAPP = "966531098278"
    const val WHATSAPP_DISPLAY = "+966 53 109 8278"

    /** آخر خطأ ظهر للمستخدم؛ يُرفق بالملاحظة إن اختار ذلك (يساعد على معرفة سبب فشل رابط). */
    @Volatile
    var lastError: String? = null

    /** معلومات تُلحق بالملاحظة تلقائياً ليعرف المطوّر على أي نسخة وجهاز حدثت. */
    fun signature(engineVersion: String?): String = buildString {
        append("— داونلودر ").append(BuildConfig.VERSION_NAME)
        if (!engineVersion.isNullOrBlank()) append(" · yt-dlp ").append(engineVersion)
        append(" · أندرويد ").append(Build.VERSION.RELEASE)
        append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
    }

    /**
     * يفتح محادثة واتساب مع المطوّر والنص جاهز فيها؛ المستخدم يراجعه ويضغط «إرسال» بنفسه.
     * يعيد false إن لم يوجد واتساب ولا متصفح على الجهاز.
     */
    fun openWhatsApp(context: Context, text: String): Boolean {
        val uri = Uri.parse("https://wa.me/$WHATSAPP").buildUpon().apply {
            if (text.isNotBlank()) appendQueryParameter("text", text)
        }.build()
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
