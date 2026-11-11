package com.downloader.app.engine

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * ترجمة نصوص على الجهاز نفسه (ML Kit). النص لا يغادر الجوال؛ الاتصال الوحيد هو تنزيل نموذج
 * اللغة مرة واحدة (نحو 30 م.ب لكل لغة) ثم يعمل بلا إنترنت. إرسال الإحصاءات معطَّل في AndroidManifest.
 */
object Translator {
    /** هل يدعم المحرك هذه اللغة مصدراً؟ (الرمز قد يحمل لواحق مثل en-orig) */
    fun sourceTag(code: String): String? = TranslateLanguage.fromLanguageTag(code.substringBefore('-').lowercase())

    /**
     * يترجم [lines] من [sourceCode] إلى [targetCode]. يعيد null عند أي فشل (لغة غير مدعومة، تعذّر تنزيل النموذج...).
     * [onStage] يُبلَّغ بالمرحلة الحالية لعرضها للمستخدم.
     */
    fun translate(sourceCode: String, targetCode: String, lines: List<String>, onStage: (String) -> Unit): List<String>? {
        val source = sourceTag(sourceCode) ?: return null
        val target = TranslateLanguage.fromLanguageTag(targetCode.substringBefore('-').lowercase()) ?: return null
        if (source == target) return lines
        val translator = try {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        } catch (t: Throwable) {
            android.util.Log.e("Downloader", "translator init failed", t)
            return null
        }
        return try {
            onStage("تجهيز نموذج الترجمة (يُنزَّل مرة واحدة)")
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()), 180, TimeUnit.SECONDS)
            val out = ArrayList<String>(lines.size)
            lines.forEachIndexed { i, line ->
                if (i % 20 == 0) onStage("ترجمة النص على الجهاز (${i * 100 / lines.size.coerceAtLeast(1)}%)")
                out += if (line.isBlank()) line else Tasks.await(translator.translate(line), 30, TimeUnit.SECONDS)
            }
            out
        } catch (t: Throwable) {
            android.util.Log.e("Downloader", "on-device translation failed", t)
            null
        } finally {
            runCatching { translator.close() }
        }
    }
}
