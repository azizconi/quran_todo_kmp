package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.PassageOps
import tj.app.quran_todo.hifz.schedule.Pagination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassageOpsTest {
    private val m = Fx.flat // 100 characters an ayah, 10 ayahs a page: 5 ayahs weigh 500, MAX_OLD_PASSAGE_CHARS 1200 = 12 ayahs
    private val today = 500

    private fun review(list: List<Passage>, id: Long, grade: ReviewGrade, marks: Set<Int> = emptySet()) =
        assertNotNull(PassageOps.review(list, id, grade, marks, today, m))

    // ---- a new passage ----

    @Test
    fun aNewPassageIsR1DueTomorrowLearnedToday() {
        val p = PassageOps.newPassage(78, 1, 5, emptySet(), today)
        assertEquals(0, p.id)
        assertEquals(PassageStage.R1, p.stage)
        assertEquals(today + 1, p.nextDueEpochDay)
        assertEquals(today, p.learnedEpochDay)
        assertEquals(today, p.lastReviewedEpochDay)
        assertEquals(0, p.lapses)
        assertTrue(p.hard.isEmpty())
    }

    @Test
    fun aNewPassageKeepsTheMarkedAyahsInsideItsRange() {
        val p = PassageOps.newPassage(78, 1, 5, setOf(2, 4, 9), today)
        assertEquals(mapOf(2 to 2, 4 to 2), p.hard)
    }

    @Test
    fun overlapsFindsEveryTouchedAyahButNotTheNeighbours() {
        val list = listOf(Fx.passage(1, 78, 6, 10, PassageStage.R1, due = 1))
        assertTrue(PassageOps.overlaps(list, 78, 10, 12))
        assertTrue(PassageOps.overlaps(list, 78, 1, 6))
        assertTrue(PassageOps.overlaps(list, 78, 7, 8))
        assertFalse(PassageOps.overlaps(list, 78, 1, 5))
        assertFalse(PassageOps.overlaps(list, 78, 11, 15))
        assertFalse(PassageOps.overlaps(list, 79, 6, 10))
    }

    // ---- a repetition ----

    @Test
    fun aRepetitionOfTheLadderIsLoggedAsRecentAndMovesTheStage() {
        val list = listOf(Fx.passage(1, 78, 1, 5, PassageStage.R3, due = today, last = today - 3))
        val outcome = review(list, 1, ReviewGrade.GOOD)
        assertEquals(ReviewKind.RECENT, outcome.kind)
        val p = outcome.passages.single()
        assertEquals(PassageStage.R7, p.stage)
        assertEquals(today + 7, p.nextDueEpochDay)
        assertEquals(today, p.lastReviewedEpochDay)
        assertEquals(1 to 5, outcome.before.startAyah to outcome.before.endAyah)
    }

    @Test
    fun aRepetitionOfAnOldPassageIsLoggedAsOldAndForgotSendsItBackToTheLadder() {
        val list = listOf(Fx.passage(1, 78, 1, 5, PassageStage.OLD, last = 100))
        val good = review(list, 1, ReviewGrade.GOOD)
        assertEquals(ReviewKind.OLD, good.kind)
        assertEquals(PassageStage.OLD, good.passages.single().stage)
        assertEquals(today, good.passages.single().lastReviewedEpochDay)

        val forgot = review(list, 1, ReviewGrade.FORGOT, marks = setOf(3))
        assertEquals(ReviewKind.OLD, forgot.kind)
        val p = forgot.passages.single()
        assertEquals(PassageStage.R1, p.stage)
        assertEquals(today + 1, p.nextDueEpochDay)
        assertEquals(1, p.lapses)
        assertEquals(mapOf(3 to 2), p.hard)
    }

    @Test
    fun reviewingAnUnknownPassageReturnsNull() {
        assertNull(PassageOps.review(emptyList(), 7, ReviewGrade.GOOD, emptySet(), today, m))
    }

    @Test
    fun goodRepetitionsCountTheDifficultAyahsDownAndRemoveThem() {
        var list = listOf(Fx.passage(1, 78, 1, 5, PassageStage.R1, due = today, hard = mapOf(2 to 2)))
        list = review(list, 1, ReviewGrade.GOOD).passages
        assertEquals(mapOf(2 to 1), list.single().hard)
        list = review(list, 1, ReviewGrade.EASY).passages
        assertEquals(emptyMap(), list.single().hard)
    }

    // ---- merging old passages ----

    @Test
    fun aPassageThatBecomesOldIsMergedWithTheOldNeighboursOfItsSurah() {
        val list = listOf(
            Fx.passage(1, 2, 1, 5, PassageStage.OLD, last = null, lapses = 1, hard = mapOf(3 to 1), learned = 10),
            Fx.passage(2, 2, 6, 8, PassageStage.OLD, last = 400, lapses = 2, learned = 20),
            Fx.passage(3, 2, 9, 10, PassageStage.R30, due = today, last = 470, lapses = 3, learned = 30),
            Fx.passage(4, 3, 1, 3, PassageStage.OLD, last = 1), // another surah: not a neighbour
        )
        val outcome = review(list, 3, ReviewGrade.GOOD)
        val merged = outcome.passages.single { it.surah == 2 }
        assertEquals(3, merged.id, "the repeated passage keeps its row")
        assertEquals(1, merged.startAyah)
        assertEquals(10, merged.endAyah)
        assertEquals(PassageStage.OLD, merged.stage)
        assertNull(merged.nextDueEpochDay)
        assertEquals(10, merged.learnedEpochDay)
        assertNull(merged.lastReviewedEpochDay, "a never reviewed part makes the merged passage the oldest")
        assertEquals(1 + 2 + 3, merged.lapses)
        assertEquals(mapOf(3 to 1), merged.hard)
        assertEquals(2, outcome.passages.size)
    }

    @Test
    fun theMergedLastReviewedIsTheSmallerOneWhenBothAreKnown() {
        val list = listOf(
            Fx.passage(1, 2, 1, 3, PassageStage.OLD, last = 300),
            Fx.passage(2, 2, 4, 5, PassageStage.R30, due = today),
        )
        val merged = review(list, 2, ReviewGrade.GOOD).passages.single()
        assertEquals(300, merged.lastReviewedEpochDay)
    }

    @Test
    fun theMergedWeightNeverExceedsOnePageButTheLimitItselfIsAllowed() {
        // 12 ayahs = 1200 characters = MAX_OLD_PASSAGE_CHARS.
        assertEquals(1200, HifzConstants.MAX_OLD_PASSAGE_CHARS)
        val allowed = listOf(
            Fx.passage(1, 2, 1, 10, PassageStage.OLD), // 1000
            Fx.passage(2, 2, 11, 12, PassageStage.R30, due = today), // 200: 1200 in total, allowed
            Fx.passage(3, 2, 14, 14, PassageStage.OLD), // not adjacent
        )
        val merged = review(allowed, 2, ReviewGrade.GOOD).passages
        assertEquals(setOf(2L to (1 to 12), 3L to (14 to 14)), merged.map { it.id to (it.startAyah to it.endAyah) }.toSet())

        val tooMuch = listOf(
            Fx.passage(1, 2, 1, 10, PassageStage.OLD), // 1000
            Fx.passage(2, 2, 11, 13, PassageStage.R30, due = today), // 300: 1300 in total
        )
        val kept = review(tooMuch, 2, ReviewGrade.GOOD).passages
        assertEquals(setOf(1L to (1 to 10), 2L to (11 to 13)), kept.map { it.id to (it.startAyah to it.endAyah) }.toSet())
    }

    @Test
    fun aHeavyNeighbourIsNotMergedAndNonAdjacentPassagesAreLeftAlone() {
        val list = listOf(
            Fx.passage(1, 2, 1, 10, PassageStage.OLD), // 1000
            Fx.passage(2, 2, 11, 15, PassageStage.R30, due = today), // 500: 1500 > 1200
            Fx.passage(3, 2, 17, 18, PassageStage.OLD), // not adjacent (16 is missing)
            Fx.passage(4, 2, 19, 20, PassageStage.R7, due = today + 3), // a ladder neighbour is not OLD
        )
        val result = review(list, 2, ReviewGrade.GOOD).passages
        assertEquals(4, result.size)
        assertEquals(11 to 15, result.single { it.id == 2L }.let { it.startAyah to it.endAyah })
    }

    @Test
    fun theLighterNeighbourIsMergedFirst() {
        val list = listOf(
            Fx.passage(1, 2, 1, 9, PassageStage.OLD), // 900, left
            Fx.passage(2, 2, 10, 11, PassageStage.R30, due = today), // 200
            Fx.passage(3, 2, 12, 13, PassageStage.OLD), // 200, right
        )
        val result = review(list, 2, ReviewGrade.GOOD).passages
        // right first: 10..13 (400); then the left one would make 1300: no.
        assertEquals(setOf(1L to (1 to 9), 2L to (10 to 13)), result.map { it.id to (it.startAyah to it.endAyah) }.toSet())
    }

    @Test
    fun aRepetitionOfAnOldPassageDoesNotMergeAnything() {
        val list = listOf(
            Fx.passage(1, 2, 1, 3, PassageStage.OLD),
            Fx.passage(2, 2, 4, 5, PassageStage.OLD),
        )
        assertEquals(2, review(list, 2, ReviewGrade.GOOD).passages.size)
    }

    @Test
    fun aPassageThatDoesNotBecomeOldIsNotMerged() {
        val list = listOf(
            Fx.passage(1, 2, 1, 3, PassageStage.OLD),
            Fx.passage(2, 2, 4, 5, PassageStage.R14, due = today),
        )
        assertEquals(2, review(list, 2, ReviewGrade.GOOD).passages.size) // R14 -> R30
        assertEquals(2, review(list, 2, ReviewGrade.FORGOT).passages.size)
    }

    // ---- check yourself ----

    @Test
    fun aCheckMarksTheCoveringPassagesAndChangesNoStage() {
        val list = listOf(
            Fx.passage(1, 78, 1, 5, PassageStage.R3, due = today + 2, hard = mapOf(2 to 1)),
            Fx.passage(2, 78, 6, 10, PassageStage.OLD),
        )
        val result = PassageOps.check(list, 78, setOf(2, 7, 30))
        assertEquals(mapOf(2 to 2), result[0].hard, "an existing mark starts over")
        assertEquals(mapOf(7 to 2), result[1].hard)
        assertEquals(list.map { it.stage to it.nextDueEpochDay }, result.map { it.stage to it.nextDueEpochDay })
        assertEquals(list.map { it.lastReviewedEpochDay }, result.map { it.lastReviewedEpochDay })
    }

    @Test
    fun aCheckWithoutMarksChangesNothing() {
        val list = listOf(Fx.passage(1, 78, 1, 5, PassageStage.R3, due = today, hard = mapOf(2 to 1)))
        assertEquals(list, PassageOps.check(list, 78, emptySet()))
    }

    @Test
    fun theCoveringPassageOfACheckIsFoundOnlyWhenOneCoversTheWholeRange() {
        val list = listOf(
            Fx.passage(1, 78, 1, 5, PassageStage.R3, due = today),
            Fx.passage(2, 78, 6, 10, PassageStage.OLD),
        )
        assertEquals(1L, PassageOps.coveringPassageId(list, 78, 2, 4))
        assertNull(PassageOps.coveringPassageId(list, 78, 4, 7))
        assertNull(PassageOps.coveringPassageId(list, 78, 11, 12))
    }

    // ---- "I know it" ----

    @Test
    fun markKnownMakesOldPassagesNeverReviewedCutByPages() {
        val result = PassageOps.markKnown(emptyList(), 2, 1, 25, today, m)
        assertEquals(listOf(1 to 10, 11 to 20, 21 to 25), result.map { it.startAyah to it.endAyah })
        assertTrue(result.all { it.id == 0L && it.stage == PassageStage.OLD && it.lastReviewedEpochDay == null })
        assertTrue(result.all { it.nextDueEpochDay == null && it.lapses == 0 && it.learnedEpochDay == today })
    }

    @Test
    fun markKnownSkipsTheCoveredAyahs() {
        val existing = listOf(Fx.passage(1, 2, 12, 14, PassageStage.R7, due = today + 2))
        val result = PassageOps.markKnown(existing, 2, 1, 25, today, m)
        val added = result.filter { it.id == 0L }.map { it.startAyah to it.endAyah }
        // 1..11 (pages 1..10 and 11), 15..25 (pages 15..20 and 21..25)
        assertEquals(listOf(1 to 10, 11 to 11, 15 to 20, 21 to 25), added)
        assertEquals(existing.single(), result.first { it.id == 1L }, "the covered passage is untouched")
    }

    @Test
    fun aShortSurahOnOnePageIsOnePassage() {
        val result = PassageOps.markKnown(emptyList(), 112, 1, 4, today, m)
        assertEquals(listOf(1 to 4), result.map { it.startAyah to it.endAyah })
    }

    @Test
    fun cuttingByPageFollowsThePagesOfTheMetrics() {
        // Ayahs 1..3 on page 5, 4..5 on page 6, 6 on page 7.
        val metrics = Fx.metrics(page = { _, ayah -> if (ayah <= 3) 5 else if (ayah <= 5) 6 else 7 })
        assertEquals(listOf(AyahRange(112, 1, 3), AyahRange(112, 4, 4)), Pagination.cutByPage(112, 1, 4, metrics))
        assertEquals(listOf(AyahRange(114, 2, 3), AyahRange(114, 4, 5), AyahRange(114, 6, 6)), Pagination.cutByPage(114, 2, 6, metrics))
    }

    // ---- forget ----

    @Test
    fun forgetInTheMiddleSplitsAPassageAndTheMarksFollowTheNumbers() {
        val list = listOf(
            Fx.passage(1, 78, 1, 10, PassageStage.R7, due = today + 3, last = today - 4, lapses = 2, learned = 7, hard = mapOf(3 to 2, 8 to 1)),
        )
        val result = PassageOps.forget(list, 78, 4, 6)
        assertEquals(2, result.size)
        val left = result[0]
        val right = result[1]
        assertEquals(1L, left.id)
        assertEquals(1 to 3, left.startAyah to left.endAyah)
        assertEquals(mapOf(3 to 2), left.hard)
        assertEquals(0L, right.id)
        assertEquals(7 to 10, right.startAyah to right.endAyah)
        assertEquals(mapOf(8 to 1), right.hard)
        for (part in result) {
            assertEquals(PassageStage.R7, part.stage)
            assertEquals(today + 3, part.nextDueEpochDay)
            assertEquals(2, part.lapses)
            assertEquals(7, part.learnedEpochDay)
        }
    }

    @Test
    fun forgetAtAnEndTrimsAndForgetOfEverythingRemoves() {
        val list = listOf(Fx.passage(1, 78, 5, 10, PassageStage.OLD, hard = mapOf(5 to 1, 10 to 2)))
        val head = PassageOps.forget(list, 78, 1, 7).single()
        assertEquals(1L, head.id)
        assertEquals(8 to 10, head.startAyah to head.endAyah)
        assertEquals(mapOf(10 to 2), head.hard)
        val tail = PassageOps.forget(list, 78, 9, 20).single()
        assertEquals(5 to 8, tail.startAyah to tail.endAyah)
        assertEquals(mapOf(5 to 1), tail.hard)
        assertTrue(PassageOps.forget(list, 78, 5, 10).isEmpty())
        assertTrue(PassageOps.forget(list, 78, 1, 40).isEmpty())
    }

    @Test
    fun forgetOutsideAPassageOrInAnotherSurahChangesNothing() {
        val list = listOf(Fx.passage(1, 78, 5, 10, PassageStage.OLD))
        assertEquals(list, PassageOps.forget(list, 78, 11, 20))
        assertEquals(list, PassageOps.forget(list, 79, 5, 10))
    }

    @Test
    fun forgetCanTouchSeveralPassages() {
        val list = listOf(
            Fx.passage(1, 78, 1, 5, PassageStage.R1, due = today),
            Fx.passage(2, 78, 6, 10, PassageStage.OLD),
            Fx.passage(3, 78, 11, 15, PassageStage.OLD),
        )
        val result = PassageOps.forget(list, 78, 4, 12)
        assertEquals(listOf(1 to 3, 13 to 15), result.map { it.startAyah to it.endAyah })
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }
}
