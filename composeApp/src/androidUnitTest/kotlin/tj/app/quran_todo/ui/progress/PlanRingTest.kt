package tj.app.quran_todo.ui.progress

import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.ui.home.HomeRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The main ring is measured against the PLAN (U24) and the page is four blocks (U25). */
class PlanRingTest {
    private val juzAmma = Fx.plan((78..114).toList(), daily = 3, started = 20_000).copy(scope = HifzScope.JUZ_30)

    private fun covered(vararg pairs: Pair<Int, Int>): Map<Int, Set<Int>> =
        pairs.associate { (surah, count) -> surah to (1..count).toSet() }

    @Test
    fun theTotalOfAPlanIsTheAyahsOfItsSurahs() {
        val ring = assertNotNull(ProgressBuilder.ringOf(juzAmma, emptyMap(), emptyMap()))
        assertEquals(564, ring.total)
        assertEquals(0, ring.covered)
        assertEquals(HifzScope.JUZ_30, ring.scope)
    }

    @Test
    fun aRepeatedSurahInThePlanCountsOnce() {
        val plan = Fx.plan(listOf(114, 114, 113), daily = 3, started = 20_000)
        assertEquals(6 + 5, assertNotNull(ProgressBuilder.ringOf(plan, emptyMap(), emptyMap())).total)
    }

    @Test
    fun onlyAyahsInsideThePlanFillTheRing() {
        // 6 + 4 ayahs of Juz Amma, and 7 of Al-Fatihah that is not in the plan.
        val covered = covered(114 to 6, 113 to 4, 1 to 7)
        val ring = assertNotNull(ProgressBuilder.ringOf(juzAmma, covered, covered(114 to 2)))
        assertEquals(10, ring.covered)
        assertEquals(2, ring.learned)
        val hero = ProgressBuilder.hero(coveredAyahs = 17, learnedAyahs = 2, learnedSurahs = 0, learningSurahs = 0, plan = ring)
        // 10 of 564 is 1.77%: the ring says 2, the whole Quran keeps its own numbers for the line under it.
        assertEquals(2, hero.percent)
        assertEquals(10, hero.ringCovered)
        assertEquals(564, hero.ringTotal)
        assertEquals(17, hero.coveredAyahs)
        assertEquals(6236, hero.totalAyahs)
        assertEquals(10f / 564, hero.fraction)
        assertEquals(HifzScope.JUZ_30, hero.planScope)
    }

    @Test
    fun withoutAPlanTheRingStaysOnTheWholeQuran() {
        assertNull(ProgressBuilder.ringOf(null, covered(1 to 7), emptyMap()))
        val hero = ProgressBuilder.hero(coveredAyahs = 623, learnedAyahs = 0, learnedSurahs = 0, learningSurahs = 0)
        assertNull(hero.planScope)
        assertEquals(623, hero.ringCovered)
        assertEquals(6236, hero.ringTotal)
        assertEquals(10, hero.percent)
    }

    @Test
    fun aPlanWithNoSurahsFallsBackToTheWholeQuran() {
        assertNull(ProgressBuilder.ringOf(Fx.plan(emptyList(), daily = 3, started = 20_000), emptyMap(), emptyMap()))
    }

    @Test
    fun percentIsNeverBelowOneUnlessNothingIsCovered() {
        assertEquals(0, ProgressBuilder.percentOf(0, 564))
        assertEquals(1, ProgressBuilder.percentOf(1, 6236)) // 0.016%
        assertEquals(1, ProgressBuilder.percentOf(2, 564)) // 0.35%
        assertEquals(2, ProgressBuilder.percentOf(10, 564)) // 1.77%
        assertEquals(50, ProgressBuilder.percentOf(282, 564))
        assertEquals(99, ProgressBuilder.percentOf(563, 564)) // 99.8% is not "done"
        assertEquals(100, ProgressBuilder.percentOf(564, 564))
        assertEquals(0, ProgressBuilder.percentOf(5, 0))
    }

    @Test
    fun theSmallRingOfTheHomeScreenFollowsThePlanToo() {
        val forecast = PlanForecast(
            totalAyahs = 564, remainingAyahs = 554, dailyNewAyahs = 3, finished = false,
            finishEpochDay = 20_200, targetEpochDay = null, requiredPerDay = null, behindAyahs = 0,
        )
        assertEquals(10f / 564, HomeRows.overallProgress(mapOf(114 to 6, 113 to 4, 1 to 7), forecast))
        // No plan: the whole Quran as before.
        assertEquals(17f / 6236, HomeRows.overallProgress(mapOf(114 to 6, 113 to 4, 1 to 7), null))
    }

    @Test
    fun thePageIsFourBlocksOnceThereIsProgress() {
        assertEquals(
            listOf(ProgressBlock.PlanAndStreak, ProgressBlock.Week, ProgressBlock.HardPlaces, ProgressBlock.QuranMap),
            ProgressBuilder.blocks(ProgressUi(phase = ProgressPhase.Content)),
        )
        assertTrue(ProgressBuilder.blocks(ProgressUi(phase = ProgressPhase.Empty)).isEmpty())
        assertTrue(ProgressBuilder.blocks(ProgressUi(phase = ProgressPhase.Loading)).isEmpty())
    }

    @Test
    fun theWeekIsTheLastSevenDaysEndingToday() {
        val days = (1..30).map { ActivityDayUi(epochDay = 20_000 + it, learned = it, reviewed = 0) }
        val week = ProgressBuilder.week(ActivityUi(days))
        assertEquals(7, week.days.size)
        assertEquals(20_024, week.days.first().epochDay)
        assertEquals(20_030, week.days.last().epochDay)
        assertEquals(5, ProgressBuilder.week(ActivityUi(days.take(5))).days.size)
    }
}
