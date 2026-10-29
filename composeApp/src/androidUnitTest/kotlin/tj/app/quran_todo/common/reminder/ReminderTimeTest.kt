package tj.app.quran_todo.common.reminder

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The moment of the next reminder: today or tomorrow, daylight saving, a change of the time zone. */
class ReminderTimeTest {
    private val dushanbe = ZoneId.of("Asia/Dushanbe") // UTC+5, no daylight saving
    private val newYork = ZoneId.of("America/New_York")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute, second), zone).toInstant().toEpochMilli()

    private fun local(millis: Long, zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    private fun minuteOf(hour: Int, minute: Int) = hour * 60 + minute

    @Test
    fun beforeTheTimeTodayTheReminderIsToday() {
        val now = at(dushanbe, 2026, 5, 10, 18, 30)
        val next = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), dushanbe)
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 0), local(next, dushanbe))
    }

    @Test
    fun afterTheTimeTodayTheReminderIsTomorrow() {
        val now = at(dushanbe, 2026, 5, 10, 20, 0, second = 1)
        val next = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), dushanbe)
        assertEquals(LocalDateTime.of(2026, 5, 11, 20, 0), local(next, dushanbe))
    }

    @Test
    fun atExactlyTheTimeTheNextOneIsTomorrowSoTheAlarmNeverRepeatsItself() {
        val now = at(dushanbe, 2026, 5, 10, 20, 0)
        val next = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), dushanbe)
        assertEquals(LocalDateTime.of(2026, 5, 11, 20, 0), local(next, dushanbe))
        assertTrue(next > now)
    }

    @Test
    fun theTimeIsAlwaysInTheFuture() {
        val start = at(dushanbe, 2026, 5, 10, 0, 0)
        for (minuteOfDay in listOf(0, 1, 59, 600, 1199, 1200, 1439)) {
            for (step in 0 until 24 * 60 step 7) {
                val now = start + step * 60_000L
                val next = ReminderTime.nextTriggerMillis(now, minuteOfDay, dushanbe)
                assertTrue(next > now, "minute $minuteOfDay at step $step")
                assertTrue(next - now <= 24 * 60 * 60_000L, "never more than a day away: minute $minuteOfDay at step $step")
            }
        }
    }

    @Test
    fun theLastAndFirstMinutesOfTheDay() {
        val lateEvening = at(dushanbe, 2026, 5, 10, 23, 59, second = 30)
        assertEquals(
            LocalDateTime.of(2026, 5, 11, 0, 0),
            local(ReminderTime.nextTriggerMillis(lateEvening, 0, dushanbe), dushanbe),
        )
        assertEquals(
            LocalDateTime.of(2026, 5, 11, 23, 59),
            local(ReminderTime.nextTriggerMillis(lateEvening, 1439, dushanbe), dushanbe),
        )
    }

    @Test
    fun theTimeOfDayIsKeptAcrossTheEndOfAMonthAndAYear() {
        val now = at(dushanbe, 2026, 12, 31, 21, 0)
        val next = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), dushanbe)
        assertEquals(LocalDateTime.of(2027, 1, 1, 20, 0), local(next, dushanbe))
    }

    @Test
    fun anOutOfRangeMinuteIsClamped() {
        val now = at(dushanbe, 2026, 5, 10, 12, 0)
        assertEquals(
            LocalDateTime.of(2026, 5, 10, 23, 59),
            local(ReminderTime.nextTriggerMillis(now, 5000, dushanbe), dushanbe),
        )
        assertEquals(
            LocalDateTime.of(2026, 5, 11, 0, 0),
            local(ReminderTime.nextTriggerMillis(now, -5, dushanbe), dushanbe),
        )
    }

    // ---- Daylight saving ----

    @Test
    fun aTimeThatDoesNotExistWhenTheClocksGoForwardComesAtTheShiftedTimeThatDay() {
        // New York, 2026-03-08: at 02:00 the clocks jump to 03:00, so 02:30 does not exist.
        val now = at(newYork, 2026, 3, 8, 0, 10)
        val next = ReminderTime.nextTriggerMillis(now, minuteOf(2, 30), newYork)
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), local(next, newYork))
        // And the day after it is the normal 02:30 again.
        val dayAfter = ReminderTime.nextTriggerMillis(next, minuteOf(2, 30), newYork)
        assertEquals(LocalDateTime.of(2026, 3, 9, 2, 30), local(dayAfter, newYork))
    }

    @Test
    fun aTimeThatExistsTwiceWhenTheClocksGoBackComesAtItsFirstOccurrenceOnly() {
        // New York, 2026-11-01: at 02:00 the clocks go back to 01:00, so 01:30 happens twice (EDT, then EST).
        val before = at(newYork, 2026, 11, 1, 0, 10)
        val first = ReminderTime.nextTriggerMillis(before, minuteOf(1, 30), newYork)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(), first) // 01:30 EDT = 05:30 UTC
        // Once the first 01:30 is behind, the next reminder is the following day, not the second 01:30 of the same night.
        val following = ReminderTime.nextTriggerMillis(first, minuteOf(1, 30), newYork)
        assertEquals(LocalDateTime.of(2026, 11, 2, 1, 30), local(following, newYork))
    }

    @Test
    fun theEveningReminderStaysAtTheSameWallClockTimeAcrossAClockChange() {
        // Berlin, last Sunday of March 2026 is the 29th: 20:00 on the 28th is UTC+1, 20:00 on the 29th is UTC+2.
        val saturday = at(berlin, 2026, 3, 28, 21, 0)
        val sunday = ReminderTime.nextTriggerMillis(saturday, minuteOf(20, 0), berlin)
        assertEquals(LocalDateTime.of(2026, 3, 29, 20, 0), local(sunday, berlin))
        assertEquals(Instant.parse("2026-03-29T18:00:00Z").toEpochMilli(), sunday)
        // The gap between the two reminders is 23 hours, because that day is one hour shorter.
        val saturdayReminder = ReminderTime.nextTriggerMillis(at(berlin, 2026, 3, 28, 19, 0), minuteOf(20, 0), berlin)
        assertEquals(23 * 60 * 60_000L, sunday - saturdayReminder)
    }

    // ---- A change of the time zone ----

    @Test
    fun anotherTimeZoneMovesTheMomentButNotTheWallClockTime() {
        val now = at(dushanbe, 2026, 5, 10, 18, 30)
        val inDushanbe = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), dushanbe)
        val inBerlin = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), berlin)
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 0), local(inDushanbe, dushanbe))
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 0), local(inBerlin, berlin))
        // Dushanbe is UTC+5 and Berlin UTC+2 in May: Berlin's 20:00 comes three hours after Dushanbe's.
        assertEquals(3 * 60 * 60_000L, inBerlin - inDushanbe)
    }

    @Test
    fun flyingEastAfterTodaysTimeHasPassedThereGivesTomorrow() {
        // 17:00 in Berlin is 20:00 in Dushanbe: in Berlin today's 20:00 is still ahead, in Dushanbe it has just passed.
        val now = at(berlin, 2026, 5, 10, 17, 0, second = 5)
        val berlinNext = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), berlin)
        val dushanbeNext = ReminderTime.nextTriggerMillis(now, minuteOf(20, 0), dushanbe)
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 0), local(berlinNext, berlin))
        assertEquals(LocalDateTime.of(2026, 5, 11, 20, 0), local(dushanbeNext, dushanbe))
    }

    // ---- Snooze ----

    @Test
    fun snoozeIsExactlyOneHour() {
        val now = at(dushanbe, 2026, 5, 10, 20, 0, second = 7)
        assertEquals(now + 60 * 60 * 1000L, ReminderTime.snoozeMillis(now))
    }
}
