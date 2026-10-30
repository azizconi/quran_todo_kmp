package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardAyahsOfSurahsTest {
    @Test
    fun noPassagesOrNoMarksMeansAnEmptyMap() {
        assertTrue(HifzStatuses.hardAyahs(emptyList()).isEmpty())
        assertTrue(HifzStatuses.hardAyahs(listOf(Fx.passage(1, 112, 1, 4))).isEmpty())
    }

    @Test
    fun theDifficultAyahsOfEverySurahAreListedAscendingInMushafOrderOfSurahs() {
        val passages = listOf(
            Fx.passage(1, 114, 1, 6, PassageStage.R1, due = 5, hard = mapOf(5 to 2, 2 to 1)),
            Fx.passage(2, 112, 1, 4, hard = mapOf(3 to 2)),
            Fx.passage(3, 113, 1, 5),
        )
        val hard = HifzStatuses.hardAyahs(passages)
        assertEquals(listOf(112, 114), hard.keys.toList())
        assertEquals(listOf(2, 5), hard[114]?.toList())
        assertEquals(setOf(3), hard[112])
    }

    @Test
    fun theMarksOfSeveralPassagesOfOneSurahAreMergedAscending() {
        val passages = listOf(
            Fx.passage(1, 2, 1, 5, hard = mapOf(4 to 2, 1 to 2)),
            Fx.passage(2, 2, 6, 9, hard = mapOf(7 to 2)),
            Fx.passage(3, 2, 10, 12, hard = mapOf(11 to 1)),
        )
        assertEquals(listOf(1, 4, 7, 11), HifzStatuses.hardAyahs(passages)[2]?.toList())
    }
}
