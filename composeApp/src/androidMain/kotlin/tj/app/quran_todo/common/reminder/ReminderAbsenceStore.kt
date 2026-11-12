package tj.app.quran_todo.common.reminder

import android.content.Context
import tj.app.quran_todo.hifz.EpochDays

/**
 * Keeps the last day the app was in the foreground (task U18), in its own preferences file: no backup, no schema. Read by the
 * alarm receiver, which may run in a process that has no UI at all.
 */
internal object ReminderAbsenceStore {
    private const val PREFS = "reminder_absence"
    private const val KEY_LAST_FOREGROUND_DAY = "last_foreground_day"

    /** Call when the app comes to the foreground. */
    fun recordForeground(context: Context, today: Int = EpochDays.now()) {
        save(context, ReminderAbsence.onForeground(today))
    }

    /**
     * The days the person has been away. The first call of a version that never recorded a day starts the count from today, so an
     * existing user is not treated as long gone.
     */
    fun daysAway(context: Context, today: Int = EpochDays.now()): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = if (prefs.contains(KEY_LAST_FOREGROUND_DAY)) prefs.getInt(KEY_LAST_FOREGROUND_DAY, today) else null
        if (last == null) save(context, today)
        return ReminderAbsence.daysAway(last, today)
    }

    /** The plan is paused: the days of the pause are not absence, so the count restarts from today. */
    fun recordPauseDay(context: Context, today: Int = EpochDays.now()) = save(context, today)

    private fun save(context: Context, day: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_LAST_FOREGROUND_DAY, day).apply()
    }
}
