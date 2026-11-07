package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.home.components.RowTexts
import tj.app.quran_todo.ui.home.components.subtitleMaxLines
import tj.app.quran_todo.ui.i18n.homeCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The final QA fixes of the home screen (task 3): spoken states, large text. */
class FinalQaFixesTest {
    // -- Spoken state of a surah row ---------------------------------------------------------------

    private fun texts(language: AppLanguage) = RowTexts.of(homeCopy(language))

    @Test
    fun aRowSpeaksItsStatusAndTheMemorizedShare() {
        val ru = texts(AppLanguage.RU)
        assertEquals("Не начал", ru.stateOf(null, 0f))
        assertEquals("Заучиваю, выучено 40%", ru.stateOf(SurahTodoStatus.LEARNING, 0.4f))
        assertEquals("Выучено", ru.stateOf(SurahTodoStatus.LEARNED, 1f))

        val en = texts(AppLanguage.EN)
        assertEquals("Not started", en.stateOf(null, 0f))
        assertEquals("Memorizing, 40% memorized", en.stateOf(SurahTodoStatus.LEARNING, 0.4f))
        assertEquals("Memorized", en.stateOf(SurahTodoStatus.LEARNED, 1f))
    }

    @Test
    fun theLearningStateStaysShortWhileNothingIsMemorizedYet() {
        for (language in AppLanguage.entries) {
            val texts = texts(language)
            assertEquals(homeCopy(language).statusLearning, texts.stateOf(SurahTodoStatus.LEARNING, 0f), language.name)
        }
        // A sliver of progress shows as 1%, not as "0%".
        assertTrue(texts(AppLanguage.EN).stateOf(SurahTodoStatus.LEARNING, 0.001f).contains("1%"))
    }

    @Test
    fun theSignComesFirstInTurkishLikeInTheProgressScreen() {
        assertEquals("Ezberliyorum, %40 ezberlendi", texts(AppLanguage.TR).stateOf(SurahTodoStatus.LEARNING, 0.4f))
        for (language in AppLanguage.entries - AppLanguage.TR) {
            val text = texts(language).stateOf(SurahTodoStatus.LEARNING, 0.4f)
            assertTrue(text.contains("40%"), "${language.name}: $text")
        }
    }

    @Test
    fun everyLanguageSpeaksAllThreeStates() {
        for (language in AppLanguage.entries) {
            val texts = texts(language)
            val states = listOf(
                texts.stateOf(null, 0f),
                texts.stateOf(SurahTodoStatus.LEARNING, 0.4f),
                texts.stateOf(SurahTodoStatus.LEARNED, 1f),
            )
            assertEquals(3, states.toSet().size, "${language.name}: $states")
            states.forEach { assertTrue(it.isNotBlank() && !it.contains('{'), "${language.name}: $it") }
        }
    }

    @Test
    fun thePinnedLabelIsNeutralInRussian() {
        assertEquals("Закреплено", homeCopy(AppLanguage.RU).pinned)
    }

    // -- Large text -------------------------------------------------------------------------------

    @Test
    fun theSubtitleTakesTwoLinesOnlyForLargeText() {
        assertEquals(1, subtitleMaxLines(0.85f))
        assertEquals(1, subtitleMaxLines(1f))
        assertEquals(1, subtitleMaxLines(1.15f))
        assertEquals(2, subtitleMaxLines(1.16f))
        assertEquals(2, subtitleMaxLines(1.3f))
        assertEquals(2, subtitleMaxLines(2f))
    }
}
