package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.PlanPause

/** What the lesson screen needs to decide on "Welcome back" (task U17). */
data class ReturnInfo(
    /** Full calendar days without a lesson between the last day of study and today; the days of a pause do not count. */
    val missedDays: Int,
    /** The last day before today with a record of study; null when there is none. */
    val lastStudyDay: Int?,
    /** Today already has a record of study (the lesson was started). */
    val startedToday: Boolean,
    /** The longest run of study days in the log (pause days bridge it); 0 when the log is empty. */
    val bestStreak: Int,
)

/**
 * "Welcome back" (task U17): pure rules. A day of study is a day with a record in the review log that is not a LISTEN record
 * (the same days as the streak). The missed days are the days strictly between the last day of study and today that are not
 * inside a pause, so the pause never counts as an absence (the same bridge as [Streak]).
 */
object WelcomeBack {
    /** The screen is shown from this many missed days. */
    const val MIN_MISSED_DAYS = 2

    fun info(daysWithRecords: Collection<Int>, today: Int, pauses: List<PlanPause>): ReturnInfo {
        val days = daysWithRecords.toHashSet()
        val last = days.filter { it < today }.maxOrNull()
        val missed = if (last == null) 0 else (last + 1 until today).count { day -> pauses.none { day in it } }
        return ReturnInfo(
            missedDays = missed,
            lastStudyDay = last,
            startedToday = today in days,
            bestStreak = Streak.best(days, pauses),
        )
    }

    /**
     * Should the screen be shown: [missedDays] reaches [MIN_MISSED_DAYS], today is not started and the screen was not answered
     * for this return. An answer made on [answeredDay] belongs to the return when it is after the last day of study; once the
     * person studies again ([ReturnInfo.lastStudyDay] reaches the answer) a new absence asks again.
     */
    fun shouldShow(info: ReturnInfo, answeredDay: Int?): Boolean {
        if (info.startedToday || info.missedDays < MIN_MISSED_DAYS) return false
        val last = info.lastStudyDay ?: return false
        return answeredDay == null || answeredDay <= last
    }
}

/**
 * "A lighter week" (task U17): for [DAYS] days the daily amount of new ayahs is half of the plan's pace (rounded down, at least 1).
 * Only the day until which it holds is stored (`hifz_meta`, [META_KEY]); the plan itself is never rewritten.
 */
object LighterWeek {
    const val DAYS = 7
    const val META_KEY = "lighter_until"

    /** The last day of a lighter week chosen on [today]: today and the 6 days after it. */
    fun untilFor(today: Int): Int = today + DAYS - 1

    fun isActive(until: Int?, today: Int): Boolean = until != null && today <= until

    /** Half of [pace], rounded down, never below 1. */
    fun halfPace(pace: Int): Int = (pace / 2).coerceAtLeast(1)

    /** The daily amount of new ayahs that holds on [today]. */
    fun effectivePace(pace: Int, until: Int?, today: Int): Int = if (isActive(until, today)) halfPace(pace) else pace

    /** The plan as the planner of the new passage should see it on [today]. */
    fun applied(plan: HifzPlan, until: Int?, today: Int): HifzPlan {
        val pace = effectivePace(plan.dailyNewAyahs, until, today)
        return if (pace == plan.dailyNewAyahs) plan else plan.copy(dailyNewAyahs = pace)
    }
}
