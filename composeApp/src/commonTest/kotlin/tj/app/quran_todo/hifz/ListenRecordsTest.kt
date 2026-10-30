package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.Manzil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Listen to the old" (task F1b) writes `LISTEN` records into the review log. Listening is a help, not a repetition: whatever the
 * log holds of it, the ladder, the circle, "done today", "catching up", the forecast and the minutes of the lesson must be exactly
 * what they are without it. The readers of the log that live outside the pure functions (the streak, the progress screen, the
 * reminder, the report) are covered by their own tests; the SQL filters by `HifzRepositoryTest` on a device.
 */
class ListenRecordsTest {
    private val m = Fx.flat // 100 characters an ayah
    private val today = 1_000

    // A ladder passage due today, an overdue one, a circle of three old passages and a plan with something new to learn.
    private val recentToday = Fx.passage(1, 2, 1, 5, PassageStage.R3, due = today, last = today - 3)
    private val recentLate = Fx.passage(2, 2, 6, 10, PassageStage.R1, due = today - 2, last = today - 3)
    private val old1 = Fx.passage(3, 3, 1, 5, PassageStage.OLD, last = today - 20)
    private val old2 = Fx.passage(4, 3, 6, 10, PassageStage.OLD, last = today - 15)
    private val old3 = Fx.passage(5, 3, 11, 15, PassageStage.OLD, last = null)
    private val passages = listOf(recentToday, recentLate, old1, old2, old3)
    private val plan: HifzPlan = Fx.plan(listOf(114, 113, 112), daily = 5, started = today - 10)

    private fun lesson(log: List<ReviewLogEntry>, passages: List<Passage> = this.passages): LessonPlan =
        LessonBuilder.build(LessonInput(today, plan, passages, log, m))

    private fun listened(passage: Passage, day: Int = today) = Fx.logOf(passage, ReviewKind.LISTEN, day = day)

    private val everythingListened = passages.map { listened(it) }

    @Test
    fun theLessonIsExactlyTheSameWithAndWithoutListening() {
        assertEquals(lesson(emptyList()), lesson(everythingListened))
    }

    @Test
    fun listeningDoesNotMarkAnythingDoneOrCompleteTheLesson() {
        val plan = lesson(everythingListened)
        assertTrue(plan.old.none { it.done })
        assertTrue(plan.old.all { it.grade == null })
        assertTrue(plan.recent.none { it.done })
        assertFalse(plan.isComplete)
        assertTrue(plan.remainingMinutes > 0)
        assertEquals(lesson(emptyList()).remainingMinutes, plan.remainingMinutes)
    }

    @Test
    fun listeningDoesNotChangeWhatIsDoneByRealRepetitions() {
        val real = listOf(
            Fx.logOf(old1, ReviewKind.OLD, ReviewGrade.GOOD, day = today),
            Fx.logOf(recentToday, ReviewKind.RECENT, ReviewGrade.EASY, day = today),
            Fx.log(null, 114, 1, 5, ReviewKind.NEW, day = today),
        )
        val withListening = real + everythingListened + listened(old1)
        val without = lesson(real)
        val with = lesson(withListening)
        assertEquals(without, with)
        // The real repetitions are done; the listening did not add to them or take from them.
        assertEquals(setOf(old1.id), with.old.filter { it.done }.map { it.passageId }.toSet())
        assertEquals(setOf(recentToday.id), with.recent.filter { it.done }.map { it.passageId }.toSet())
    }

    @Test
    fun theCircleOfTheDayIsTheSameWithAndWithoutListening() {
        val without = Manzil.portion(passages, emptyList(), 7, m, today)
        val with = Manzil.portion(passages, everythingListened, 7, m, today)
        assertEquals(without.quota, with.quota)
        assertEquals(without.items.map { Triple(it.passage.id, it.done, it.grade) }, with.items.map { Triple(it.passage.id, it.done, it.grade) })
        // And tomorrow's portion, which the lesson counts as "waiting tomorrow".
        assertEquals(lesson(emptyList()).tomorrow, lesson(everythingListened).tomorrow)
    }

    @Test
    fun aListenedPassageIsStillDueAndStillInTheCircle() {
        val plan = lesson(everythingListened)
        // The late and the due-today passages of the ladder wait for their recall as before.
        assertEquals(setOf(recentToday.id, recentLate.id), plan.recent.map { it.passageId }.toSet())
        // The circle of three: the quota takes the oldest ones, listened or not.
        assertEquals(lesson(emptyList()).old.map { it.passageId }, plan.old.map { it.passageId })
    }

    @Test
    fun catchingUpAndTheDeferredCountDoNotSeeListening() {
        // A backlog: 20 ladder passages of 500 characters do not fit into 25 minutes.
        val backlog = (1L..20L).map { id ->
            Fx.passage(id, 2, 5 * (id.toInt() - 1) + 1, 5 * id.toInt(), PassageStage.R3, due = today - 1, last = today - 4)
        }
        val without = lesson(emptyList(), backlog)
        val with = lesson(backlog.map { listened(it) }, backlog)
        assertTrue(without.catchingUp)
        assertEquals(without.catchingUp, with.catchingUp)
        assertEquals(without.deferredCount, with.deferredCount)
        assertEquals(without, with)
    }

    @Test
    fun listeningOfYesterdayOrAnotherKindOfDayChangesNothingEither() {
        val old = listOf(listened(old1, today - 1), listened(old2, today - 7))
        assertEquals(lesson(emptyList()), lesson(old))
    }

    @Test
    fun theForecastDoesNotSeeListening() {
        val coverage = Coverage(listOf(Fx.passage(1, 114, 1, 3, PassageStage.OLD)), m)
        val news = listOf(Fx.log(null, 114, 1, 3, ReviewKind.NEW, day = today - 2))
        val mixed = news + everythingListened + listened(old1, today - 2)
        val target = plan.copy(targetEpochDay = today + 30)
        assertEquals(Forecast.compute(target, coverage, news, today), Forecast.compute(target, coverage, mixed, today))
        assertEquals(Forecast.compute(plan, coverage, news, today), Forecast.compute(plan, coverage, mixed, today))
    }

    @Test
    fun listeningIsNotAStudyRecordAndTheFormulasOfTheEstimateAreUntouched() {
        assertFalse(ReviewKind.LISTEN.countsAsStudy)
        ReviewKind.entries.filter { it != ReviewKind.LISTEN }.forEach { assertTrue(it.countsAsStudy, it.name) }
        // The estimate of a repetition is the same as before; listening has its own, for the audio only.
        assertEquals(191, HifzConstants.reviewSeconds(500))
        assertEquals(88, HifzConstants.listenSeconds(500)) // 500 / 5.7 = 87.7 s of recitation
        assertEquals(193, HifzConstants.listenSeconds(500, echoRepeats = 1)) // + the silence: 87.7 x 2.2
        assertEquals(386, HifzConstants.listenSeconds(500, echoRepeats = 2))
    }

    @Test
    fun theLessonItemCarriesTheSecondsOfOneRecitationForTheButton() {
        val item = lesson(emptyList()).old.first()
        assertEquals(HifzConstants.listenSeconds(500), item.listenSeconds)
        assertTrue(lesson(emptyList()).old.all { it.listenSeconds > 0 })
    }
}
