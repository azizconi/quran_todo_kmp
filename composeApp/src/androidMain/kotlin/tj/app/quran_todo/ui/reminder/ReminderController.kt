package tj.app.quran_todo.ui.reminder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.reminder.ReminderChannel
import tj.app.quran_todo.common.reminder.ReminderSync
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Turning the reminder on and off and moving its time: stores the value in [UiPrefs], plans the alarm ([ReminderSync]) and counts
 * the event. The settings screen and the import of a copy go through it.
 */
internal class ReminderController(private val context: Context, private val prefs: UiPrefs) {
    /**
     * Turns the reminder on or off. The notification permission is not asked here: the screen asks it before it turns the reminder
     * on. [track] is false for an import, which is not a choice the person made on this screen.
     */
    fun setEnabled(enabled: Boolean, track: Boolean = true) {
        prefs.setReminderEnabled(enabled)
        ReminderSync.sync(context)
        if (enabled && track) logEnabled()
    }

    /** Moves the time of the reminder to [minuteOfDay] (minutes after midnight). A reminder that is on is planned again. */
    fun setMinuteOfDay(minuteOfDay: Int, track: Boolean = true) {
        prefs.setReminderMinuteOfDay(minuteOfDay)
        ReminderSync.sync(context)
        if (prefs.reminderEnabled.value && track) logEnabled()
    }

    private fun logEnabled() {
        AppTelemetry.logEvent("reminder_enabled", mapOf("hour" to (prefs.reminderMinuteOfDay.value / 60).toString()))
    }
}

/** What the system allows for the reminder, and the way to its settings. */
internal object ReminderPermission {
    /** Android 13 and later ask for the permission at run time, and it is not given (yet). */
    fun needsRequest(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Notifications of the app, or the channel of the reminder, are switched off in the system: the reminder cannot come. */
    fun isBlocked(context: Context): Boolean =
        !NotificationManagerCompat.from(context).areNotificationsEnabled() || ReminderChannel.isBlocked(context)

    /** Opens the notification settings of the app (before Android 8: the page of the app). False when the phone has neither. */
    fun openSystemSettings(context: Context): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

/** "20:00" on a 24-hour phone, "8:00 PM" on a 12-hour one. */
internal fun formatReminderTime(minuteOfDay: Int, is24Hour: Boolean): String {
    val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    val hour = minute / 60
    val mm = (minute % 60).toString().padStart(2, '0')
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return "$hour12:$mm ${if (hour < 12) "AM" else "PM"}"
}
