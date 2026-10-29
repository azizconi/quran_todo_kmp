package tj.app.quran_todo.common.reminder

import android.content.Context
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Brings the alarms in line with the stored reminder settings (`UiPrefs.reminderEnabled` / `reminderMinuteOfDay`). It is the single
 * entry point of the scheduling and can be called from anywhere and as often as needed: the app at start, the settings when the
 * person changes something, and [ReminderSystemReceiver] after a boot, an app update, or a clock, zone or language change.
 *
 * - On: the channel exists, the daily alarm is planned for the next reminder time.
 * - Off: both alarms (daily and snooze) are cancelled.
 *
 * The result does not depend on what was planned before, so a repeated call is harmless.
 */
internal object ReminderSync {
    fun sync(context: Context) {
        val settings = UiPrefs.readReminder(context)
        ReminderAlarms.cancelLegacy(context)
        if (settings.enabled) {
            ReminderChannel.ensure(context)
            ReminderAlarms.scheduleDaily(context, settings.minuteOfDay)
        } else {
            ReminderAlarms.cancelDaily(context)
            ReminderAlarms.cancelSnooze(context)
        }
    }
}
