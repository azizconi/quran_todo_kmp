package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry

/**
 * How the plan goes. [remainingAyahs] are the uncovered ayahs of the surahs of the plan.
 *
 * - [finishEpochDay]: with the daily amount: `start + ceil(remaining / dailyNewAyahs)` where `start` is today, or tomorrow if
 *   today's new passage is already learned. Null when the plan is [finished].
 * - With a target day: [requiredPerDay] = `ceil(remaining / days to the target)` (at least one day) and [behindAyahs]: how far
 *   behind the straight line from the start of the plan to the target the person is (0 when on time).
 * - On a day inside a pause (task F2b) the counting starts on the first day after it: no lessons happen before that. The target and
 *   start days of the plan were moved by the pause already, so the pause is not lateness.
 */
data class PlanForecast(
    val totalAyahs: Int,
    val remainingAyahs: Int,
    val dailyNewAyahs: Int,
    val finished: Boolean,
    val finishEpochDay: Int?,
    val targetEpochDay: Int?,
    val requiredPerDay: Int?,
    val behindAyahs: Int,
)

object Forecast {
    /**
     * [newLogSinceStart] are the NEW records of the review log from the day the plan started (they say how much was learned
     * since then, which the covered ayahs alone cannot: ayahs known before the plan are covered too).
     *
     * [pause] is the pause that [today] is inside of, if any. [learnedFromDay] is the day the records of [newLogSinceStart] are counted
     * from: the day the plan really started, which is earlier than `plan.startedEpochDay` when pauses moved the calendar of the plan
     * (the lessons of those first days must stay in the count); it defaults to `plan.startedEpochDay`.
     */
    fun compute(
        plan: HifzPlan,
        coverage: Coverage,
        newLogSinceStart: List<ReviewLogEntry>,
        today: Int,
        pause: PlanPause? = null,
        learnedFromDay: Int = plan.startedEpochDay,
    ): PlanForecast {
        val remaining = coverage.remaining(plan)
        val total = coverage.total(plan)
        val daily = plan.dailyNewAyahs.coerceAtLeast(1)
        val activePause = pause?.takeIf { today in it }
        val paused = activePause != null
        // Inside a pause nothing is learned before the day it ends: count from the first day with a lesson.
        val effectiveToday = activePause?.resumeEpochDay ?: today
        val newDoneToday = newLogSinceStart.any { it.kind == ReviewKind.NEW && it.epochDay == today }

        val finish = if (remaining == 0) {
            null
        } else {
            val start = if (paused) effectiveToday else if (newDoneToday) today + 1 else today
            start + (remaining + daily - 1) / daily
        }

        val target = plan.targetEpochDay
        var required: Int? = null
        var behind = 0
        if (target != null) {
            val daysLeft = (target - effectiveToday).coerceAtLeast(1)
            required = (remaining + daysLeft - 1) / daysLeft
            val learnedSinceStart = newLogSinceStart
                .filter { it.kind == ReviewKind.NEW && it.epochDay >= learnedFromDay && it.surah in plan.surahOrder }
                .sumOf { it.endAyah - it.startAyah + 1 }
            val remainingAtStart = remaining + learnedSinceStart
            val span = target - plan.startedEpochDay
            val elapsed = (effectiveToday - plan.startedEpochDay).coerceAtLeast(0)
            val expected = if (span <= 0 || elapsed >= span) {
                remainingAtStart
            } else {
                (remainingAtStart.toLong() * elapsed / span).toInt()
            }
            behind = (expected - learnedSinceStart).coerceIn(0, remaining)
        }
        return PlanForecast(
            totalAyahs = total,
            remainingAyahs = remaining,
            dailyNewAyahs = plan.dailyNewAyahs,
            finished = remaining == 0,
            finishEpochDay = finish,
            targetEpochDay = target,
            requiredPerDay = required,
            behindAyahs = behind,
        )
    }
}

object Streak {
    /**
     * Days in a row with at least one record in the review log, ending today or yesterday (the person has not studied
     * today yet - the streak is alive). [daysWithRecords] may be in any order and contain duplicates.
     *
     * The days of a pause ([pauses], task F2b) are a bridge: a day of a pause without a record neither adds to the streak nor breaks
     * it. Studied on days 1-3, a pause on 4-6, today is 7 and nothing is done yet: the streak is 3 and alive; after a lesson on day 7
     * it is 4. A day of a pause with a record (the pause was set after the lesson of that day) is a day of study like any other.
     */
    fun compute(daysWithRecords: Collection<Int>, today: Int, pauses: List<PlanPause> = emptyList()): Int {
        val days = daysWithRecords.toHashSet()
        fun bridge(day: Int) = pauses.any { day in it }
        // Today without a record is "not yet" (the streak is alive), whether it is a day of a pause or not.
        var day = if (today in days) today else today - 1
        var count = 0
        while (true) {
            when {
                day in days -> count++
                bridge(day) -> Unit
                else -> return count
            }
            day--
        }
    }

    /**
     * The longest run of study days in [daysWithRecords] ever (task U17), with the same bridge over [pauses] as [compute]: a gap
     * made only of pause days neither adds to the run nor breaks it. 0 for an empty log.
     */
    fun best(daysWithRecords: Collection<Int>, pauses: List<PlanPause> = emptyList()): Int {
        val days = daysWithRecords.toSortedSet().toList()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            val bridged = (days[i - 1] + 1 until days[i]).all { gap -> pauses.any { gap in it } }
            run = if (bridged) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
