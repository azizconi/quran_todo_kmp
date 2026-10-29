package tj.app.quran_todo.common.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId

/**
 * The two alarms of the reminder, both one-shot (`setAndAllowWhileIdle`, no exact-alarm permission):
 *
 * - the daily one: at the next reminder time; when it fires, [ReminderReceiver] plans the one for the next day;
 * - the snooze one: one hour after "In an hour" was pressed; it does not touch the daily one.
 *
 * The system keeps no alarm over a reboot, so [ReminderSync] plans the daily one again after boot, an app update and a clock
 * or zone change, and at every app start.
 */
internal object ReminderAlarms {
    /** The action of the daily alarm. */
    const val ACTION_DAILY = "tj.app.quran_todo.reminder.DAILY"

    /** The action of the snooze alarm. */
    const val ACTION_SNOOZE_FIRE = "tj.app.quran_todo.reminder.SNOOZE_FIRE"

    private const val REQUEST_DAILY = 9107
    private const val REQUEST_SNOOZE = 9109

    // The alarm of the previous implementation (before B4): a repeating alarm with this action and request code.
    private const val LEGACY_ACTION = "tj.app.quran_todo.REMIND_DAILY"
    private const val LEGACY_REQUEST = 9107

    /** Plans the daily alarm for the next reminder time, replacing the earlier one. Returns the planned moment. */
    fun scheduleDaily(
        context: Context,
        minuteOfDay: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val at = ReminderTime.nextTriggerMillis(nowMillis, minuteOfDay, zone)
        set(context, REQUEST_DAILY, ACTION_DAILY, at)
        return at
    }

    fun cancelDaily(context: Context) = cancel(context, REQUEST_DAILY, ACTION_DAILY)

    /** Plans the snooze alarm one hour from [nowMillis]. Returns the planned moment. */
    fun snooze(context: Context, nowMillis: Long = System.currentTimeMillis()): Long {
        val at = ReminderTime.snoozeMillis(nowMillis)
        set(context, REQUEST_SNOOZE, ACTION_SNOOZE_FIRE, at)
        return at
    }

    fun cancelSnooze(context: Context) = cancel(context, REQUEST_SNOOZE, ACTION_SNOOZE_FIRE)

    /** Removes the repeating alarm that an older version of the app may have left. */
    fun cancelLegacy(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).setAction(LEGACY_ACTION)
        PendingIntent.getBroadcast(context, LEGACY_REQUEST, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { pending ->
                alarms.cancel(pending)
                pending.cancel()
            }
    }

    private fun intentFor(context: Context, action: String) =
        Intent(context, ReminderReceiver::class.java).setAction(action)

    private fun set(context: Context, request: Int, action: String, atMillis: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context, request, intentFor(context, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }

    private fun cancel(context: Context, request: Int, action: String) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context, request, intentFor(context, action), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }
}
