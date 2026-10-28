package tj.app.quran_todo.ui.reader

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.i18n.readerCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReaderCopyTest {
    private fun ReaderCopy.strings(): Map<String, String> =
        ReaderCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = readerCopy(language).strings()
            assertTrue(strings.size > 50, "copy of $language looks incomplete")
            strings.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "$language.$name is blank")
            }
        }
    }

    @Test
    fun theLanguagesDifferFromEnglish() {
        val english = readerCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = readerCopy(language).strings()
            // A few words are the same everywhere (the reciter's name); most of the copy must be translated.
            val same = english.filter { (name, value) -> other[name] == value }
            assertTrue(same.size < 12, "$language shares too much with English: ${same.keys}")
        }
    }

    @Test
    fun placeholdersSurviveInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = readerCopy(language)
            assertTrue("Al-Fatiha" in copy.learnedToast("Al-Fatiha"), "$language learned toast")
            assertTrue("Al-Mulk" in copy.playingElsewhere("Al-Mulk"), "$language playing elsewhere")
            assertTrue("12" in copy.selectAyah(12), "$language select ayah")
            assertTrue("15" in copy.sleepMinutes(15), "$language sleep minutes")
            assertTrue("12:30" in copy.sleepLeft("12:30"), "$language sleep left")
            assertTrue("3" in copy.reviewIn(3), "$language review in")
            listOf(copy.learnedToast("x"), copy.playingElsewhere("x"), copy.selectAyah(1), copy.sleepMinutes(1), copy.sleepLeft("x"), copy.reviewIn(1))
                .forEach { assertTrue('{' !in it && '}' !in it, "$language leaves a placeholder: $it") }
        }
    }

    @Test
    fun russianAyahCountUsesThePluralForms() {
        val copy = readerCopy(AppLanguage.RU)
        assertEquals("1 аят", copy.ayahCount(1))
        assertEquals("3 аята", copy.ayahCount(3))
        assertEquals("7 аятов", copy.ayahCount(7))
        assertEquals("11 аятов", copy.ayahCount(11))
        assertEquals("21 аят", copy.ayahCount(21))
        assertEquals("22 аята", copy.ayahCount(22))
        assertEquals("286 аятов", copy.ayahCount(286))
    }

    @Test
    fun englishAyahCountSeparatesOneFromMany() {
        val copy = readerCopy(AppLanguage.EN)
        assertEquals("1 ayah", copy.ayahCount(1))
        assertEquals("7 ayahs", copy.ayahCount(7))
    }

    @Test
    fun otherLanguagesDoNotInflectTheCount() {
        assertEquals("286 оят", readerCopy(AppLanguage.TG).ayahCount(286))
        assertEquals("286 oyat", readerCopy(AppLanguage.UZ).ayahCount(286))
        assertEquals("286 ayet", readerCopy(AppLanguage.TR).ayahCount(286))
    }

    @Test
    fun theStatusNamesFollowTheGlossary() {
        assertEquals("Заучиваю", readerCopy(AppLanguage.RU).learning)
        assertEquals("Memorizing", readerCopy(AppLanguage.EN).learning)
        assertEquals("Аз бар мекунам", readerCopy(AppLanguage.TG).learning)
        assertEquals("Yodlayapman", readerCopy(AppLanguage.UZ).learning)
        assertEquals("Ezberliyorum", readerCopy(AppLanguage.TR).learning)
        assertNotEquals(readerCopy(AppLanguage.RU).learning, readerCopy(AppLanguage.RU).learned)
    }
}
