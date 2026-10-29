package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry

/**
 * How the plan goes. [remainingAyahs] are the uncovered ayahs of the surahs of the plan.
 *
 * - [finishEpochDay]: with the daily amount: `start + ceil(remaining / dailyNewAyahs)` where `start` is today, or tomorrow if
 *   today's new passage is already learned. Null when the plan is [finished].
 * - With a target day: [requiredPerDay] = `ceil(remaining / days to the target)` (at least one day) and [behindAyahs]: how far
 *   behind the straight line from the start of the plan to the target the person is (0 when on time).
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
     */
    fun compute(
        plan: HifzPlan,
        coverage: Coverage,
        newLogSinceStart: List<ReviewLogEntry>,
        today: Int,
    ): PlanForecast {
        val remaining = coverage.remaining(plan)
        val total = coverage.total(plan)
        val daily = plan.dailyNewAyahs.coerceAtLeast(1)
        val newDoneToday = newLogSinceStart.any { it.kind == ReviewKind.NEW && it.epochDay == today }

        val finish = if (remaining == 0) {
            null
        } else {
            val start = if (newDoneToday) today + 1 else today
            start + (remaining + daily - 1) / daily
        }

        val target = plan.targetEpochDay
        var required: Int? = null
        var behind = 0
        if (target != null) {
            val daysLeft = (target - today).coerceAtLeast(1)
            required = (remaining + daysLeft - 1) / daysLeft
            val learnedSinceStart = newLogSinceStart
                .filter { it.kind == ReviewKind.NEW && it.epochDay >= plan.startedEpochDay && it.surah in plan.surahOrder }
                .sumOf { it.endAyah - it.startAyah + 1 }
            val remainingAtStart = remaining + learnedSinceStart
            val span = target - plan.startedEpochDay
            val elapsed = (today - plan.startedEpochDay).coerceAtLeast(0)
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
     */
    fun compute(daysWithRecords: Collection<Int>, today: Int): Int {
        val days = daysWithRecords.toHashSet()
        var day = when {
            today in days -> today
            today - 1 in days -> today - 1
            else -> return 0
        }
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }
}
