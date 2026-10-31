package tj.app.quran_todo.khatm

import tj.app.quran_todo.common.mushaf.MushafPages

/** A day of the Hijri calendar: [month] 1..12 (9 is Ramadan). */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/** Turns an epoch day into a Hijri date; the Android app uses the Umm al-Qura variant of `android.icu`, the tests use the same ICU rules. */
fun interface HijriConverter {
    fun toHijri(epochDay: Int): HijriDate
}

/** Where [today] stands against Ramadan. */
sealed interface RamadanStatus {
    /** Not Ramadan and not within [RamadanCalendar.LEAD_DAYS] days before it. */
    data object Outside : RamadanStatus

    /** Ramadan begins in [daysUntil] days (1..LEAD_DAYS); [lastEpochDay] is the last day of the month. */
    data class Upcoming(val daysUntil: Int, val firstEpochDay: Int, val lastEpochDay: Int) : RamadanStatus

    /** Ramadan is on: [dayOfMonth] is the number of the day (and so of the night: 1..30) and [juz] the juz of the night. */
    data class Active(val dayOfMonth: Int, val firstEpochDay: Int, val lastEpochDay: Int) : RamadanStatus {
        val juz: Int get() = dayOfMonth.coerceIn(1, 30)

        /** Days left in the month, today included. */
        fun daysLeft(today: Int): Int = lastEpochDay - today + 1
    }
}

/** Ramadan by the Hijri calendar (task F5b): what [RamadanStatus] a day has, and the plan of a khatm for the month. Pure. */
object RamadanCalendar {
    const val RAMADAN = 9

    /** The card appears this many days before Ramadan. */
    const val LEAD_DAYS = 7

    fun status(today: Int, converter: HijriConverter): RamadanStatus {
        val date = converter.toHijri(today)
        if (date.month == RAMADAN) {
            val first = today - (date.day - 1)
            return RamadanStatus.Active(date.day, first, lastDayOfMonth(today, converter))
        }
        for (ahead in 1..LEAD_DAYS) {
            val later = converter.toHijri(today + ahead)
            if (later.month == RAMADAN) {
                return RamadanStatus.Upcoming(ahead, today + ahead - (later.day - 1), lastDayOfMonth(today + ahead, converter))
            }
        }
        return RamadanStatus.Outside
    }

    /** The last day of the month that [day] is in (a Hijri month has 29 or 30 days). */
    private fun lastDayOfMonth(day: Int, converter: HijriConverter): Int {
        val month = converter.toHijri(day).month
        var last = day
        while (last - day < 31 && converter.toHijri(last + 1).month == month) last++
        return last
    }

    /** The whole mushaf from [today] to the last day of Ramadan: "a khatm for the month". */
    fun monthPlan(status: RamadanStatus, today: Int): KhatmPlan? {
        val last = when (status) {
            is RamadanStatus.Active -> status.lastEpochDay
            is RamadanStatus.Upcoming -> status.lastEpochDay
            RamadanStatus.Outside -> return null
        }
        return KhatmPlan(startEpochDay = today, targetEpochDay = maxOf(last, today))
    }

    /** The first page of the juz of the night (1..30), where "Read" opens the pages. */
    fun firstPageOfJuz(juz: Int): Int = MushafPages.pagesOfJuz(juz.coerceIn(1, 30)).first
}
