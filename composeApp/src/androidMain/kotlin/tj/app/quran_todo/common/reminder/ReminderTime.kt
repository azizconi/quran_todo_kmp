package tj.app.quran_todo.common.reminder

import java.time.Instant
import java.time.ZoneId

/**
 * When the reminder comes. Pure functions over `java.time`, so the tests can pin the clock and the time zone.
 *
 * The reminder time is a wall-clock time ("20:00") of the phone, so it follows the time zone and the daylight saving
 * changes of the phone: every plan is computed again from the current zone ([ReminderSync] runs after a zone or clock change).
 */
internal object ReminderTime {
    const val MINUTES_PER_DAY = 24 * 60

    /** "In an hour" in the notification. */
    const val SNOOZE_MILLIS: Long = 60L * 60L * 1000L

    /**
     * The first moment strictly after [nowMillis] at which the wall clock of [zone] shows [minuteOfDay] (minutes after
     * midnight, 0..1439): today if that time is still ahead, otherwise tomorrow.
     *
     * Daylight saving: a time that does not exist on the day the clocks go forward (02:30 in a 02:00 -> 03:00 gap) is moved
     * forward by the length of the gap (03:30), so the reminder still comes that day; a time that exists twice when the clocks go
     * back (01:30) is taken at its first occurrence.
     */
    fun nextTriggerMillis(nowMillis: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val first = today.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
        if (first > nowMillis) return first
        return today.plusDays(1).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
    }

    /** The snoozed moment: one hour from [nowMillis]. */
    fun snoozeMillis(nowMillis: Long): Long = nowMillis + SNOOZE_MILLIS
}
