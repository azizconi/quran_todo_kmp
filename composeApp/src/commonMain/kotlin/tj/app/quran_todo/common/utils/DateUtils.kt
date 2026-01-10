package tj.app.quran_todo.common.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun currentLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun localDateFromEpoch(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

fun daysBetween(from: LocalDate, to: LocalDate): Int =
    (to.toEpochDays() - from.toEpochDays()).coerceAtLeast(0)
