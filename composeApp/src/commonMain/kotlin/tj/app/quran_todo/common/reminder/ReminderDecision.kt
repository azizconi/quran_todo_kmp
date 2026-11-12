package tj.app.quran_todo.common.reminder

/** What the daily reminder does when its alarm fires (task U18). */
sealed interface ReminderDecision {
    /** Show the reminder with the usual text. */
    data object ShowNormal : ReminderDecision

    /** Show the reminder with the gentle "coming back is easy" text. */
    data object ShowGentle : ReminderDecision

    /** Stay silent today. The next alarm is planned all the same. */
    data object Skip : ReminderDecision
}

/**
 * The rule of the reminder by how long the person has been away, in full calendar days since the last day the app was in the
 * foreground (0 when it was opened today):
 *
 * - 0..2 days: the usual reminder;
 * - 3..6: every day, with the gentle text;
 * - 7..20: the gentle text on every third day of absence (7, 10, 13, 16, 19);
 * - 21 and more: nothing, until the app is opened again (which makes the count 0).
 */
fun reminderDecision(daysAway: Int): ReminderDecision = when {
    daysAway < GENTLE_FROM -> ReminderDecision.ShowNormal
    daysAway < SPARSE_FROM -> ReminderDecision.ShowGentle
    daysAway < SILENT_FROM -> if ((daysAway - SPARSE_FROM) % SPARSE_STEP == 0) ReminderDecision.ShowGentle else ReminderDecision.Skip
    else -> ReminderDecision.Skip
}

private const val GENTLE_FROM = 3
private const val SPARSE_FROM = 7
private const val SILENT_FROM = 21
private const val SPARSE_STEP = 3

/**
 * The last day (epoch day) the app was in the foreground, and the days away counted from it. Pure: `ReminderAbsenceStore` keeps
 * the value in the preferences.
 */
object ReminderAbsence {
    /** Full days from [lastForegroundDay] to [today]; 0 when nothing is recorded yet or the clock went back. */
    fun daysAway(lastForegroundDay: Int?, today: Int): Int =
        if (lastForegroundDay == null) 0 else (today - lastForegroundDay).coerceAtLeast(0)

    /** The day to store when the app comes to the foreground: today, which resets the count to 0. */
    fun onForeground(today: Int): Int = today
}
