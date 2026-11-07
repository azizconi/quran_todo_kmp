package tj.app.quran_todo.ui.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressMotionTest {
    @Test
    fun aLongWayIsClimbedInFewEasedStridesAndLandsOnTheTarget() {
        val ladder = counterLadder(from = 0, to = 66)
        assertTrue(ladder.size in 2..12, "steps: $ladder")
        assertEquals(66, ladder.last())
        assertEquals(ladder.sorted(), ladder)
        assertEquals(ladder.distinct(), ladder)
        // Eased out: the first stride is bigger than an even share.
        assertTrue(ladder.first() > 66 / ladder.size)
    }

    @Test
    fun aShortWayIsShownDigitByDigit() {
        assertEquals(listOf(1, 2, 3), counterLadder(0, 3))
        assertEquals(listOf(1), counterLadder(0, 1))
        assertEquals(12, counterLadder(0, 12).size)
    }

    @Test
    fun theCounterCanAlsoCountDown() {
        assertEquals(listOf(4, 3), counterLadder(from = 5, to = 3))
        val ladder = counterLadder(from = 40, to = 5)
        assertEquals(5, ladder.last())
        assertEquals(ladder.sortedDescending(), ladder)
        assertTrue(ladder.all { it in 5..40 })
    }

    @Test
    fun nothingMovesWhenThereIsNothingToCount() {
        assertTrue(counterLadder(7, 7).isEmpty())
    }

    @Test
    fun theWaveRunsOverTheCellsInOrder() {
        // Before its turn a cell is dark; later cells light up later.
        assertEquals(0f, waveIntensity(0f, index = 0))
        assertEquals(0f, waveIntensity(0f, index = 50))
        val first = waveIntensity(WaveGlintMillis / 2, index = 0)
        assertTrue(first > 0.99f, "peak of the first cell: $first")
        assertEquals(0f, waveIntensity(WaveGlintMillis / 2, index = 80))
        val tenth = waveIntensity(10 * WaveDelayMillis + WaveGlintMillis / 2, index = 10)
        assertTrue(tenth > 0.99f, "peak of the tenth cell: $tenth")
    }

    @Test
    fun everyCellIsDarkAgainWhenTheWaveEnds() {
        val total = waveTotalMillis(114)
        for (index in 0 until 114) {
            assertEquals(0f, waveIntensity(total, index), "cell $index")
        }
        assertEquals(113 * WaveDelayMillis + WaveGlintMillis, total)
    }
}
