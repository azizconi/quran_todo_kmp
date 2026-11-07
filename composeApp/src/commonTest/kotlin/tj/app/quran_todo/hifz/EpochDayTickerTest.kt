package tj.app.quran_todo.hifz

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EpochDayTickerTest {
    private val utc = TimeZone.UTC
    private val march1 = LocalDate(2026, 3, 1).toEpochDays()

    @Test
    fun theEpochDayIsTheCalendarDayOfTheTimeZone() {
        val instant = Instant.parse("2026-03-01T20:00:00Z")
        assertEquals(march1, EpochDays.of(instant, utc))
        // Five hours ahead it is already after midnight.
        assertEquals(march1 + 1, EpochDays.of(instant, TimeZone.of("UTC+05:00")))
        assertEquals(march1, EpochDays.ofMillis(instant.toEpochMilliseconds(), utc))
        assertEquals(0, EpochDays.of(Instant.parse("1970-01-01T00:00:00Z"), utc))
    }

    @Test
    fun theTimeToTheNextMidnightFollowsTheTimeZone() {
        val instant = Instant.parse("2026-03-01T23:59:00Z")
        assertEquals(60_000L, EpochDays.millisUntilNextMidnight(instant, utc))
        // 23:59 UTC is 04:59 on the next day at UTC+5: 19 hours and 1 minute to its midnight.
        assertEquals(19 * 3_600_000L + 60_000L, EpochDays.millisUntilNextMidnight(instant, TimeZone.of("UTC+05:00")))
        // 00:00:00 sharp is a new day: the next midnight is a whole day away.
        assertEquals(86_400_000L, EpochDays.millisUntilNextMidnight(Instant.parse("2026-03-02T00:00:00Z"), utc))
    }

    @Test
    fun theDaySwitchesByItselfAtMidnight() = runTest {
        val start = Instant.parse("2026-03-01T23:59:00Z").toEpochMilliseconds()
        val ticker = EpochDayTicker(
            scope = backgroundScope,
            clock = { Instant.fromEpochMilliseconds(start + testScheduler.currentTime) },
            zone = { utc },
        )
        runCurrent()
        assertEquals(march1, ticker.day.value)

        advanceTimeBy(59_000)
        runCurrent()
        assertEquals(march1, ticker.day.value, "23:59:59 is still the same day")

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(march1 + 1, ticker.day.value, "00:00:01 is the next day")

        advanceTimeBy(86_400_000)
        runCurrent()
        assertEquals(march1 + 2, ticker.day.value, "and the next midnight is awaited too")
    }

    @Test
    fun refreshReadsTheClockAtOnceAndMovesTheNextMidnight() = runTest {
        var now = Instant.parse("2026-03-01T10:00:00Z")
        var zone: TimeZone = utc
        val ticker = EpochDayTicker(scope = backgroundScope, clock = { now }, zone = { zone })
        runCurrent()
        assertEquals(march1, ticker.day.value)

        // The device slept through the timer: the clock jumped a day ahead.
        now = Instant.parse("2026-03-02T09:00:00Z")
        ticker.refresh()
        assertEquals(march1 + 1, ticker.day.value, "refresh() does not wait for the timer")

        // The person flew somewhere five hours ahead: it is the evening there and the day changes with the zone.
        now = Instant.parse("2026-03-02T20:00:00Z")
        zone = TimeZone.of("UTC+05:00")
        ticker.refresh()
        runCurrent()
        assertEquals(march1 + 2, ticker.day.value)
    }
}
