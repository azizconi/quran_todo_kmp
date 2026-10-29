package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeCopyTest {
    private val stringFields = HomeCopy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }

    @Test
    fun everyLanguageFillsEveryString() {
        assertTrue(stringFields.size > 50, "the copy should expose its strings as fields")
        for (language in AppLanguage.entries) {
            val copy = homeCopy(language)
            assertEquals(language, copy.language)
            for (field in stringFields) {
                val value = field.get(copy) as String
                assertTrue(value.isNotBlank(), "${language.name}.${field.name} is blank")
            }
        }
    }

    @Test
    fun otherLanguagesAreTranslatedNotCopiedFromEnglish() {
        val english = homeCopy(AppLanguage.EN)
        for (language in listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR)) {
            val copy = homeCopy(language)
            val untranslated = stringFields
                .filter { field -> field.get(copy) == field.get(english) }
                .map { it.name }
            // Words that are the same in several languages are fine; a wholesale copy is not.
            assertTrue(untranslated.size <= 6, "${language.name} repeats English in $untranslated")
        }
    }

    @Test
    fun placeholdersAreFilledInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val copy = homeCopy(language)
            val texts = listOf(
                copy.ayahOf(255, 286),
                copy.ofGoal(5),
                copy.todayProgress(3, 5),
                copy.smartTitle("Al-Baqarah", 255),
                copy.playerSub(12, 1.25f),
                copy.learnedToast("Al-Fatihah"),
                copy.progressDescription(3),
            )
            texts.forEach { text -> assertFalse(text.contains('{') || text.contains('}'), "${language.name}: $text") }
            assertTrue(copy.ayahOf(255, 286).contains("255") && copy.ayahOf(255, 286).contains("286"))
            assertTrue(copy.smartTitle("Al-Baqarah", 255).contains("Al-Baqarah"))
            assertTrue(copy.learnedToast("Al-Fatihah").contains("Al-Fatihah"))
        }
    }

    @Test
    fun speedIsShownInTheShortestExactForm() {
        val copy = homeCopy(AppLanguage.EN)
        assertEquals("Ayah 12 · 1×", copy.playerSub(12, 1f))
        assertEquals("Ayah 12 · 1.25×", copy.playerSub(12, 1.25f))
        assertEquals("Ayah 12 · 0.75×", copy.playerSub(12, 0.75f))
        assertEquals("Ayah 12 · 2×", copy.playerSub(12, 2f))
        assertEquals("Ayah 12 · 1.5×", copy.playerSub(12, 1.5f))
    }

    @Test
    fun russianAyahCountsFollowTheThreeForms() {
        val expected = mapOf(
            0 to "0 аятов", 1 to "1 аят", 2 to "2 аята", 4 to "4 аята", 5 to "5 аятов", 11 to "11 аятов",
            12 to "12 аятов", 14 to "14 аятов", 21 to "21 аят", 22 to "22 аята", 25 to "25 аятов",
            101 to "101 аят", 111 to "111 аятов", 112 to "112 аятов", 286 to "286 аятов",
        )
        expected.forEach { (n, text) -> assertEquals(text, ayahCountLabel(n, AppLanguage.RU), "n=$n") }
    }

    @Test
    fun russianSurahCountsFollowTheThreeForms() {
        assertEquals("1 сура", surahCountLabel(1, AppLanguage.RU))
        assertEquals("3 суры", surahCountLabel(3, AppLanguage.RU))
        assertEquals("5 сур", surahCountLabel(5, AppLanguage.RU))
        assertEquals("12 сур", surahCountLabel(12, AppLanguage.RU))
        assertEquals("22 суры", surahCountLabel(22, AppLanguage.RU))
    }

    @Test
    fun englishCountsHaveASingularAndAPlural() {
        assertEquals("1 ayah", ayahCountLabel(1, AppLanguage.EN))
        assertEquals("2 ayahs", ayahCountLabel(2, AppLanguage.EN))
        assertEquals("1 surah", surahCountLabel(1, AppLanguage.EN))
        assertEquals("114 surahs", surahCountLabel(114, AppLanguage.EN))
    }

    @Test
    fun tajikUzbekAndTurkishDoNotInflectAfterANumber() {
        for (n in listOf(1, 2, 7, 286)) {
            assertEquals("$n оят", ayahCountLabel(n, AppLanguage.TG))
            assertEquals("$n oyat", ayahCountLabel(n, AppLanguage.UZ))
            assertEquals("$n ayet", ayahCountLabel(n, AppLanguage.TR))
        }
        assertEquals("3 сура", surahCountLabel(3, AppLanguage.TG))
        assertEquals("3 sura", surahCountLabel(3, AppLanguage.UZ))
        assertEquals("3 sure", surahCountLabel(3, AppLanguage.TR))
    }

    @Test
    fun ayahRangesNameASingleAyahOrARange() {
        assertEquals("Ayah 5", ayahRangeLabel(5, 5, AppLanguage.EN))
        assertEquals("Ayahs 1–5", ayahRangeLabel(1, 5, AppLanguage.EN))
        assertEquals("Аят 5", ayahRangeLabel(5, 5, AppLanguage.RU))
        assertEquals("Аяты 1–5", ayahRangeLabel(1, 5, AppLanguage.RU))
        for (language in AppLanguage.entries) {
            assertNotEquals(ayahRangeLabel(5, 5, language), ayahRangeLabel(1, 5, language))
            assertTrue(ayahRangeLabel(3, 9, language).contains("3–9"))
        }
    }

    @Test
    fun theGreetingIsTransliteratedIntoEachScript() {
        assertEquals("Ассаляму алейкум", homeCopy(AppLanguage.RU).greeting)
        assertEquals("Assalamu alaikum", homeCopy(AppLanguage.EN).greeting)
        assertTrue(homeCopy(AppLanguage.TG).greeting.startsWith("Ассалому"))
        assertTrue(homeCopy(AppLanguage.UZ).greeting.startsWith("Assalomu"))
        assertTrue(homeCopy(AppLanguage.TR).greeting.startsWith("Selam"))
    }
}
