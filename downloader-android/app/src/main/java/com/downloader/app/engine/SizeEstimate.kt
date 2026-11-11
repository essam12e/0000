package com.downloader.app.engine

import com.downloader.app.data.MediaInfo
import com.downloader.app.data.PlaylistItem
import com.downloader.app.data.QualityOption

/**
 * تقدير حجم تحميل قائمة تشغيل. التحليل السريع للقائمة لا يجلب صيغ كل فيديو، لذا:
 *   1) فوراً: مجموع مدد الفيديوهات المحددة × معدل بتّ نموذجي لكل جودة («تقديري»).
 *   2) ثم: نحلّل عيّنة صغيرة من فيديوهات القائمة نفسها ونستخدم معدلها الفعلي («تقريبي») —
 *      أدق بكثير لأن المعدل يختلف كثيراً بين محاضرة ثابتة ومقطع حركة.
 */
object SizeEstimate {
    const val AUDIO_KEY = "audio"

    /** معدلات نموذجية ليوتيوب (كيلوبت/ثانية، فيديو + صوت) حسب الضلع الأقصر. */
    private val typicalKbps = listOf(2160 to 14000, 1440 to 7000, 1080 to 3200, 720 to 1700, 480 to 900, 360 to 550, 240 to 350)
    private const val AUDIO_KBPS = 130

    private fun kbpsToBytes(kbps: Int) = kbps * 125.0

    /** معدل نموذجي (بايت/ثانية) لجودة ضلعها الأقصر [side]؛ «الأعلى المتاح» يُعامل كـ 1080p حتى تُعرف العيّنة. */
    fun typicalRate(side: Int): Double {
        val s = if (side > 5000) 1080 else side
        val match = typicalKbps.firstOrNull { s >= it.first * 0.93 } ?: typicalKbps.last()
        return kbpsToBytes(match.second)
    }

    val typicalAudioRate: Double get() = kbpsToBytes(AUDIO_KBPS)

    /**
     * معدلات فعلية (بايت/ثانية) لكل جودة من جودات القائمة [presets]، محسوبة من فيديوهات العيّنة.
     * لكل جودة نأخذ من كل فيديو الخيار الذي سيختاره التحميل فعلاً (أعلى جودة لا تتجاوزها).
     */
    fun ratesFrom(samples: List<MediaInfo>, presets: List<QualityOption>): Map<String, Double> {
        val usable = samples.filter { (it.durationSec ?: 0) > 0 }
        if (usable.isEmpty()) return emptyMap()
        val out = HashMap<String, Double>()
        for (preset in presets) {
            var bytes = 0L
            var seconds = 0L
            for (s in usable) {
                val pick = if (preset.shortSide > 5000) s.videoOptions.firstOrNull()
                else s.videoOptions.firstOrNull { it.shortSide <= preset.shortSide * 1.07 } ?: s.videoOptions.lastOrNull()
                val size = pick?.sizeBytes ?: continue
                bytes += size
                seconds += s.durationSec!!
            }
            if (seconds > 0) out[preset.id] = bytes.toDouble() / seconds
        }
        var audioBytes = 0L
        var audioSeconds = 0L
        for (s in usable) {
            val size = s.audioSizeBytes ?: continue
            audioBytes += size
            audioSeconds += s.durationSec!!
        }
        if (audioSeconds > 0) out[AUDIO_KEY] = audioBytes.toDouble() / audioSeconds
        return out
    }

    /**
     * مجموع مدد العناصر المحددة بالثواني. العناصر مجهولة المدة تُحسب بمتوسط المعروفة.
     * يعيد null إذا لم تُعرف مدة أي عنصر (عندها لا يمكن التقدير).
     */
    fun selectedSeconds(items: List<PlaylistItem>, selected: Set<Int>): Long? {
        val chosen = items.filter { it.index in selected }
        val known = chosen.mapNotNull { it.durationSec?.takeIf { d -> d > 0 } }
        if (known.isEmpty()) return null
        val unknown = chosen.size - known.size
        return known.sum() + (known.average() * unknown).toLong()
    }

    /** عيّنة موزّعة على القائمة: الأول والأوسط والأخير من العناصر المحددة التي لها رابط. */
    fun pickSamples(items: List<PlaylistItem>, selected: Set<Int>, max: Int = 3): List<PlaylistItem> {
        val pool = items.filter { it.index in selected && it.url != null }
        if (pool.size <= max) return pool
        return listOf(pool.first(), pool[pool.size / 2], pool.last()).distinct().take(max)
    }
}
