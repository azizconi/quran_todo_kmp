package tj.app.quran_todo.hifz

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.hifz.schedule.LegacyMirror
import tj.app.quran_todo.hifz.schedule.TextMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusTest {
    private val m = Fx.flat

    @Test
    fun aSurahIsLearnedWhenEveryAyahIsCoveredEvenByManyPassages() {
        val passages = listOf(
            Fx.passage(1, 112, 1, 2, PassageStage.R1, due = 5),
            Fx.passage(2, 112, 3, 4, PassageStage.OLD),
        )
        assertEquals(SurahHifzStatus.LEARNED, HifzStatuses.all(passages, null, m)[112])
    }

    @Test
    fun aPartlyCoveredSurahIsLearning() {
        val statuses = HifzStatuses.all(listOf(Fx.passage(1, 112, 1, 2)), null, m)
        assertEquals(mapOf(112 to SurahHifzStatus.LEARNING), statuses)
    }

    @Test
    fun theCurrentSurahOfThePlanIsLearningEvenWithNothingCovered() {
        val plan = Fx.plan(listOf(114, 113, 112))
        assertEquals(mapOf(114 to SurahHifzStatus.LEARNING), HifzStatuses.all(emptyList(), plan, m))
    }

    @Test
    fun whenTheCurrentSurahIsCoveredTheNextOneBecomesCurrent() {
        val plan = Fx.plan(listOf(114, 113, 112))
        val statuses = HifzStatuses.all(listOf(Fx.passage(1, 114, 1, 6)), plan, m)
        assertEquals(mapOf(114 to SurahHifzStatus.LEARNED, 113 to SurahHifzStatus.LEARNING), statuses)
    }

    @Test
    fun noPlanAndNoPassagesMeansNoStatuses() {
        assertTrue(HifzStatuses.all(emptyList(), null, m).isEmpty())
    }

    @Test
    fun theCoverageOfASurahListsCoveredAndDifficultAyahsAndItsPassagesInOrder() {
        val passages = listOf(
            Fx.passage(2, 78, 6, 10, PassageStage.OLD, hard = mapOf(8 to 1)),
            Fx.passage(1, 78, 1, 3, PassageStage.R3, due = 9, hard = mapOf(2 to 2)),
            Fx.passage(3, 114, 1, 6),
        )
        val plan = Fx.plan(listOf(78))
        val coverage = HifzStatuses.surahCoverage(78, passages, plan, m)
        assertEquals(40, coverage.ayahCount)
        assertEquals(setOf(1, 2, 3, 6, 7, 8, 9, 10), coverage.coveredAyahs)
        assertEquals(setOf(2, 8), coverage.hardAyahs)
        assertEquals(listOf(1L, 2L), coverage.passages.map { it.id })
        assertTrue(coverage.isCurrent)
        assertEquals(SurahHifzStatus.LEARNING, coverage.status)

        val other = HifzStatuses.surahCoverage(114, passages, plan, m)
        assertFalse(other.isCurrent)
        assertEquals(SurahHifzStatus.LEARNED, other.status)
        assertTrue(HifzStatuses.surahCoverage(1, passages, plan, m).status == null)
    }

    @Test
    fun coverageCountsOverlappingPassagesOnce() {
        val coverage = Coverage(listOf(Fx.passage(1, 78, 1, 5), Fx.passage(2, 78, 4, 8)), m)
        assertEquals(8, coverage.coveredCount(78))
        assertEquals(32, coverage.uncoveredCount(78))
        assertEquals(9, coverage.firstUncovered(78))
        assertNull(Coverage(listOf(Fx.passage(1, 114, 1, 6)), m).firstUncovered(114))
    }
}

class MirrorTest {
    private fun row(surah: Int, ayah: Int, status: AyahTodoStatus = AyahTodoStatus.LEARNED, updatedAt: Long = 7) =
        AyahTodoEntity(LegacyMirror.globalAyahNumber(surah, ayah), surah, status, updatedAt)

    @Test
    fun theGlobalNumberIsTheFirstNumberOfTheSurahPlusTheOffset() {
        assertEquals(6222, LegacyMirror.globalAyahNumber(112, 1))
        assertEquals(6236, LegacyMirror.globalAyahNumber(114, 6))
        assertEquals(1, LegacyMirror.globalAyahNumber(1, 1))
        assertEquals(QuranFacts.firstAyahNumber(2) + 285, LegacyMirror.globalAyahNumber(2, 286))
    }

    @Test
    fun rowsAreLearnedExactlyForTheCoveredAyahs() {
        val delta = LegacyMirror.sync(
            surah = 112,
            coveredAyahs = setOf(1, 2),
            status = SurahHifzStatus.LEARNING,
            existingAyahRows = listOf(row(112, 1, updatedAt = 5), row(112, 4, AyahTodoStatus.LEARNING)),
            existingSurahRow = SurahTodoEntity(112, SurahTodoStatus.LEARNING),
            now = 99,
        )
        assertEquals(listOf(LegacyMirror.globalAyahNumber(112, 4)), delta.deleteAyahNumbers, "a row of an ayah that is not covered goes")
        assertEquals(listOf(LegacyMirror.globalAyahNumber(112, 2)), delta.upsertAyahs.map { it.ayahNumber }, "the right row 1:1 is kept")
        assertEquals(AyahTodoStatus.LEARNED, delta.upsertAyahs.single().status)
        assertEquals(99, delta.upsertAyahs.single().updatedAt)
        assertNull(delta.upsertSurah)
        assertFalse(delta.deleteSurah)
    }

    @Test
    fun aLearningRowOfACoveredAyahBecomesLearned() {
        val delta = LegacyMirror.sync(112, setOf(1), SurahHifzStatus.LEARNING, listOf(row(112, 1, AyahTodoStatus.LEARNING)), null, now = 1)
        assertTrue(delta.deleteAyahNumbers.isEmpty())
        assertEquals(AyahTodoStatus.LEARNED, delta.upsertAyahs.single().status)
        assertEquals(SurahTodoEntity(112, SurahTodoStatus.LEARNING), delta.upsertSurah)
    }

    @Test
    fun theSurahRowFollowsTheDerivedStatus() {
        val learned = LegacyMirror.sync(112, setOf(1, 2, 3, 4), SurahHifzStatus.LEARNED, emptyList(), SurahTodoEntity(112, SurahTodoStatus.LEARNING), 1)
        assertEquals(SurahTodoEntity(112, SurahTodoStatus.LEARNED), learned.upsertSurah)
        assertEquals(4, learned.upsertAyahs.size)

        val same = LegacyMirror.sync(112, setOf(1, 2, 3, 4), SurahHifzStatus.LEARNED, (1..4).map { row(112, it) }, SurahTodoEntity(112, SurahTodoStatus.LEARNED), 1)
        assertTrue(same.isEmpty, "nothing to write when the rows are right already")

        val none = LegacyMirror.sync(112, emptySet(), null, listOf(row(112, 1)), SurahTodoEntity(112, SurahTodoStatus.LEARNING), 1)
        assertTrue(none.deleteSurah, "no status means no row")
        assertEquals(1, none.deleteAyahNumbers.size)
        assertNull(none.upsertSurah)

        val noneAndNoRow = LegacyMirror.sync(112, emptySet(), null, emptyList(), null, 1)
        assertTrue(noneAndNoRow.isEmpty)
    }

    @Test
    fun rowsOfAnotherSurahAreNotTouched() {
        // The caller passes the rows of this surah only; the numbers of 113 are not even in the delta.
        val delta = LegacyMirror.sync(112, setOf(1), SurahHifzStatus.LEARNING, emptyList(), null, 1)
        assertTrue(delta.upsertAyahs.all { it.surahNumber == 112 })
    }
}

class TextMetricsTest {
    @Test
    fun rangesAreSummedAndCountsAndPagesAreAnswered() {
        val metrics = TextMetrics(
            listOf(
                AyahMetric(114, 1, 600, 10), AyahMetric(114, 2, 600, 20), AyahMetric(114, 3, 601, 30),
                AyahMetric(113, 1, 599, 5),
            ),
        )
        assertEquals(3, metrics.ayahCount(114))
        assertEquals(1, metrics.ayahCount(113))
        assertEquals(0, metrics.ayahCount(112))
        assertEquals(60, metrics.chars(114, 1, 3))
        assertEquals(50, metrics.chars(114, 2, 3))
        assertEquals(30, metrics.chars(114, 3))
        assertEquals(60, metrics.surahChars(114))
        assertEquals(0, metrics.chars(114, 3, 2))
        assertEquals(0, metrics.chars(112, 1, 3))
        assertEquals(601, metrics.page(114, 3))
        assertEquals(60, metrics.weight(Fx.passage(1, 114, 1, 3)))
    }

    @Test
    fun theAyahsOfASurahMustBeNumberedFromOneWithoutGaps() {
        assertFailsWith<IllegalArgumentException> {
            TextMetrics(listOf(AyahMetric(114, 1, 1, 1), AyahMetric(114, 3, 1, 1)))
        }
    }

    @Test
    fun theFixtureHasTheRealAyahCounts() {
        val metrics = Fx.flat
        assertEquals(QuranFacts.AYAH_COUNT, (1..QuranFacts.SURAH_COUNT).sumOf { metrics.ayahCount(it) })
        assertEquals(286, metrics.ayahCount(2))
    }
}
