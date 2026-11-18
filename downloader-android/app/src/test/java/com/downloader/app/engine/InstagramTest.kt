package com.downloader.app.engine

import com.downloader.app.data.AppError
import com.downloader.app.data.ErrorType
import com.downloader.app.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** الملفات في resources هي مخرجات السكربت الفعلي منفَّذاً في كروم ضد خادم يحاكي ردود انستجرام. */
class InstagramTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun shortcodes() {
        assertEquals("DReelTest01", Instagram.shortcodeOf("https://www.instagram.com/reel/DReelTest01/?igsh=abc"))
        assertEquals("Cabc_-9", Instagram.shortcodeOf("https://instagram.com/user.name/p/Cabc_-9/"))
        assertEquals("XyZ", Instagram.shortcodeOf("https://www.instagram.com/reels/XyZ"))
        assertEquals("T1", Instagram.shortcodeOf("https://www.instagram.com/tv/T1/"))
        assertNull(Instagram.shortcodeOf("https://www.instagram.com/someuser/"))
        assertNull(Instagram.shortcodeOf("https://www.instagram.com/stories/user/123/"))
        // قيمة معروفة: «B» = 1، و«BA» = 64
        assertEquals("1", Instagram.mediaPk("B"))
        assertEquals("64", Instagram.mediaPk("BA"))
        assertEquals("3773588042715159861", Instagram.mediaPk("DReelTest01"))
    }

    @Test fun reel() {
        val info = Instagram.parse(res("igout_DReelTest01.json"), "https://www.instagram.com/reel/DReelTest01/", "DReelTest01")!!
        assertEquals("رحلة إلى الجبال", info.title)
        assertEquals("travel.ar", info.uploader)
        assertEquals(15L, info.durationSec)
        assertEquals("Instagram:DReelTest01", info.mediaKey)
        assertEquals(1, info.directFiles.size)
        val f = info.directFiles.first()
        assertEquals(MediaKind.VIDEO, f.kind)
        assertTrue("must pick the highest resolution", f.url.endsWith("q=hd"))
        assertEquals("travel.ar-DReelTest01.mp4", f.fileName)
        assertTrue(info.thumbnail!!.contains("photo.jpg"))
    }

    @Test fun carousel() {
        val info = Instagram.parse(res("igout_DCarousel02.json"), "https://www.instagram.com/p/DCarousel02/", "DCarousel02")!!
        assertEquals(listOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.IMAGE), info.directFiles.map { it.kind })
        assertEquals(listOf("photos.ar-DCarousel02-1.jpg", "photos.ar-DCarousel02-2.mp4", "photos.ar-DCarousel02-3.jpg"), info.directFiles.map { it.fileName })
        assertEquals("منشور photos.ar", info.title)
    }

    @Test fun restrictedNeedsLogin() {
        try {
            Instagram.parse(res("igout_DNoSuch0003.json"), "https://www.instagram.com/reel/DNoSuch0003/", "DNoSuch0003")
            fail("expected PRIVATE")
        } catch (e: AppError) {
            assertEquals(ErrorType.PRIVATE, e.type)
        }
    }

    @Test fun emptyOrBrokenAnswers() {
        assertNull(Instagram.parse("""{"page":"/p/X/","hasLsd":false,"gqlStatus":200,"gql":"{\"data\":{\"xig_polaris_media\":null}}"}""", "u", "X"))
        assertNull(Instagram.parse("not json", "u", "X"))
        assertNull(Instagram.parse("""{"page":"/p/X/","gqlStatus":500,"gql":"<html>error</html>"}""", "u", "X"))
        // صفحة تسجيل الدخول = تجاوز الحد أو محتوى خاص
        try { Instagram.parse("""{"page":"/accounts/login/","hasLsd":true,"gqlStatus":200,"gql":"{}"}""", "u", "X"); fail("expected PRIVATE") }
        catch (e: AppError) { assertEquals(ErrorType.PRIVATE, e.type) }
        // بيانات مضمّنة في الصفحة (sjs) بلا استعلام
        val sjs = """{"require":[["x",{"__bbox":{"result":{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"user":{"username":"u1"},"video_versions":[{"url":"https://cdn/v.mp4","width":10,"height":10}]}}}}}}]]}"""
        val raw = kotlinx.serialization.json.buildJsonObject { put("page", kotlinx.serialization.json.JsonPrimitive("/p/X/")); put("sjs", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(sjs)) }) }.toString()
        assertEquals("https://cdn/v.mp4", Instagram.parse(raw, "u", "X")!!.directFiles.single().url)
    }
}
