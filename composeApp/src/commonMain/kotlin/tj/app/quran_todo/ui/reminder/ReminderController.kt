package tj.app.quran_todo.ui.reminder

import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Plans (or cancels) the daily reminder from the stored settings ([UiPrefs.reminderEnabled], [UiPrefs.reminderMinuteOfDay]). A repeated
 * call is harmless: the result does not depend on what was planned before.
 *
 * Android: `AndroidReminderPlanner` (AlarmManager, the notification channel). iOS (S2): remove the pending `UNNotificationRequest`s of
 * the app, and when the reminder is on add a repeating `UNCalendarNotificationTrigger` at the stored time of day (the text is made
 * from the lesson by the Swift side when the notification is scheduled or by a notification service extension).
 */
interface ReminderPlanner {
    fun sync()
}

/**
 * Turning the reminder on and off and moving its time: stores the value in [UiPrefs], plans the alarm ([ReminderSync]) and counts
 * the event. The settings screen and the import of a copy go through it.
 */
class ReminderController(private val planner: ReminderPlanner, private val prefs: UiPrefs) {
    /**
     * Turns the reminder on or off. The notification permission is not asked here: the screen asks it before it turns the reminder
     * on. [track] is false for an import, which is not a choice the person made on this screen.
     */
    fun setEnabled(enabled: Boolean, track: Boolean = true) {
        prefs.setReminderEnabled(enabled)
        planner.sync()
        if (enabled && track) logEnabled()
    }

    /** Moves the time of the reminder to [minuteOfDay] (minutes after midnight). A reminder that is on is planned again. */
    fun setMinuteOfDay(minuteOfDay: Int, track: Boolean = true) {
        prefs.setReminderMinuteOfDay(minuteOfDay)
        planner.sync()
        if (prefs.reminderEnabled.value && track) logEnabled()
    }

    private fun logEnabled() {
        AppTelemetry.logEvent("reminder_enabled", mapOf("hour" to (prefs.reminderMinuteOfDay.value / 60).toString()))
    }
}

/** "20:00" on a 24-hour phone, "8:00 PM" on a 12-hour one. */
fun formatReminderTime(minuteOfDay: Int, is24Hour: Boolean): String {
    val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    val hour = minute / 60
    val mm = (minute % 60).toString().padStart(2, '0')
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return "$hour12:$mm ${if (hour < 12) "AM" else "PM"}"
}
