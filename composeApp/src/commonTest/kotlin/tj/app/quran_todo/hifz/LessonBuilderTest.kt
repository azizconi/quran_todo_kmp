package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.Manzil
import tj.app.quran_todo.hifz.schedule.PassageOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonBuilderTest {
    private val m = Fx.flat // 100 characters an ayah
    private val today = 1_000
    private val plan114 = Fx.plan(listOf(114, 113, 112), daily = 5)

    private fun lesson(plan: HifzPlan?, passages: List<Passage>, log: List<ReviewLogEntry> = emptyList()): LessonPlan =
        LessonBuilder.build(LessonInput(today, plan, passages, log, m))

    /** A ladder passage of 5 ayahs (500 characters) in surah 2: the slot [slot] is ayahs 5*slot+1 .. 5*slot+5. */
    private fun recent(id: Long, due: Int, stage: PassageStage = PassageStage.R3) =
        Fx.passage(id, 2, 5 * (id.toInt() - 1) + 1, 5 * id.toInt(), stage, due = due, last = due - 3)

    private val reviewSeconds500 = HifzConstants.reviewSeconds(500)

    @Test
    fun theEstimatesFollowTheFormula() {
        // 500 characters: 500 / 5.7 = 87.7 s of recitation; a repetition x2 + 15 s, a new passage x7 + 15 s.
        assertEquals(191, reviewSeconds500)
        assertEquals(752, HifzConstants.newSeconds(600))
    }

    // ---- empty / plan ----

    @Test
    fun withoutAPlanAndWithoutPassagesTheLessonIsEmpty() {
        val lesson = lesson(null, emptyList())
        assertFalse(lesson.hasPlan)
        assertFalse(lesson.planFinished)
        assertNull(lesson.newBlock)
        assertTrue(lesson.isEmpty)
        assertFalse(lesson.isComplete)
        assertEquals(0, lesson.totalMinutes)
        assertFalse(lesson.catchingUp)
        assertNull(lesson.tomorrow.newPassage)
    }

    @Test
    fun aPlanOffersTheNextNewPassage() {
        val lesson = lesson(plan114, emptyList())
        val block = assertNotNull(lesson.newBlock)
        assertEquals(LessonNewState.PENDING, block.state)
        assertEquals(Triple(114, 1, 6), Triple(block.surah, block.startAyah, block.endAyah))
        assertEquals(752, block.estimateSeconds)
        assertEquals(13, lesson.totalMinutes)
        assertEquals(13, lesson.remainingMinutes)
        assertFalse(lesson.isComplete)
        assertFalse(lesson.isEmpty)
        assertTrue(lesson.hasPlan)
        assertEquals(Triple(114, 1, 6), lesson.tomorrow.newPassage?.let { Triple(it.surah, it.startAyah, it.endAyah) })
    }

    @Test
    fun theNewBlockIsClosedOnceANewPassageIsLoggedToday() {
        val learned = Fx.passage(1, 114, 1, 6, PassageStage.R1, due = today + 1, last = today, learned = today)
        val log = listOf(Fx.logOf(learned, ReviewKind.NEW, ReviewGrade.GOOD, today))
        val lesson = lesson(plan114, listOf(learned), log)
        val block = assertNotNull(lesson.newBlock)
        assertEquals(LessonNewState.DONE, block.state)
        assertEquals(ReviewGrade.GOOD, block.grade)
        assertEquals(1L, block.passageId)
        assertEquals(Triple(114, 1, 6), Triple(block.surah, block.startAyah, block.endAyah))
        assertTrue(lesson.isComplete, "nothing else is due: the lesson is done")
        assertEquals(0, lesson.remainingMinutes)
        // The next one is for tomorrow, and a repetition of this one is due then.
        assertEquals(Triple(113, 1, 5), lesson.tomorrow.newPassage?.let { Triple(it.surah, it.startAyah, it.endAyah) })
        assertEquals(1, lesson.tomorrow.dueCount)
    }

    @Test
    fun aSecondNewPassageIsNotOfferedTheSameDay() {
        val learned = Fx.passage(1, 114, 1, 3, PassageStage.R1, due = today + 1, learned = today)
        val log = listOf(Fx.logOf(learned, ReviewKind.NEW, ReviewGrade.HARD, today))
        val block = assertNotNull(lesson(plan114, listOf(learned), log).newBlock)
        assertEquals(LessonNewState.DONE, block.state)
        assertEquals(3, block.endAyah)
    }

    @Test
    fun aFinishedPlanHasNoNewBlockAndIsMarkedFinished() {
        val covered = Fx.passage(1, 114, 1, 6, PassageStage.OLD)
        val lesson = lesson(Fx.plan(listOf(114)), listOf(covered))
        assertTrue(lesson.planFinished)
        assertTrue(lesson.hasPlan)
        assertNull(lesson.newBlock)
        assertEquals(1, lesson.old.size, "the manzil goes on")
        assertNull(lesson.tomorrow.newPassage)
    }

    @Test
    fun aPlanFinishedByTodaysPassageKeepsTheDoneBlock() {
        val learned = Fx.passage(1, 114, 1, 6, PassageStage.R1, due = today + 1, learned = today)
        val log = listOf(Fx.logOf(learned, ReviewKind.NEW, ReviewGrade.EASY, today))
        val lesson = lesson(Fx.plan(listOf(114)), listOf(learned), log)
        assertTrue(lesson.planFinished)
        assertEquals(LessonNewState.DONE, lesson.newBlock?.state)
        assertTrue(lesson.isComplete)
    }

    @Test
    fun withoutAPlanTheOldCircleIsStillThere() {
        val old = listOf(Fx.passage(1, 2, 1, 5), Fx.passage(2, 2, 6, 10), Fx.passage(3, 2, 11, 15))
        val lesson = lesson(null, old)
        assertFalse(lesson.hasPlan)
        assertNull(lesson.newBlock)
        // 3 passages of 500, manzilDays defaults to 7: quota 215, one passage.
        assertEquals(1, lesson.old.size)
        assertFalse(lesson.isEmpty)
        assertEquals(1, lesson.tomorrow.dueCount, "tomorrow's turn of the circle")
    }

    @Test
    fun tomorrowCountsTheNextTurnOfTheCircleAfterTodaysIsDone() {
        val old = listOf(Fx.passage(1, 2, 1, 5), Fx.passage(2, 2, 6, 10), Fx.passage(3, 2, 11, 15))
        val outcome = PassageOps.review(old, 1, ReviewGrade.GOOD, emptySet(), today, m)!!
        val log = listOf(Fx.logOf(outcome.before, ReviewKind.OLD, ReviewGrade.GOOD, today))
        val recentDue = recent(4, due = today + 1)
        val lesson = lesson(null, outcome.passages + recentDue, log)

        assertTrue(lesson.isComplete)
        // The ladder passage due tomorrow, and the next passage of the circle (today's one waits its 7 days).
        assertEquals(2, lesson.tomorrow.dueCount)
    }

    // ---- recent ----

    @Test
    fun theRecentBlockListsTheMostOverdueFirstAndOnlyWhatIsDue() {
        val passages = listOf(
            recent(1, due = today - 2),
            recent(2, due = today),
            recent(3, due = today - 5, stage = PassageStage.R7),
            recent(4, due = today + 1),
        )
        val lesson = lesson(null, passages)
        assertEquals(listOf(3L, 1L, 2L), lesson.recent.map { it.passageId })
        assertEquals(listOf(5, 2, 0), lesson.recent.map { it.overdueDays })
        assertTrue(lesson.recent.none { it.done })
        assertEquals(4, lesson.tomorrow.dueCount, "the one due tomorrow counts too")
    }

    @Test
    fun whatWasRepeatedTodayStaysInTheLessonAsDoneEvenWhenForgotMovedItToTomorrow() {
        val passages = listOf(recent(1, due = today), recent(2, due = today - 1))
        val outcome = PassageOps.review(passages, 2, ReviewGrade.FORGOT, setOf(7), today, m)!!
        val log = listOf(Fx.logOf(outcome.before, ReviewKind.RECENT, ReviewGrade.FORGOT, today))
        val lesson = lesson(null, outcome.passages, log)

        assertEquals(listOf(2L, 1L), lesson.recent.map { it.passageId }, "done first, then the ones to do")
        val done = lesson.recent.first()
        assertTrue(done.done)
        assertEquals(ReviewGrade.FORGOT, done.grade)
        assertEquals(PassageStage.R1, done.stage)
        assertEquals(setOf(7), done.hardAyahs)
        assertFalse(lesson.recent.last().done)
        assertFalse(lesson.isComplete)
    }

    @Test
    fun anOldPassageRepeatedTodayIsDoneInTheOldBlockOnly() {
        val passages = listOf(Fx.passage(1, 2, 1, 5, last = 5), Fx.passage(2, 2, 6, 10, last = 6))
        val outcome = PassageOps.review(passages, 1, ReviewGrade.FORGOT, emptySet(), today, m)!!
        val log = listOf(Fx.logOf(outcome.before, ReviewKind.OLD, ReviewGrade.FORGOT, today))
        val lesson = lesson(null, outcome.passages, log)

        assertTrue(lesson.recent.isEmpty(), "tomorrow is its day in the ladder")
        val done = lesson.old.single { it.passageId == 1L }
        assertTrue(done.done)
        assertEquals(ReviewGrade.FORGOT, done.grade)
        assertEquals(PassageStage.R1, done.stage)
    }

    // ---- budget ----

    private fun overdueRecents(count: Int): List<Passage> =
        (1..count).map { recent(it.toLong(), due = today - 20 + it) } // 1 is the most overdue

    @Test
    fun theBudgetCapsTheRecentBlockOnly_theOldPortionGoesInWholeAndTheNewPassageWaits() {
        // 8 repetitions of 191 s: 7 fit into 1500 s (1337 s), the 8th does not: a backlog. The old portion is not cut by the
        // budget (it is one passage, 191 s, after the 1500 s line of the recent block), and the new passage waits.
        val passages = overdueRecents(8) + Fx.passage(20, 3, 1, 5)
        val lesson = lesson(plan114, passages)

        assertEquals((1L..7L).toList(), lesson.recent.map { it.passageId }, "the most overdue ones are done first")
        assertEquals(listOf(20L), lesson.old.map { it.passageId }, "the old portion is whole")
        assertEquals(LessonNewState.DEFERRED, lesson.newBlock?.state)
        assertTrue(lesson.catchingUp)
        assertEquals(2, lesson.deferredCount, "one repetition and the new passage; the old portion is never counted")
        assertTrue(lesson.recent.sumOf { it.estimateSeconds } <= HifzConstants.LESSON_BUDGET_SECONDS)
        assertEquals(26, lesson.totalMinutes) // 1337 s of recent + 191 s of old = 1528 s
    }

    @Test
    fun theNewPassageIsKeptEvenWhenItPushesTheLessonPastTheBudgetIfNoRepetitionWasCut() {
        // 3 repetitions (573 s) + one old (191 s) + new (752 s) = 1516 s > 1500 s, but nothing of the repetitions was cut:
        // the person chose the pace, the new passage stays.
        val passages = overdueRecents(3) + Fx.passage(20, 3, 1, 5)
        val lesson = lesson(plan114, passages)
        assertEquals(3, lesson.recent.size)
        assertEquals(1, lesson.old.size)
        assertEquals(LessonNewState.PENDING, lesson.newBlock?.state)
        assertEquals(0, lesson.deferredCount)
        assertFalse(lesson.catchingUp)
        assertEquals(764, lesson.old.sumOf { it.estimateSeconds } + lesson.recent.sumOf { it.estimateSeconds })
        assertEquals(26, lesson.totalMinutes, "1516 s: past the 25 minutes, and that is fine")
    }

    @Test
    fun aCutRepetitionDefersTheNewPassageWholeButNotTheOldPortion() {
        val passages = overdueRecents(12) + Fx.passage(20, 3, 1, 5)
        val lesson = lesson(plan114, passages)
        assertEquals(7, lesson.recent.size)
        assertEquals(1, lesson.old.size)
        assertEquals(LessonNewState.DEFERRED, lesson.newBlock?.state)
        assertEquals(6, lesson.deferredCount, "five repetitions and the new passage")
        assertTrue(lesson.catchingUp)
        assertFalse(lesson.isComplete)
    }

    @Test
    fun aRecentBlockThatJustFitsIsNotABacklogAndTheOldPortionDoesNotChangeThat() {
        // Seven repetitions fill 1337 s of the 1500 s; the old portion (191 s) is not counted against them, so nothing is cut.
        val passages = overdueRecents(7) + Fx.passage(20, 3, 1, 5)
        val lesson = lesson(plan114, passages)
        assertEquals(7, lesson.recent.size)
        assertEquals(1, lesson.old.size)
        assertEquals(LessonNewState.PENDING, lesson.newBlock?.state)
        assertEquals(0, lesson.deferredCount)
        assertFalse(lesson.catchingUp)
    }

    @Test
    fun aPersonWhoKnowsJuzAmmaStillLearnsNewEveryDayWithTheWeekCircle() {
        // About the size of Juz Amma in the real text (564 ayahs, ~22 000 characters; 60 characters an ayah make it bigger on purpose):
        // a week of the circle is far beyond the 25 minutes, and the new passage is still in the lesson.
        val metrics = Fx.metrics(chars = { _, _ -> 60 })
        var old = emptyList<Passage>()
        for (surah in 114 downTo 78) old = PassageOps.markKnown(old, surah, 1, metrics.ayahCount(surah), today - 30, metrics)
        val plan = Fx.plan(listOf(77, 76), daily = 5, manzilDays = 7)
        val lesson = LessonBuilder.build(LessonInput(today, plan, old, emptyList(), metrics))

        assertTrue(lesson.old.isNotEmpty())
        assertTrue(
            lesson.old.sumOf { it.estimateSeconds } > HifzConstants.LESSON_BUDGET_SECONDS,
            "the old portion alone is past the budget",
        )
        assertEquals(LessonNewState.PENDING, lesson.newBlock?.state, "new learning does not wait for the old circle")
        assertTrue(lesson.recent.isEmpty())
        assertEquals(0, lesson.deferredCount)
        assertFalse(lesson.catchingUp)
        assertTrue(lesson.totalMinutes > HifzConstants.LESSON_BUDGET_MINUTES)
        val seconds = lesson.old.sumOf { it.estimateSeconds } + lesson.newBlock!!.estimateSeconds
        assertEquals(HifzConstants.minutes(seconds), lesson.totalMinutes)
        assertEquals(lesson.totalMinutes, lesson.remainingMinutes, "nothing is done yet")
    }

    @Test
    fun anOldPortionBiggerThanTheBudgetDefersNothingAndIsNotCatchingUp() {
        // 20 old passages of 500 characters and a one-day circle: all of them are in the portion, 20 x 191 s = 64 minutes.
        val old = (1..20).map { Fx.passage(it.toLong(), 2, 5 * (it - 1) + 1, 5 * it) }
        val lesson = lesson(Fx.plan(listOf(114), manzilDays = 1), old)
        assertEquals(20, lesson.old.size)
        assertTrue(lesson.totalMinutes > 2 * HifzConstants.LESSON_BUDGET_MINUTES)
        assertEquals(0, lesson.deferredCount)
        assertFalse(lesson.catchingUp)
        assertEquals(LessonNewState.PENDING, lesson.newBlock?.state)
    }

    @Test
    fun theOldBlockIsExactlyTheManzilPortionEvenWithABacklogOfRepetitions() {
        val old = (1..6).map { Fx.passage(100L + it, 3, 5 * (it - 1) + 1, 5 * it) }
        val passages = overdueRecents(12) + old
        val plan = Fx.plan(listOf(114), manzilDays = 2)
        val portion = Manzil.portion(passages, emptyList(), 2, m, today)
        val lesson = lesson(plan, passages)
        assertEquals(portion.items.map { it.passage.id }, lesson.old.map { it.passageId })
        assertTrue(lesson.catchingUp, "the recent block was cut")
        assertEquals(6, lesson.deferredCount, "five cut repetitions and the new passage; the old portion is not counted")
    }

    @Test
    fun learningTheNewPassageDoesNotShrinkTheRecentBlock() {
        // Five repetitions (955 s) and a new passage of 752 s together are past 1500 s. The budget of the recent block does not
        // depend on the new passage: the same five stay after it is learned.
        val recents = overdueRecents(5)
        val before = lesson(plan114, recents)
        assertEquals(5, before.recent.size)
        assertEquals(LessonNewState.PENDING, before.newBlock?.state)

        val learned = Fx.passage(30, 114, 1, 6, PassageStage.R1, due = today + 1, last = today, learned = today)
        val log = listOf(Fx.logOf(learned, ReviewKind.NEW, ReviewGrade.GOOD, today))
        val after = lesson(plan114, recents + learned, log)
        assertEquals(before.recent.map { it.passageId }, after.recent.map { it.passageId })
        assertEquals(LessonNewState.DONE, after.newBlock?.state)
        assertEquals(0, after.deferredCount)
    }

    @Test
    fun aLadderPassageThatBecameOldTodayIsDoneInRecentAndNotOfferedAgainInOld() {
        val circle = listOf(Fx.passage(20, 3, 1, 5), recent(1, due = today, stage = PassageStage.R30))
        val outcome = PassageOps.review(circle, 1, ReviewGrade.GOOD, emptySet(), today, m)!!
        assertEquals(PassageStage.OLD, outcome.passages.first { it.id == 1L }.stage)
        val log = listOf(Fx.logOf(outcome.before, ReviewKind.RECENT, ReviewGrade.GOOD, today))
        val lesson = lesson(null, outcome.passages, log)
        assertEquals(listOf(1L), lesson.recent.map { it.passageId })
        assertTrue(lesson.recent.single().done)
        assertEquals(listOf(20L), lesson.old.map { it.passageId }, "the passage that graduated today is not in the Old block")
    }

    @Test
    fun anOldPassageRepeatedRecentlyIsNotOfferedAgainUntilManzilDaysHavePassed() {
        val passages = listOf(Fx.passage(1, 2, 1, 5, last = today - 3))
        assertTrue(lesson(null, passages).old.isEmpty(), "7 days is the default circle")
        assertEquals(1, lesson(null, listOf(Fx.passage(1, 2, 1, 5, last = today - 7))).old.size)
        assertEquals(1, lesson(null, listOf(Fx.passage(1, 2, 1, 5, last = null))).old.size)
        assertTrue(lesson(null, passages).isEmpty)
    }

    @Test
    fun theNewPassageIsKeptWhenTheRestLeavesRoom() {
        val lesson = lesson(plan114, overdueRecents(3))
        assertEquals(LessonNewState.PENDING, lesson.newBlock?.state)
        assertFalse(lesson.catchingUp)
        assertEquals(0, lesson.deferredCount)
        assertEquals(23, lesson.totalMinutes) // 573 + 752 = 1325 s = 22.1 minutes, rounded up
    }

    @Test
    fun aHighPaceNewPassageIsNeverStalledByTheBudget() {
        // 20 ayahs of Al-Baqarah a day: 2000 characters, about 41 minutes - more than the whole budget, alone and with reviews.
        val big = Fx.plan(listOf(2), daily = 20)
        val alone = lesson(big, emptyList())
        assertEquals(LessonNewState.PENDING, alone.newBlock?.state)
        assertFalse(alone.catchingUp)

        // A due repetition does not push it out either: no repetition was cut, so the person's pace stands.
        val withReview = lesson(big, listOf(recent(1, due = today - 1).copy(surah = 3, startAyah = 1, endAyah = 5)))
        assertEquals(1, withReview.recent.size)
        assertEquals(LessonNewState.PENDING, withReview.newBlock?.state)
        assertEquals(0, withReview.deferredCount)
        assertFalse(withReview.catchingUp)
        assertTrue(withReview.totalMinutes > HifzConstants.LESSON_BUDGET_MINUTES)
    }

    @Test
    fun finishingCardsNeitherPullsDeferredOnesInNorThrowsAcceptedOnesOut() {
        val start = overdueRecents(8) + Fx.passage(20, 3, 1, 5)
        val before = lesson(plan114, start)

        var passages = start
        val log = mutableListOf<ReviewLogEntry>()
        for (id in listOf(1L, 2L)) {
            val outcome = PassageOps.review(passages, id, ReviewGrade.GOOD, emptySet(), today, m)!!
            passages = outcome.passages
            log += Fx.logOf(outcome.before, ReviewKind.RECENT, ReviewGrade.GOOD, today)
        }
        val after = lesson(plan114, passages, log)

        assertEquals(before.recent.map { it.passageId }.toSet(), after.recent.map { it.passageId }.toSet())
        assertEquals(listOf(true, true, false, false, false, false, false), after.recent.map { it.done })
        assertEquals(before.deferredCount, after.deferredCount)
        assertEquals(before.totalMinutes, after.totalMinutes)
        assertTrue(after.remainingMinutes < before.remainingMinutes)
        assertEquals(LessonNewState.DEFERRED, after.newBlock?.state)
    }

    @Test
    fun aBlockStartedTodayKeepsEverythingItHad_evenAfterTheOldBlockIsFinishedFirst() {
        // Recent (3) + old portion (1) fit. The person does the old card first: the recent cards are all still there.
        val passages = overdueRecents(3) + Fx.passage(20, 3, 1, 5)
        val before = lesson(plan114, passages)
        val outcome = PassageOps.review(passages, 20, ReviewGrade.GOOD, emptySet(), today, m)!!
        val log = listOf(Fx.logOf(outcome.before, ReviewKind.OLD, ReviewGrade.GOOD, today))
        val after = lesson(plan114, outcome.passages, log)
        assertEquals(before.recent.map { it.passageId }, after.recent.map { it.passageId })
        assertEquals(1, after.old.size)
        assertTrue(after.old.single().done)
        assertEquals(before.newBlock?.state, after.newBlock?.state)
    }

    @Test
    fun theLessonIsCompleteWhenEverythingInItIsDoneAndDeferredItemsDoNotCount() {
        val passages = overdueRecents(8)
        var state = passages
        val log = mutableListOf<ReviewLogEntry>()
        val initial = lesson(null, state)
        assertEquals(1, initial.deferredCount)
        for (item in initial.recent) {
            val outcome = PassageOps.review(state, item.passageId, ReviewGrade.GOOD, emptySet(), today, m)!!
            state = outcome.passages
            log += Fx.logOf(outcome.before, ReviewKind.RECENT, ReviewGrade.GOOD, today)
        }
        val finished = lesson(null, state, log)
        assertTrue(finished.isComplete)
        assertEquals(0, finished.remainingMinutes)
        assertEquals(7, finished.recent.size, "the deferred eighth is still due, not in the lesson")
        assertEquals(initial.totalMinutes, finished.totalMinutes)
    }

    @Test
    fun theOldBlockIsInTheOrderOfTheMushaf() {
        val old = listOf(
            Fx.passage(1, 114, 1, 3, last = 5),
            Fx.passage(2, 2, 1, 5, last = 1),
            Fx.passage(3, 78, 1, 5, last = 3),
        )
        val lesson = lesson(Fx.plan(listOf(112), manzilDays = 1), old)
        assertEquals(listOf(2L, 3L, 1L), lesson.old.map { it.passageId })
    }

    @Test
    fun withoutAPlanTheCircleLengthIsSuggestedFromTheOldWeight() {
        // 50 old passages of 500 characters: a 7-day circle would put about 8 of them in a day; the suggested one far fewer.
        val old = (1..50).map { Fx.passage(it.toLong(), 2, 5 * (it - 1) + 1, 5 * it) }
        val suggested = tj.app.quran_todo.hifz.schedule.Manzil.suggestedDays(old.sumOf { m.weight(it) })
        assertTrue(suggested > HifzConstants.MANZIL_DAYS_DEFAULT)
        val lesson = lesson(null, old)
        assertEquals((50 + suggested - 1) / suggested, lesson.old.size)
    }
}
