package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.Manzil
import tj.app.quran_todo.hifz.schedule.PassageOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManzilTest {
    private val m = Fx.flat // 100 characters an ayah

    /** Five OLD passages of 500 characters in surah 2: 2500 in total. */
    private fun circle(
        p1Last: Int? = 50,
        p2Last: Int? = null,
        p3Last: Int? = 10,
        p4Last: Int? = 10,
        p5Last: Int? = 60,
    ) = listOf(
        Fx.passage(1, 2, 1, 5, last = p1Last),
        Fx.passage(2, 2, 6, 10, last = p2Last),
        Fx.passage(3, 2, 11, 15, last = p3Last),
        Fx.passage(4, 2, 16, 20, last = p4Last),
        Fx.passage(5, 2, 21, 25, last = p5Last),
    )

    private fun ids(portion: tj.app.quran_todo.hifz.schedule.ManzilPortion) = portion.items.map { it.passage.id }

    @Test
    fun theQuotaIsTheCeilingOfTheTotalWeightOverTheDays() {
        assertEquals(358, Manzil.quota(2500, 7))
        assertEquals(500, Manzil.quota(2500, 5))
        assertEquals(84, Manzil.quota(2500, 30))
        assertEquals(1, Manzil.quota(1, 30))
        assertEquals(0, Manzil.quota(0, 7))
    }

    @Test
    fun thePortionTakesTheOldestFirstAndNeverReviewedIsTheOldest() {
        // days = 2 -> quota 1250: P2 (never reviewed), P3 and P4 (day 10; P3 is first in the mushaf), the third one crosses the quota.
        val portion = Manzil.portion(circle(), emptyList(), manzilDays = 2, metrics = m, today = 100)
        assertEquals(1250, portion.quota)
        assertEquals(listOf(2L, 3L, 4L), ids(portion))
        assertTrue(portion.items.none { it.done })
    }

    @Test
    fun thePassageThatCrossesTheQuotaIsTakenWholeAndTheBlockCloses() {
        // days = 7 -> quota 358 < 500: one passage, the oldest.
        val portion = Manzil.portion(circle(), emptyList(), manzilDays = 7, metrics = m, today = 100)
        assertEquals(358, portion.quota)
        assertEquals(listOf(2L), ids(portion))
    }

    @Test
    fun theItemsAreListedInTheOrderOfTheMushafNotOfThePriority() {
        val old = listOf(
            Fx.passage(1, 114, 1, 3, last = 5),
            Fx.passage(2, 2, 1, 5, last = 1),
            Fx.passage(3, 78, 1, 5, last = 3),
        )
        val portion = Manzil.portion(old, emptyList(), manzilDays = 1, metrics = m, today = 100)
        assertEquals(listOf(2L, 3L, 1L), ids(portion))
    }

    @Test
    fun whatWasRepeatedTodayStaysInThePortionAndCountsIntoTheQuota() {
        val today = 100
        val before = circle()
        val planned = ids(Manzil.portion(before, emptyList(), 2, m, today))

        // P2 was repeated today with GOOD.
        val reviewed = PassageOps.review(before, 2, ReviewGrade.GOOD, emptySet(), today, m)!!.passages
        val log = listOf(Fx.log(2, 2, 6, 10, ReviewKind.OLD, ReviewGrade.GOOD, today))
        val portion = Manzil.portion(reviewed, log, 2, m, today)

        assertEquals(planned, ids(portion), "the portion does not jump during the day")
        assertEquals(listOf(true, false, false), portion.items.map { it.done })
        assertEquals(ReviewGrade.GOOD, portion.items.first().grade)
    }

    @Test
    fun aPassageSentBackToTheLadderTodayStaysInThePortionAsDone() {
        val today = 100
        val before = circle()
        val planned = ids(Manzil.portion(before, emptyList(), 2, m, today))

        val forgotten = PassageOps.review(before, 2, ReviewGrade.FORGOT, emptySet(), today, m)!!.passages
        val log = listOf(Fx.log(2, 2, 6, 10, ReviewKind.OLD, ReviewGrade.FORGOT, today))
        val portion = Manzil.portion(forgotten, log, 2, m, today)

        assertEquals(PassageStage.R1, forgotten.first { it.id == 2L }.stage)
        assertEquals(planned, ids(portion))
        assertEquals(listOf(true, false, false), portion.items.map { it.done })
        assertEquals(1250, portion.quota, "the passage that left the circle still counts into the total weight")
    }

    @Test
    fun theBlockIsClosedWhenTheQuotaIsReachedByWhatIsDone() {
        val today = 100
        // quota 358: one passage repeated -> nothing else is offered.
        val reviewed = PassageOps.review(circle(), 2, ReviewGrade.GOOD, emptySet(), today, m)!!.passages
        val log = listOf(Fx.log(2, 2, 6, 10, ReviewKind.OLD, ReviewGrade.GOOD, today))
        val portion = Manzil.portion(reviewed, log, 7, m, today)
        assertEquals(listOf(2L), ids(portion))
        assertTrue(portion.items.single().done)
    }

    @Test
    fun aNewOldPassageInTheMiddleOfTheCircleBreaksNothing() {
        // P6 has just become OLD (reviewed today): it is the newest and goes to the end of the queue.
        val withNew = circle() + Fx.passage(6, 2, 26, 30, last = 100)
        val portion = Manzil.portion(withNew, emptyList(), manzilDays = 2, metrics = m, today = 100)
        assertEquals(1500, portion.quota)
        assertEquals(listOf(2L, 3L, 4L), ids(portion))
    }

    @Test
    fun anEmptyCircleHasAnEmptyPortion() {
        val portion = Manzil.portion(listOf(Fx.passage(1, 2, 1, 5, PassageStage.R3, due = 5)), emptyList(), 7, m, 100)
        assertEquals(0, portion.quota)
        assertTrue(portion.items.isEmpty())
    }

    @Test
    fun logRecordsOfOtherKindsDoNotCountAsRepeatedInTheCircle() {
        val log = listOf(Fx.log(2, 2, 6, 10, ReviewKind.CHECK, ReviewGrade.GOOD, 100), Fx.log(3, 2, 11, 15, ReviewKind.RECENT, day = 100))
        val portion = Manzil.portion(circle(), log, 2, m, 100)
        assertTrue(portion.items.none { it.done })
    }

    /** The days each passage was repeated on, when everything the portion offers is repeated (GOOD) every day. */
    private fun simulate(
        manzilDays: Int,
        passages: List<Passage>,
        days: Int = 30,
        arrivals: Map<Int, Passage> = emptyMap(),
    ): Map<Long, List<Int>> {
        var state = passages
        val reviewedOn = passages.associate { it.id to mutableListOf<Int>() }.toMutableMap()
        val log = mutableListOf<ReviewLogEntry>()
        for (day in 0 until days) {
            arrivals[day]?.let { arrived ->
                state = state + arrived
                reviewedOn[arrived.id] = mutableListOf()
            }
            val todayLog = log.filter { it.epochDay == day }
            val portion = Manzil.portion(state, todayLog, manzilDays, m, day)
            for (item in portion.items.filter { !it.done }) {
                state = PassageOps.review(state, item.passage.id, ReviewGrade.GOOD, emptySet(), day, m)!!.passages
                log += Fx.logOf(item.passage, ReviewKind.OLD, ReviewGrade.GOOD, day)
                reviewedOn.getValue(item.passage.id) += day
            }
        }
        return reviewedOn
    }

    private fun sizes(vararg ayahs: Int): List<Passage> {
        var next = 1
        return ayahs.mapIndexed { index, size ->
            Fx.passage((index + 1).toLong(), 2, next, next + size - 1).also { next += size }
        }
    }

    @Test
    fun aPassageIsEligibleWhenNeverReviewedOrManzilDaysHavePassed() {
        val never = Fx.passage(1, 2, 1, 5, last = null)
        assertTrue(Manzil.isEligible(never, today = 100, manzilDays = 7))
        val boundary = Fx.passage(2, 2, 6, 10, last = 93)
        assertTrue(Manzil.isEligible(boundary, 100, 7), "exactly manzilDays ago")
        assertFalse(Manzil.isEligible(boundary.copy(lastReviewedEpochDay = 94), 100, 7), "one day short")
        assertFalse(Manzil.isEligible(boundary.copy(lastReviewedEpochDay = 100), 100, 7), "today")
        assertTrue(Manzil.isEligible(boundary.copy(lastReviewedEpochDay = 105), 100, 7), "the clock was set back: do not stall")
    }

    @Test
    fun aSingleOldPassageIsRepeatedOnceInManzilDaysNotEveryDay() {
        // Al-Fatiha-like: 285 characters, the quota is 285 / 7 = 41, so the crossing rule alone would take it daily.
        val fatiha = listOf(Fx.passage(1, 1, 1, 7))
        for (manzilDays in listOf(3, 7, 14)) {
            val days = simulate(manzilDays, fatiha).getValue(1)
            assertEquals((0 until 30 step manzilDays).toList(), days, "manzilDays=$manzilDays")
        }
        // The days in between have no Old block at all.
        val afterOneRepetition = PassageOps.review(fatiha, 1, ReviewGrade.GOOD, emptySet(), 0, m)!!.passages
        val portion = Manzil.portion(afterOneRepetition, emptyList(), 7, m, today = 3)
        assertTrue(portion.items.isEmpty())
    }

    @Test
    fun threePassagesComeBackOnceInManzilDaysEach() {
        val days = simulate(7, sizes(5, 5, 5))
        assertEquals(listOf(0, 7, 14, 21, 28), days.getValue(1))
        assertEquals(listOf(1, 8, 15, 22, 29), days.getValue(2))
        assertEquals(listOf(2, 9, 16, 23), days.getValue(3))
    }

    @Test
    fun aLadderPassageThatBecameOldTodayIsNotInTheOldPortionOfTheSameDay() {
        val today = 100
        val circle = listOf(Fx.passage(1, 2, 1, 5), Fx.passage(2, 3, 1, 5, PassageStage.R30, due = today))
        val outcome = PassageOps.review(circle, 2, ReviewGrade.GOOD, emptySet(), today, m)!!
        assertEquals(PassageStage.OLD, outcome.passages.first { it.id == 2L }.stage)
        assertEquals(today, outcome.passages.first { it.id == 2L }.lastReviewedEpochDay)
        val log = listOf(Fx.logOf(outcome.before, ReviewKind.RECENT, ReviewGrade.GOOD, today))

        val portion = Manzil.portion(outcome.passages, log, manzilDays = 2, metrics = m, today = today)
        assertEquals(listOf(1L), ids(portion), "only the passage that was not reviewed today")
        // It is in the circle (its weight counts into the quota) but its turn comes manzilDays later.
        assertEquals(500, portion.quota)
        val graduated = outcome.passages.first { it.id == 2L }
        assertFalse(Manzil.isEligible(graduated, today, 2))
        assertTrue(Manzil.isEligible(graduated, today + 2, 2))
    }

    @Test
    fun aBacklogIsSpreadOverTheFollowingDaysAndNothingComesBackSoonerThanManzilDays() {
        val passages = sizes(5, 2, 10, 7, 3, 8, 4, 10, 6, 2, 9, 5)
        // Right after an import everything is "never reviewed": about 1/manzilDays of the circle a day.
        val firstDay = Manzil.portion(passages, emptyList(), 7, m, today = 0)
        assertTrue(firstDay.items.size in 1 until passages.size, "${firstDay.items.size} of ${passages.size}")
        val reviewed = simulate(7, passages)
        for ((id, days) in reviewed) {
            assertTrue(days.first() < 7, "passage $id has its first turn on day ${days.first()}")
        }
    }

    /**
     * 30 days of daily repetitions of everything the portion offers, for several circle lengths: every passage gets its
     * first turn within the first `manzilDays` days, and NOTHING is repeated sooner than `manzilDays` days after its last
     * repetition. The turns stay close to the rhythm: a passage waits at most a few days longer than `manzilDays` when the
     * quota of a busy day is used up.
     */
    @Test
    fun repeatingTheDailyPortionCoversEveryPassageAndNeverRepeatsSoonerThanManzilDaysFor30Days() {
        val passages = sizes(5, 2, 10, 7, 3, 8, 4, 10, 6, 2, 9, 5)
        for (manzilDays in listOf(3, 7, 14, 30)) {
            val reviewed = simulate(manzilDays, passages)
            for ((id, days) in reviewed) {
                assertTrue(days.isNotEmpty(), "passage $id was never repeated (manzilDays=$manzilDays)")
                assertTrue(days.first() < manzilDays, "passage $id is first repeated on day ${days.first()} (manzilDays=$manzilDays)")
                val gaps = days.zipWithNext { a, b -> b - a }
                assertTrue(gaps.all { it >= manzilDays }, "passage $id is repeated too often: gaps $gaps (manzilDays=$manzilDays)")
                assertTrue(gaps.all { it <= manzilDays + 3 }, "passage $id waits too long: gaps $gaps (manzilDays=$manzilDays)")
            }
        }
    }

    @Test
    fun theCircleKeepsItsRhythmWhenNewOldPassagesJoinInTheMiddle() {
        val start = listOf(Fx.passage(1, 2, 1, 5), Fx.passage(2, 2, 6, 10), Fx.passage(3, 2, 11, 15))
        // A ladder passage reaches OLD on days 5 and 12: it is new in the circle and was repeated that very day.
        val arrivals = mapOf(
            5 to Fx.passage(4, 3, 1, 5, last = 5),
            12 to Fx.passage(5, 3, 6, 10, last = 12),
        )
        val reviewed = simulate(7, start, arrivals = arrivals)
        for ((id, days) in reviewed) {
            assertTrue(days.isNotEmpty(), "passage $id was never repeated")
            assertTrue(days.zipWithNext { a, b -> b - a }.all { it >= 7 }, "passage $id: $days")
        }
        assertTrue(reviewed.getValue(4).first() >= 5 + 7, "the newcomer waits its manzilDays: ${reviewed.getValue(4)}")
        assertTrue(reviewed.getValue(5).first() >= 12 + 7, "the newcomer waits its manzilDays: ${reviewed.getValue(5)}")
        for ((id, days) in reviewed) assertTrue(29 - days.last() < 7 + 4, "passage $id is not repeated since day ${days.last()}")
    }
}
