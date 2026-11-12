package tj.app.quran_todo.ui.progress.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeatGridHitTest {
    // Three weeks, days 100..120; the last week holds only 118..120 (after today the rest is null).
    private val heat = HeatYear(
        year = 2026,
        weeks = listOf(
            (100..106).map { day(it) },
            (107..113).map { day(it) },
            (114..120).map { day(it) } ,
        ),
        max = 1,
        activeDays = 0,
    )

    private fun day(epoch: Int) = HeatDay(epoch, 0, 0, 0, 0, false)

    private val cell = 5f
    private val gap = 1f
    private val monthRow = 10f

    @Test
    fun exactHitPicksThatDay() {
        // week 1, weekday 2 -> 109; its centre is x = 6 + 2.5, y = 10 + 12 + 2.5
        assertEquals(109, HeatGridHit.nearestDay(heat, 8.5f, 24.5f, cell, gap, monthRow, 6f))
    }

    @Test
    fun nearMissPicksTheNearestDay() {
        // 3 px right of the centre of 109, still inside the tolerance
        assertEquals(109, HeatGridHit.nearestDay(heat, 11.5f, 24.5f, cell, gap, monthRow, 6f))
    }

    @Test
    fun farFromAnyDayPicksNothing() {
        assertNull(HeatGridHit.nearestDay(heat, 200f, 24f, cell, gap, monthRow, 6f))
        assertNull(HeatGridHit.nearestDay(heat, 8f, 0f, cell, gap, monthRow, 2f))
    }

    @Test
    fun stepStartsAtTheLatestDayAndStaysInTheYear() {
        assertEquals(120, HeatGridHit.step(heat, null, -1))
        assertEquals(101, HeatGridHit.step(heat, 100, 1))
        assertEquals(100, HeatGridHit.step(heat, 103, -7))
        assertEquals(120, HeatGridHit.step(heat, 118, 7))
        assertEquals(110, HeatGridHit.step(heat, 103, 7))
    }

    @Test
    fun stepOfAnEmptyYearIsNull() {
        assertNull(HeatGridHit.step(HeatYear(2026, emptyList(), 0, 0), null, 1))
    }
}
