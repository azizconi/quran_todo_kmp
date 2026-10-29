package tj.app.quran_todo.ui.hifz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.hifzCopy

class HifzCopyTest {
    private fun HifzCopy.strings(): Map<String, String> =
        HifzCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = hifzCopy(language).strings()
            assertTrue(strings.size >= 30, "copy of $language looks incomplete")
            strings.forEach { (name, value) -> assertTrue(value.isNotBlank(), "$language.$name is blank") }
        }
    }

    @Test
    fun theOtherLanguagesAreTranslated() {
        val english = hifzCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = hifzCopy(language).strings()
            val same = english.filter { (name, value) -> other[name] == value }
            assertTrue(same.size < 4, "$language shares too much with English: ${same.keys}")
        }
    }

    @Test
    fun theGradesFollowTheGlossary() {
        val expected = mapOf(
            AppLanguage.RU to listOf("Забыл", "Трудно", "Хорошо", "Легко"),
            AppLanguage.EN to listOf("Forgot", "Hard", "Good", "Easy"),
            AppLanguage.TG to listOf("Фаромӯш", "Душвор", "Хуб", "Осон"),
            AppLanguage.UZ to listOf("Unutdim", "Qiyin", "Yaxshi", "Oson"),
            AppLanguage.TR to listOf("Unuttum", "Zor", "İyi", "Kolay"),
        )
        val order = listOf(ReviewGrade.FORGOT, ReviewGrade.HARD, ReviewGrade.GOOD, ReviewGrade.EASY)
        expected.forEach { (language, words) ->
            val copy = hifzCopy(language)
            assertEquals(words, order.map { copy.label(it) }, "$language grades")
        }
    }

    @Test
    fun theModeIsCalledCheckYourselfAndRevealFollowsTheGlossary() {
        assertEquals("Проверь себя", hifzCopy(AppLanguage.RU).modeName)
        assertEquals("Check yourself", hifzCopy(AppLanguage.EN).modeName)
        assertEquals("Худро санҷед", hifzCopy(AppLanguage.TG).modeName)
        assertEquals("O‘zingizni tekshiring", hifzCopy(AppLanguage.UZ).modeName)
        assertEquals("Kendini sına", hifzCopy(AppLanguage.TR).modeName)
        assertEquals("Показать", hifzCopy(AppLanguage.RU).reveal)
        assertEquals("Reveal", hifzCopy(AppLanguage.EN).reveal)
        assertEquals("Нишон додан", hifzCopy(AppLanguage.TG).reveal)
        assertEquals("Ko‘rsatish", hifzCopy(AppLanguage.UZ).reveal)
        assertEquals("Göster", hifzCopy(AppLanguage.TR).reveal)
    }

    @Test
    fun theTwoAnswersAreRememberedAndHard() {
        assertEquals("Помню", hifzCopy(AppLanguage.RU).label(AyahMark.REMEMBERED))
        assertEquals("Трудно", hifzCopy(AppLanguage.RU).label(AyahMark.HARD))
        assertEquals("I remember", hifzCopy(AppLanguage.EN).label(AyahMark.REMEMBERED))
        assertEquals("Hard", hifzCopy(AppLanguage.EN).label(AyahMark.HARD))
    }

    @Test
    fun placeholdersAreFilledInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = hifzCopy(language)
            val body = copy.exitBody(4, 10)
            assertTrue("4" in body && "10" in body, "$language exit body: $body")
            val answered = copy.answered(AyahMark.HARD)
            assertTrue(copy.hardMark in answered, "$language answered: $answered")
            listOf(body, answered).forEach { assertTrue('{' !in it && '}' !in it, "$language leaves a placeholder: $it") }
        }
    }
}
