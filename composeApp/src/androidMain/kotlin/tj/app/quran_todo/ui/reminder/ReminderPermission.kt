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
import tj.app.quran_todo.common.reminder.ReminderChannel
import tj.app.quran_todo.common.reminder.ReminderSync
import tj.app.quran_todo.ui.state.UiPrefs

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

/** The reminder of this phone is planned with AlarmManager ([ReminderSync]). */
internal class AndroidReminderPlanner(private val context: Context) : ReminderPlanner {
    override fun sync() = ReminderSync.sync(context.applicationContext)
}

/** The controller with the Android planner, so the screens keep building it from a context. */
internal fun ReminderController(context: Context, prefs: UiPrefs): ReminderController =
    ReminderController(AndroidReminderPlanner(context), prefs)
