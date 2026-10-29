package tj.app.quran_todo.data

import tj.app.quran_todo.data.translation.FailureThrottle
import tj.app.quran_todo.data.translation.TranslationText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationTextTest {
    @Test
    fun footnoteMarkersOfQuranComAreRemovedWithTheirNumber() {
        // Real answers of GET /api/v4/quran/translations/55?chapter_number=1
        assertEquals(
            "Mehribon va rahmli Allohning nomi bilan boshlayman.",
            TranslationText.cleanQuranCom("Mehribon va rahmli Allohning nomi bilan boshlayman.<sup foot_note=\"170564\">1</sup>"),
        )
        assertEquals(
            "Hamd olamlarning Robbi — Allohgadir.",
            TranslationText.cleanQuranCom("Hamd olamlarning Robbi — Allohgadir.<sup foot_note=\"170569\">1</sup>"),
        )
    }

    @Test
    fun footnotesInTheMiddleAndSeveralOfThemAreRemoved() {
        assertEquals(
            "Allohdan qo‘rqinglar va rost so‘zlovchi bo‘linglar",
            TranslationText.cleanQuranCom(
                "Allohdan<sup foot_note=\"1\">1</sup> qo‘rqinglar va rost <sup foot_note=2>12</sup>so‘zlovchi bo‘linglar<sup foot_note=\"3\">3</sup>",
            ),
        )
        assertEquals("a b", TranslationText.cleanQuranCom("a <SUP FOOT_NOTE=\"1\">\n1\n</SUP> b"))
    }

    @Test
    fun otherTagsAndEntitiesAreCleanedToo() {
        assertEquals("bir ikki", TranslationText.cleanQuranCom("<b>bir</b><br/>ikki"))
        assertEquals("Tom & Jerry \"x\" <y>", TranslationText.cleanQuranCom("Tom &amp; Jerry &quot;x&quot; &lt;y&gt;"))
        assertEquals("a b", TranslationText.cleanQuranCom("a&nbsp;b"))
        assertEquals("’", TranslationText.cleanQuranCom("&#8217;"))
        assertEquals("’", TranslationText.cleanQuranCom("&#x2019;"))
        // unknown entities stay as they are
        assertEquals("&foo;", TranslationText.cleanQuranCom("&foo;"))
    }

    @Test
    fun plainTextOnlyLosesRedundantSpaces() {
        assertEquals("Faqat Sengagina ibodat qilamiz.", TranslationText.cleanQuranCom("  Faqat   Sengagina ibodat\n qilamiz. "))
        // The Uzbek source writes its apostrophes as a backtick; the text is left as the translator wrote it.
        assertEquals("so`raymiz to`g`ri", TranslationText.cleanQuranCom("so`raymiz to`g`ri"))
        assertEquals("", TranslationText.cleanQuranCom("<sup foot_note=\"1\">1</sup>"))
    }

    @Test
    fun cyrillicIsRecognised() {
        assertTrue(TranslationText.containsCyrillic("Меҳрибон ва раҳмли Аллоҳнинг номи билан бошлайман."))
        assertTrue(TranslationText.containsCyrillic("Latin and one Ж letter"))
        assertFalse(TranslationText.containsCyrillic("Mehribon va rahmli Allohning nomi bilan boshlayman."))
        assertFalse(TranslationText.containsCyrillic("بِسْمِ ٱللَّهِ"))
        assertFalse(TranslationText.containsCyrillic(""))
    }
}

class FailureThrottleTest {
    private var now = 1_000_000L
    private val throttle = FailureThrottle(windowMs = 30_000, clock = { now })

    @Test
    fun aFailedKeyIsBlockedForThirtySecondsOnly() = runBlocking {
        assertFalse(throttle.isBlocked("112:uz"))
        throttle.markFailed("112:uz")
        assertTrue(throttle.isBlocked("112:uz"))
        now += 29_999
        assertTrue(throttle.isBlocked("112:uz"), "29.999 s after the failure")
        now += 1
        assertFalse(throttle.isBlocked("112:uz"), "30 s after the failure")
    }

    @Test
    fun otherSurahsAndLanguagesAreNotAffected() = runBlocking {
        throttle.markFailed("112:uz")
        assertFalse(throttle.isBlocked("113:uz"))
        assertFalse(throttle.isBlocked("112:en"))
    }

    @Test
    fun aSuccessClearsTheMemoryAndANewFailureStartsANewWindow() = runBlocking {
        throttle.markFailed("1:en")
        throttle.clear("1:en")
        assertFalse(throttle.isBlocked("1:en"))

        throttle.markFailed("1:en")
        now += 20_000
        throttle.markFailed("1:en")
        now += 20_000
        assertTrue(throttle.isBlocked("1:en"), "20 s after the second failure")
        now += 10_000
        assertFalse(throttle.isBlocked("1:en"))
    }

    @Test
    fun aClockThatWentBackwardsDoesNotBlockForever() = runBlocking {
        throttle.markFailed("1:en")
        now -= 60_000
        assertFalse(throttle.isBlocked("1:en"))
    }
}
