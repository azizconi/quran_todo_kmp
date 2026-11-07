package tj.app.quran_todo.common.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId

/**
 * The two alarms of the reminder, both one-shot and without the exact-alarm permission:
 *
 * - the daily one: at the next reminder time; when it fires, [ReminderReceiver] plans the one for the next day;
 * - the snooze one: one hour after "In an hour" was pressed; it does not touch the daily one.
 *
 * Each of them is set twice for the same moment, and the first of the two to come is taken ([claim]):
 *
 * - `setWindow` with a window of [WINDOW_MILLIS] (the shortest the system keeps without the permission): on time while the phone
 *   is not in deep Doze. An inexact alarm alone may come up to an hour late (its window is 75% of the time until it, at most an
 *   hour), so a reminder planned a day ahead for 20:00 could come at 20:55;
 * - `setAndAllowWhileIdle`: comes in deep Doze too, the one a sleeping phone gets.
 *
 * The system keeps no alarm over a reboot, so [ReminderSync] plans the daily one again after boot, an app update and a clock
 * or zone change, and at every app start.
 */
internal object ReminderAlarms {
    /** The action of the daily alarm. */
    const val ACTION_DAILY = "tj.app.quran_todo.reminder.DAILY"

    /** The action of the snooze alarm. */
    const val ACTION_SNOOZE_FIRE = "tj.app.quran_todo.reminder.SNOOZE_FIRE"

    /** The moment the alarm was planned for (millis), the same in both copies of it. */
    const val EXTRA_PLANNED_AT = "tj.app.quran_todo.reminder.PLANNED_AT"

    /** The window of the `setWindow` copy: from API 31 the system makes a shorter one 10 minutes anyway. */
    const val WINDOW_MILLIS: Long = 10L * 60L * 1000L

    private const val REQUEST_DAILY = 9107
    private const val REQUEST_DAILY_WINDOW = 9113
    private const val REQUEST_SNOOZE = 9109
    private const val REQUEST_SNOOZE_WINDOW = 9114

    // The alarm of the previous implementation (before B4): a repeating alarm with this action and request code.
    private const val LEGACY_ACTION = "tj.app.quran_todo.REMIND_DAILY"
    private const val LEGACY_REQUEST = 9107

    private const val STATE_PREFS = "reminder_alarm_state"
    private const val KEY_LAST_CLAIMED = "last_claimed_planned_at"

    /** Plans the daily alarm for the next reminder time, replacing the earlier one. Returns the planned moment. */
    fun scheduleDaily(
        context: Context,
        minuteOfDay: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val at = ReminderTime.nextTriggerMillis(nowMillis, minuteOfDay, zone)
        set(context, REQUEST_DAILY, REQUEST_DAILY_WINDOW, ACTION_DAILY, at)
        return at
    }

    fun cancelDaily(context: Context) {
        cancel(context, REQUEST_DAILY, ACTION_DAILY)
        cancel(context, REQUEST_DAILY_WINDOW, ACTION_DAILY)
    }

    /** Plans the snooze alarm one hour from [nowMillis]. Returns the planned moment. */
    fun snooze(context: Context, nowMillis: Long = System.currentTimeMillis()): Long {
        val at = ReminderTime.snoozeMillis(nowMillis)
        set(context, REQUEST_SNOOZE, REQUEST_SNOOZE_WINDOW, ACTION_SNOOZE_FIRE, at)
        return at
    }

    fun cancelSnooze(context: Context) {
        cancel(context, REQUEST_SNOOZE, ACTION_SNOOZE_FIRE)
        cancel(context, REQUEST_SNOOZE_WINDOW, ACTION_SNOOZE_FIRE)
    }

    /**
     * True for the first of the two copies of an alarm to arrive, false for the second one (the same planned moment). Written at
     * once (`commit`), because the process may end right after the broadcast.
     */
    fun claim(context: Context, intent: Intent): Boolean {
        val planned = intent.getLongExtra(EXTRA_PLANNED_AT, 0L)
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (!isFirstArrival(planned, prefs.getLong(KEY_LAST_CLAIMED, 0L))) return false
        if (planned != 0L) prefs.edit().putLong(KEY_LAST_CLAIMED, planned).commit()
        return true
    }

    /** An alarm without a planned moment (planned by an older version) is always taken. */
    fun isFirstArrival(plannedAt: Long, lastClaimed: Long): Boolean = plannedAt == 0L || plannedAt != lastClaimed

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

    private fun set(context: Context, requestIdle: Int, requestWindow: Int, action: String, atMillis: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        fun pending(request: Int) = PendingIntent.getBroadcast(
            context,
            request,
            intentFor(context, action).putExtra(EXTRA_PLANNED_AT, atMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setWindow(AlarmManager.RTC_WAKEUP, atMillis, WINDOW_MILLIS, pending(requestWindow))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(requestIdle))
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
