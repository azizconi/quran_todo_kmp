package tj.app.quran_todo.ui.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonTimingTest {
    private var clock = 0L
    private val watch = LessonStopwatch { clock }

    @Test
    fun countsOnlyWhileTheScreenIsInTheForeground() {
        watch.resume(100)
        clock += 60_000
        watch.pause()
        clock += 10 * 60_000 // in the background: not counted
        watch.resume(100)
        clock += 30_000
        assertEquals(90_000L, watch.elapsedMs())
        watch.pause()
        assertEquals(90_000L, watch.elapsedMs())
    }

    @Test
    fun aSecondResumeWhileRunningChangesNothing() {
        watch.resume(100)
        clock += 10_000
        watch.resume(100)
        clock += 10_000
        assertEquals(20_000L, watch.elapsedMs())
    }

    @Test
    fun aHalfDoneLessonAddsUpOnTheSameDay() {
        watch.resume(100)
        clock += 120_000
        watch.pause()
        // A new view model: the saved total is put back.
        val later = LessonStopwatch { clock }
        later.restore(watch.day, watch.accumulatedMs)
        clock += 3_600_000
        later.resume(100)
        clock += 60_000
        assertEquals(180_000L, later.elapsedMs())
    }

    @Test
    fun anotherDayStartsFromZero() {
        watch.resume(100)
        clock += 120_000
        watch.pause()
        val next = LessonStopwatch { clock }
        next.restore(watch.day, watch.accumulatedMs)
        next.resume(101)
        clock += 5_000
        assertEquals(5_000L, next.elapsedMs())
    }

    @Test
    fun clearForgetsEverything() {
        watch.resume(100)
        clock += 5_000
        watch.clear()
        assertEquals(0L, watch.elapsedMs())
        assertTrue(!watch.isRunning)
    }

    @Test
    fun lineNeedsFiveDurations() {
        assertNull(LessonDurations.medianMinutes(emptyList()))
        assertNull(LessonDurations.medianMinutes(listOf(600, 600, 600, 600)))
        assertEquals(10, LessonDurations.medianMinutes(listOf(600, 600, 600, 600, 600)))
    }

    @Test
    fun medianIsRoundedToWholeMinutesWithMinimumOne() {
        // 10 s, 20 s, 30 s, 40 s, 50 s: median 30 s rounds to 1 minute (never 0).
        assertEquals(1, LessonDurations.medianMinutes(listOf(10, 20, 30, 40, 50)))
        // 5 values: median 8 min 29 s -> 8; one long outlier does not move it.
        assertEquals(8, LessonDurations.medianMinutes(listOf(300, 480, 509, 600, 7200)))
        // 6 values: mean of the two middle ones (7 min and 8 min) -> 7.5 -> 8.
        assertEquals(8, LessonDurations.medianMinutes(listOf(60, 120, 420, 480, 900, 1200)))
    }

    @Test
    fun onlyTheLastSevenAreKept() {
        var stored = emptyList<Int>()
        for (i in 1..9) stored = LessonDurations.append(stored, i * 60)
        assertEquals(listOf(180, 240, 300, 360, 420, 480, 540), stored)
    }

    @Test
    fun telemetryMinutesAreAtLeastOne() {
        assertEquals(1, LessonDurations.minutesOf(5_000))
        assertEquals(3, LessonDurations.minutesOf(170_000))
    }
}
