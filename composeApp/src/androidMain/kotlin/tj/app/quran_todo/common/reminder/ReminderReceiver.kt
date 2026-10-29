package tj.app.quran_todo.common.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * The receiver of the app's own reminder broadcasts (not exported, no intent filter: only our `PendingIntent`s reach it).
 *
 * - [ReminderAlarms.ACTION_DAILY]: the daily alarm. The alarm of the next day is planned first, synchronously, so that nothing
 *   that goes wrong while looking at the lesson can lose the following days; then the lesson is looked at and the reminder
 *   shown if there is something to say.
 * - [ReminderAlarms.ACTION_SNOOZE_FIRE]: the snooze alarm, one hour after "In an hour". Looks at the lesson again (it may be done
 *   by now) and shows the reminder; it leaves the daily alarm alone.
 * - [ACTION_SNOOZE]: the "In an hour" button of the notification. Takes the notification down and plans the snooze alarm; the
 *   alarm of tomorrow stays.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            ReminderAlarms.ACTION_DAILY -> {
                val settings = UiPrefs.readReminder(app)
                if (!settings.enabled) return
                ReminderAlarms.scheduleDaily(app, settings.minuteOfDay)
                showInBackground(app)
            }
            ReminderAlarms.ACTION_SNOOZE_FIRE -> {
                if (UiPrefs.readReminder(app).enabled) showInBackground(app)
            }
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(app).cancel(ReminderNotifier.NOTIFICATION_ID)
                ReminderAlarms.snooze(app)
                AppTelemetry.logEvent("reminder_snoozed")
            }
        }
    }

    /** Looks at the lesson off the main thread, at most [SHOW_TIMEOUT_MILLIS], and keeps the broadcast alive until it is done. */
    private fun showInBackground(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(SHOW_TIMEOUT_MILLIS) { ReminderNotifier.showIfDue(context) }
            } catch (_: TimeoutCancellationException) {
                AppTelemetry.logEvent("reminder_timeout")
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "reminder_failed")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** The "In an hour" button of the notification. */
        const val ACTION_SNOOZE = "tj.app.quran_todo.reminder.SNOOZE"

        private const val SHOW_TIMEOUT_MILLIS = 8_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
