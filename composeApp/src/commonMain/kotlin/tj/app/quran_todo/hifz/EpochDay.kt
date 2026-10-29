package tj.app.quran_todo.hifz

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Calendar days of the hifz model are `epochDay` numbers: days since 1970-01-01 in the time zone of the device. */
object EpochDays {
    fun of(instant: Instant, zone: TimeZone): Int = instant.toLocalDateTime(zone).date.toEpochDays()

    fun ofMillis(epochMillis: Long, zone: TimeZone): Int = of(Instant.fromEpochMilliseconds(epochMillis), zone)

    fun now(zone: TimeZone = TimeZone.currentSystemDefault()): Int = of(Clock.System.now(), zone)

    /** Milliseconds from [instant] to the next midnight of [zone] (at least 1). */
    fun millisUntilNextMidnight(instant: Instant, zone: TimeZone): Long {
        val tomorrow: LocalDate = instant.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
        val midnight = tomorrow.atStartOfDayIn(zone)
        return (midnight.toEpochMilliseconds() - instant.toEpochMilliseconds()).coerceAtLeast(1)
    }
}

/**
 * The current epoch day as a [StateFlow] that switches by itself at midnight of the device time zone. [refresh] reads the
 * clock again right away: call it when the app returns to the foreground (`ON_RESUME`), because the timer may have been
 * late while the device slept, or the person may have changed the time zone or the date.
 *
 * The timer runs in [scope] from the moment the ticker is created (in the app it is a Koin single, created on first use).
 * [clock] and [zone] are parameters for the tests; the delays are coroutine delays, so a test scope drives them.
 */
class EpochDayTicker(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Instant = { Clock.System.now() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val _day = MutableStateFlow(EpochDays.of(clock(), zone()))
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The current epoch day. */
    val day: StateFlow<Int> = _day.asStateFlow()

    init {
        scope.launch {
            while (true) {
                val now = clock()
                _day.value = EpochDays.of(now, zone())
                // Sleep until the next midnight, or until refresh() asks to look at the clock again.
                withTimeoutOrNull(EpochDays.millisUntilNextMidnight(now, zone()) + MIDNIGHT_SLACK_MS) { wake.receive() }
            }
        }
    }

    /** Reads the clock now and wakes the timer, so that a changed time zone or date moves the next midnight too. */
    fun refresh() {
        _day.value = EpochDays.of(clock(), zone())
        wake.trySend(Unit)
    }

    private companion object {
        const val MIDNIGHT_SLACK_MS = 250L
    }
}
