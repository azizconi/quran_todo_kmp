package tj.app.quran_todo.ui.lesson

/**
 * The active time of a lesson (task U3): the time the lesson screen is in the foreground between its first open on a day and the
 * moment the summary appears. Pure: the clock is injected, the view model saves [day] and [accumulatedMs] on every pause so a
 * lesson that is picked up later the same day adds to the same total.
 *
 * - [resume] starts counting (the screen is resumed); a second call while counting changes nothing.
 * - [pause] stops counting and keeps what was counted (the app went to the background, or the screen was left).
 * - A resume on another day starts from zero: yesterday's half-done time does not belong to today's lesson.
 */
class LessonStopwatch(private val now: () -> Long) {
    var day: Int = NO_DAY
        private set
    var accumulatedMs: Long = 0L
        private set
    private var resumedAt: Long? = null

    /** Puts back what was saved by an earlier visit. Used only before counting starts. */
    fun restore(day: Int, accumulatedMs: Long) {
        if (resumedAt != null) return
        this.day = day
        this.accumulatedMs = accumulatedMs.coerceAtLeast(0L)
    }

    fun resume(today: Int) {
        if (resumedAt != null) return
        if (today != day) {
            day = today
            accumulatedMs = 0L
        }
        resumedAt = now()
    }

    fun pause() {
        val started = resumedAt ?: return
        accumulatedMs += (now() - started).coerceAtLeast(0L)
        resumedAt = null
    }

    /** What has been counted so far, including the running stretch. */
    fun elapsedMs(): Long {
        val started = resumedAt ?: return accumulatedMs
        return accumulatedMs + (now() - started).coerceAtLeast(0L)
    }

    val isRunning: Boolean get() = resumedAt != null

    /** Stops and forgets everything (the lesson is over, or there was nothing to measure). */
    fun clear() {
        resumedAt = null
        accumulatedMs = 0L
        day = NO_DAY
    }

    companion object {
        const val NO_DAY = Int.MIN_VALUE
    }
}

/** The last durations of completed lessons, kept in seconds, and the line on the summary that comes from them. */
object LessonDurations {
    /** How many durations are kept. */
    const val KEEP = 7

    /** The line "Your lesson takes about N minutes" needs at least this many durations. */
    const val MIN_FOR_LINE = 5

    /** [seconds] is added at the end; only the last [KEEP] stay. */
    fun append(stored: List<Int>, seconds: Int): List<Int> = (stored + seconds.coerceAtLeast(0)).takeLast(KEEP)

    /** The median as whole minutes (at least 1), or null while fewer than [MIN_FOR_LINE] durations are stored. */
    fun medianMinutes(stored: List<Int>): Int? {
        if (stored.size < MIN_FOR_LINE) return null
        val sorted = stored.sorted()
        val middle = sorted.size / 2
        val medianSeconds = if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
        return Math.round(medianSeconds / 60.0).toInt().coerceAtLeast(1)
    }

    /** Minutes for the telemetry event: rounded, at least 1. */
    fun minutesOf(ms: Long): Int = Math.round(ms / 60_000.0).toInt().coerceAtLeast(1)
}
