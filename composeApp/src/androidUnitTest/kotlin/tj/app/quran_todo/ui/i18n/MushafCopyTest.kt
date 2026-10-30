package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.quran.Sajda
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MushafCopyTest {
    private fun MushafCopy.strings(): Map<String, String> =
        MushafCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = mushafCopy(language).strings()
            assertTrue(strings.size >= 14, "copy of $language looks incomplete")
            strings.forEach { (name, value) -> assertTrue(value.isNotBlank(), "$language.$name is blank") }
        }
    }

    @Test
    fun theLanguagesDifferFromEnglish() {
        val english = mushafCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = mushafCopy(language).strings()
            val same = english.filter { (name, value) -> other[name] == value }
            // The quarter signs and a word or two ("Juz", "Hizb") are the same in several languages; the rest is translated.
            assertTrue(same.size <= 4, "$language shares too much with English: ${same.keys}")
        }
    }

    @Test
    fun everyPatternTakesTheNumberAndLeavesNoPlaceholder() {
        AppLanguage.entries.forEach { language ->
            val copy = mushafCopy(language)
            listOf(copy.juzPattern, copy.hizbPattern, copy.hizbQuarterPattern, copy.hizbHalfPattern, copy.hizbThreeQuartersPattern, copy.pagePattern)
                .forEach { assertTrue("{n}" in it, "$language pattern without a number: $it") }
            val texts = listOf(copy.juzLabel(12), copy.hizbLabel(10), copy.hizbLabel(11), copy.hizbLabel(12), copy.hizbLabel(13), copy.pageLabel(604))
            texts.forEach { assertFalse('{' in it || '}' in it, "$language leaves a placeholder: $it") }
            assertTrue("12" in texts[0], "$language juz")
            assertTrue("604" in texts[5], "$language page")
        }
    }

    @Test
    fun theQuartersOfAHizbReadWithTheirFraction() {
        AppLanguage.entries.forEach { language ->
            val copy = mushafCopy(language)
            assertTrue(copy.hizbLabel(10).startsWith("¼"), "$language: ${copy.hizbLabel(10)}")
            assertTrue(copy.hizbLabel(11).startsWith("½"), "$language: ${copy.hizbLabel(11)}")
            assertTrue(copy.hizbLabel(12).startsWith("¾"), "$language: ${copy.hizbLabel(12)}")
            assertFalse(copy.hizbLabel(9).any { it in "¼½¾" }, "$language: ${copy.hizbLabel(9)}")
            assertTrue("3" in copy.hizbLabel(9) && "3" in copy.hizbLabel(12))
        }
    }

    @Test
    fun theLineOfTheTopBarFollowsTheWordOrderOfTheLanguage() {
        val place = tj.app.quran_todo.ui.reader.MushafPlace(juz = 2, hizb = 3, page = 22)
        assertEquals("Juz 2 · Hizb 3 · p. 22", mushafCopy(AppLanguage.EN).placeText(place))
        assertEquals("Джуз 2 · Хизб 3 · стр. 22", mushafCopy(AppLanguage.RU).placeText(place))
        assertEquals("2. Cüz · 3. Hizb · s. 22", mushafCopy(AppLanguage.TR).placeText(place))
        assertEquals("2-juz · 3-hizb · 22-bet", mushafCopy(AppLanguage.UZ).placeText(place))
    }

    @Test
    fun theSajdaSignNamesItsKind() {
        val obligatory = Sajda(id = 10, recommended = false, obligatory = true)
        val recommended = Sajda(id = 1, recommended = true, obligatory = false)
        AppLanguage.entries.forEach { language ->
            val copy = mushafCopy(language)
            assertTrue(copy.sajdaLabel(obligatory).contains(copy.sajdaObligatory))
            assertTrue(copy.sajdaLabel(recommended).contains(copy.sajdaRecommended))
            assertTrue(copy.sajdaLabel(recommended).startsWith(copy.sajda))
            assertTrue(copy.sajdaObligatory != copy.sajdaRecommended)
        }
    }
}
