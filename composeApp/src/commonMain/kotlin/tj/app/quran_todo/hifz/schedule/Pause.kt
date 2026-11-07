package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PlanPause

/**
 * What a pause or a resume does: the new list of pauses to store and by how many days the calendar of the plan moves
 * ([shiftDays] is negative when the pause got shorter).
 */
data class PauseStep(
    val pauses: List<PlanPause>,
    val shiftDays: Int,
    /** The pause that stands after the step; null when the step removed it. */
    val pause: PlanPause?,
)

/**
 * The pause of the plan (task F2b): pure functions of the stored list of pauses and of the calendar shift.
 *
 * A pause stops the lessons for 1..[MAX_DAYS] days counted from today. The calendar of the plan moves by the same number of days
 * ([shiftPassages], [shiftPlan]): the due days of the ladder, `lastReviewed` of every passage that has one, the target day and the
 * start day of the plan. So the lesson that comes back is the very lesson that was waiting, nothing piles up as a debt, and the
 * forecast of a plan with a target day does not count the pause as lateness. The streak bridges the pause days ([Streak]).
 *
 * The list of pauses lives in `hifz_meta` under [META_KEY] as text ([encode], [decode]): the current pause and the ones of the last
 * [HISTORY_DAYS] days (the streak needs them). The Room schema does not change, and the backup file carries `hifz_meta` as it is.
 */
object PlanPauses {
    /** The longest pause: the end of a pause is never further than today + 13. */
    const val MAX_DAYS = 14

    /** Pauses that ended longer ago than this are forgotten (a streak that long does not need them). */
    const val HISTORY_DAYS = 400

    /** The key of the list in `hifz_meta`. */
    const val META_KEY = "plan_pauses"

    /**
     * The key in `hifz_meta` of the day the current plan really started, before pauses moved its calendar. Set by the first pause of
     * a plan, dropped when the start of the plan is set again. The forecast counts what was learned since this day (the moved start
     * day is later than the first lessons of the plan, and they must not drop out of the count).
     */
    const val BASE_START_KEY = "plan_start_base"

    /** `"from,until;from,until"`, oldest first; an empty list is the empty text. */
    fun encode(pauses: List<PlanPause>): String =
        pauses.sortedBy { it.fromEpochDay }.joinToString(";") { "${it.fromEpochDay},${it.untilEpochDay}" }

    /** The reverse of [encode]; a part that is not two numbers with `from <= until` is dropped, the rest is kept. Null or blank - empty. */
    fun decode(text: String?): List<PlanPause> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { part ->
            val numbers = part.split(',').map { it.trim().toIntOrNull() }
            if (numbers.size != 2) return@mapNotNull null
            val from = numbers[0] ?: return@mapNotNull null
            val until = numbers[1] ?: return@mapNotNull null
            if (until < from) null else PlanPause(from, until)
        }.sortedBy { it.fromEpochDay }
    }

    /** The pause that is on or still ahead of [today] (not over yet); null when none. The latest one if there are several. */
    fun active(pauses: List<PlanPause>, today: Int): PlanPause? =
        pauses.filter { it.untilEpochDay >= today }.maxByOrNull { it.fromEpochDay }

    /** The pause the day [today] is inside of: the lessons of that day are off. */
    fun covering(pauses: List<PlanPause>, today: Int): PlanPause? = pauses.firstOrNull { today in it }

    /** Forgets the pauses that ended more than [HISTORY_DAYS] days before [today]. */
    fun prune(pauses: List<PlanPause>, today: Int): List<PlanPause> =
        pauses.filter { it.untilEpochDay >= today - HISTORY_DAYS }

    /**
     * Pauses the plan for [days] (1..[MAX_DAYS], forced into it) from [today]: the pause ends on `today + days - 1`. A pause that is on
     * already is extended (or shortened) to that end and its start stays; the calendar moves only by the difference between the new
     * end and the old one.
     */
    fun start(pauses: List<PlanPause>, days: Int, today: Int): PauseStep {
        val length = days.coerceIn(1, MAX_DAYS)
        val end = today + length - 1
        val current = active(pauses, today)
        val from = if (current != null) minOf(current.fromEpochDay, today) else today
        val next = PlanPause(from, end)
        val shift = next.dayCount - (current?.dayCount ?: 0)
        val rest = if (current != null) pauses - current else pauses
        return PauseStep(prune(rest + next, today).sortedBy { it.fromEpochDay }, shift, next)
    }

    /**
     * "Continue now": the pause that is on ends yesterday (a pause that started today is removed whole) and the calendar moves back
     * by the days that were left. Null when there is no pause to end.
     */
    fun resume(pauses: List<PlanPause>, today: Int): PauseStep? {
        val current = active(pauses, today) ?: return null
        val rest = pauses - current
        val lastDay = today - 1
        return if (lastDay >= current.fromEpochDay) {
            val shortened = PlanPause(current.fromEpochDay, lastDay)
            PauseStep((rest + shortened).sortedBy { it.fromEpochDay }, -(current.untilEpochDay - lastDay), shortened)
        } else {
            PauseStep(rest.sortedBy { it.fromEpochDay }, -current.dayCount, null)
        }
    }

    /** Moves the due day of every ladder passage and `lastReviewed` of every passage that has one by [days]; a null stays null. */
    fun shiftPassages(passages: List<Passage>, days: Int): List<Passage> {
        if (days == 0) return passages
        return passages.map { passage ->
            passage.copy(
                nextDueEpochDay = passage.nextDueEpochDay?.plus(days),
                lastReviewedEpochDay = passage.lastReviewedEpochDay?.plus(days),
            )
        }
    }

    /** Moves the target day (if any) and the start day of the plan by [days]. */
    fun shiftPlan(plan: HifzPlan, days: Int): HifzPlan {
        if (days == 0) return plan
        return plan.copy(
            targetEpochDay = plan.targetEpochDay?.plus(days),
            startedEpochDay = plan.startedEpochDay + days,
        )
    }
}
