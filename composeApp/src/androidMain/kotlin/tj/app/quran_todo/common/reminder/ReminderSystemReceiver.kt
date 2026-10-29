package tj.app.quran_todo.common.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Plans the reminder again after something the alarms do not survive or follow on their own:
 *
 * - `BOOT_COMPLETED`: the system forgets all alarms when the phone restarts.
 * - `MY_PACKAGE_REPLACED`: an update of the app removes its alarms.
 * - `TIME_SET` and `TIMEZONE_CHANGED`: the reminder time is a wall-clock time of the phone, so the moment is computed again.
 * - `LOCALE_CHANGED`: the name of the notification channel follows the language (the one of the app when the person never chose,
 *   otherwise the phone's).
 *
 * Declared with `exported="false"`: these are protected system broadcasts, which the system sends to the receiver whatever its
 * export flag, while no other app can address it.
 */
class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> ReminderSync.sync(app)
            Intent.ACTION_LOCALE_CHANGED -> ReminderChannel.renameIfExists(app)
        }
    }
}
