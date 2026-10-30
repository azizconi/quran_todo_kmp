package tj.app.quran_todo.ui.reminder

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.ui.state.LastReadPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The reminder of the lesson (task B4) looks at the lesson of the day. Listening to the old ("Listen to the old", F1b) is not a
 * repetition, so it must neither silence the reminder ("the lesson is done") nor change what it says.
 */
class ListenReminderTest {
    private val today = 1_000
    private val m = Fx.flat
    private val plan = Fx.plan(listOf(114, 113), daily = 5, started = today - 10)
    private val recent = Fx.passage(1, 2, 1, 5, PassageStage.R3, due = today, last = today - 3)
    private val old = Fx.passage(2, 3, 1, 5, PassageStage.OLD, last = null)
    private val passages = listOf(recent, old)
    private val lastRead = LastReadPosition(surah = 2, ayahInSurah = 10, legacyGlobalAyah = null)

    private fun lesson(log: List<ReviewLogEntry>): LessonPlan = LessonBuilder.build(LessonInput(today, plan, passages, log, m))

    private fun notice(lesson: LessonPlan) = ReminderNotices.build(AppLanguage.EN, lesson, lastRead) { "S$it" }

    private val listened = passages.map { Fx.logOf(it, ReviewKind.LISTEN, ReviewGrade.GOOD, day = today) }

    @Test
    fun listeningDoesNotMakeTheLessonDoneSoTheReminderStillComes() {
        val without = notice(lesson(emptyList()))
        assertNotNull(without)
        assertEquals(without, notice(lesson(listened)))
    }

    @Test
    fun listeningDoesNotChangeTheWordsOfAReminderOfAPartlyDoneLesson() {
        val doneNew = Fx.log(null, 114, 1, 5, ReviewKind.NEW, day = today)
        val without = notice(lesson(listOf(doneNew)))
        assertNotNull(without)
        assertEquals(without, notice(lesson(listOf(doneNew) + listened)))
    }

    @Test
    fun aLessonDoneByRealRepetitionsStaysQuietWithOrWithoutListening() {
        val done = listOf(
            Fx.log(null, 114, 1, 5, ReviewKind.NEW, day = today),
            Fx.logOf(recent, ReviewKind.RECENT, ReviewGrade.GOOD, day = today),
            Fx.logOf(old, ReviewKind.OLD, ReviewGrade.GOOD, day = today),
        )
        assertNull(notice(lesson(done)))
        assertNull(notice(lesson(done + listened)))
    }
}
