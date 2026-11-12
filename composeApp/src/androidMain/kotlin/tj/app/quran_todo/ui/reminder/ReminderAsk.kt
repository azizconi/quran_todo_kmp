package tj.app.quran_todo.ui.reminder

/** The rules of the question "Remind you about tomorrow's lesson?" on the lesson summary (task U2). Pure. */
internal object ReminderAsk {
    /**
     * The card shows on a completed lesson when the reminder is off and the person has not answered before. It is not tied to a
     * calendar day: the first completed lesson where this holds shows it.
     */
    fun shouldShow(lessonComplete: Boolean, reminderEnabled: Boolean, alreadyAnswered: Boolean): Boolean =
        lessonComplete && !reminderEnabled && !alreadyAnswered

    /** [minuteOfDay] (minutes after midnight) rounded to the nearest half hour; 23:50 becomes 00:00. */
    fun proposedMinute(minuteOfDay: Int): Int = ((minuteOfDay + 15) / 30 * 30) % (24 * 60)
}
