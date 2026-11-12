package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The strings of the quiz "Check" (task F2b) exist in the five languages, have no star and fill their patterns. */
class QuizCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷')

    private fun strings(copy: QuizCopy): Map<String, String> =
        QuizCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(copy) as String }

    @Test
    fun everyStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(quizCopy(language))
            assertTrue(all.size >= 40, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
                assertFalse('!' in value, "${language.name}.$name has an exclamation mark: $value")
            }
        }
    }

    @Test
    fun theFiveLanguagesDoNotShareTheirTexts() {
        val byLanguage = AppLanguage.entries.associateWith { strings(quizCopy(it)) }
        listOf("continueTitle", "orderTitle", "knew", "didNotKnow", "openInReader", "emptyBody").forEach { key ->
            val values = byLanguage.values.map { it.getValue(key) }
            assertEquals(values.size, values.toSet().size, "$key is the same in two languages: $values")
        }
    }

    @Test
    fun thePatternsFillAllTheirPlaceholders() {
        AppLanguage.entries.forEach { language ->
            val copy = quizCopy(language)
            val filled = listOf(
                copy.entrySubtitle(5), copy.lessonButton(5), copy.progress(2, 5), copy.itIsSurah("Al-Mulk"), copy.orderPlace(2),
                copy.ayah(12), copy.result(4, 5), copy.moreQuestions(5),
            )
            filled.forEach { text ->
                assertFalse("{" in text || "}" in text, "${language.name}: $text")
            }
            assertTrue(copy.progress(2, 5).let { "2" in it && "5" in it }, "${language.name}: ${copy.progress(2, 5)}")
            assertTrue(copy.result(4, 5).let { "4" in it && "5" in it }, "${language.name}: ${copy.result(4, 5)}")
            assertTrue("Al-Mulk" in copy.itIsSurah("Al-Mulk"))
        }
    }

    @Test
    fun theWordsOfTheEntryAndOfTheResultReadAsTheyShould() {
        val ru = quizCopy(AppLanguage.RU)
        assertEquals("5 вопросов по пройденному", ru.entrySubtitle(5))
        assertEquals("Тест · 5 вопросов", ru.lessonButton(5))
        assertEquals("2 из 5", ru.progress(2, 5))
        assertEquals("Знал 4 из 5", ru.result(4, 5))
        assertEquals("Ещё 5 вопросов", ru.moreQuestions(5))
        assertEquals("Знал", ru.knew)
        assertEquals("Не знал", ru.didNotKnow)
        assertEquals("Knew 4 of 5", quizCopy(AppLanguage.EN).result(4, 5))
        assertEquals("Another 5 questions", quizCopy(AppLanguage.EN).moreQuestions(5))
        assertEquals("1 question on what you know", quizCopy(AppLanguage.EN).entrySubtitle(1))
    }

    @Test
    fun questionsAreCountedInTheGrammarOfTheLanguage() {
        assertEquals("1 вопрос", questionCountLabel(1, AppLanguage.RU))
        assertEquals("2 вопроса", questionCountLabel(2, AppLanguage.RU))
        assertEquals("5 вопросов", questionCountLabel(5, AppLanguage.RU))
        assertEquals("11 вопросов", questionCountLabel(11, AppLanguage.RU))
        assertEquals("21 вопрос", questionCountLabel(21, AppLanguage.RU))
        assertEquals("1 question", questionCountLabel(1, AppLanguage.EN))
        assertEquals("5 questions", questionCountLabel(5, AppLanguage.EN))
        assertEquals("5 савол", questionCountLabel(5, AppLanguage.TG))
        assertEquals("5 ta savol", questionCountLabel(5, AppLanguage.UZ))
        assertEquals("5 soru", questionCountLabel(5, AppLanguage.TR))
    }
}
