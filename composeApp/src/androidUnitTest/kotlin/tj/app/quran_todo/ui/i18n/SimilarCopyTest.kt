package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimilarCopyTest {
    private fun SimilarCopy.strings(): Map<String, String> =
        SimilarCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = similarCopy(language).strings()
            assertTrue(strings.size >= 20, "copy of $language looks incomplete")
            strings.forEach { (name, value) -> assertTrue(value.isNotBlank(), "$language.$name is blank") }
        }
    }

    @Test
    fun theLanguagesDifferFromEnglish() {
        val english = similarCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = similarCopy(language).strings()
            val same = english.filter { (name, value) -> other[name] == value }
            assertTrue(same.size < 3, "$language shares too much with English: ${same.keys}")
        }
    }

    @Test
    fun placeholdersAreFilledAndNoneIsLeft() {
        AppLanguage.entries.forEach { language ->
            val copy = similarCopy(language)
            val texts = listOf(
                copy.sheetRow(3),
                copy.repeatsInSurah(31, "Ar-Rahman"),
                copy.repeats(3),
                copy.lessonHint("Al-A'raf 19"),
                copy.place(2),
                copy.entry(4),
            )
            texts.forEach { assertFalse('{' in it || '}' in it, "$language leaves a placeholder: $it") }
            assertTrue("3" in copy.sheetRow(3), "$language sheet row")
            assertTrue("Ar-Rahman" in copy.repeatsInSurah(31, "Ar-Rahman") && "31" in copy.repeatsInSurah(31, "Ar-Rahman"), "$language repeats")
            assertTrue("Al-A'raf 19" in copy.lessonHint("Al-A'raf 19"), "$language lesson hint")
            assertTrue("2" in copy.place(2), "$language place")
            assertTrue("4" in copy.entry(4), "$language entry")
        }
    }

    @Test
    fun russianCountsInTheRightForms() {
        val ru = similarCopy(AppLanguage.RU)
        assertEquals("Повторяется 31 раз в суре Ар-Рахман", ru.repeatsInSurah(31, "Ар-Рахман"))
        assertEquals("Повторяется 3 раза", ru.repeats(3))
        assertEquals("Повторяется 10 раз", ru.repeats(10))
        assertEquals("Различите похожие · 1 пара", ru.entry(1))
        assertEquals("Различите похожие · 3 пары", ru.entry(3))
        assertEquals("Различите похожие · 11 пар", ru.entry(11))
        assertEquals("Похож на Аль-Аъраф 19 — сравнить", ru.lessonHint("Аль-Аъраф 19"))
    }

    @Test
    fun noStarAnywhereInTheCopy() {
        AppLanguage.entries.forEach { language ->
            similarCopy(language).strings().forEach { (name, value) ->
                assertFalse(value.any { it == '۞' || it == '★' || it == '☆' || it == '✦' || it == '*' }, "$language.$name has a star")
            }
        }
    }
}
