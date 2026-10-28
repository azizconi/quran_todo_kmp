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

/** The final QA fixes of the home screen (task 3): goal counter, spoken states, large text. */
class FinalQaFixesTest {
    // -- Goal reached ("7 of 5") -----------------------------------------------------------------

    @Test
    fun theGoalIsReachedAtTheGoalAndPastIt() {
        assertFalse(isGoalReached(done = 4, goal = 5))
        assertTrue(isGoalReached(done = 5, goal = 5))
        assertTrue(isGoalReached(done = 7, goal = 5))
        assertFalse(isGoalReached(done = 0, goal = 0))
    }

    @Test
    fun theGoalBurstPlaysOncePerDay() {
        val today = 20_000
        val never = HomeLocalPrefs.NoDay
        // Not reached: never.
        assertFalse(shouldCelebrateGoal(done = 3, goal = 5, celebratedDay = never, today = today))
        // First time reached today (also when passed): yes.
        assertTrue(shouldCelebrateGoal(done = 5, goal = 5, celebratedDay = never, today = today))
        assertTrue(shouldCelebrateGoal(done = 7, goal = 5, celebratedDay = today - 1, today = today))
        // Already played today: no replay, however much more is done.
        assertFalse(shouldCelebrateGoal(done = 5, goal = 5, celebratedDay = today, today = today))
        assertFalse(shouldCelebrateGoal(done = 9, goal = 5, celebratedDay = today, today = today))
        // A new day plays it again.
        assertTrue(shouldCelebrateGoal(done = 5, goal = 5, celebratedDay = today, today = today + 1))
    }

    @Test
    fun theGoalReachedTextNamesTheCountInEveryLanguage() {
        assertEquals("Цель выполнена · 7", homeCopy(AppLanguage.RU).goalReached(7))
        assertEquals("Goal reached · 7", homeCopy(AppLanguage.EN).goalReached(7))
        for (language in AppLanguage.entries) {
            val copy = homeCopy(language)
            val text = copy.goalReached(7)
            val spoken = copy.goalReachedDescription(7, 5)
            assertTrue(text.contains("7"), "${language.name}: $text")
            assertTrue(spoken.contains("7") && spoken.contains("5"), "${language.name}: $spoken")
            assertFalse(text.contains('{') || spoken.contains('{'), "${language.name}: $text / $spoken")
            // It is not the "3 of 5" text: the counter really changes.
            assertNotEquals(copy.todayProgress(7, 5), spoken)
        }
    }

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
