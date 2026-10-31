package tj.app.quran_todo.khatm

import com.ibm.icu.util.Calendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.common.mushaf.MushafPages
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ramadan by the Umm al-Qura calendar (task F5b). The converter here is ICU4J with the same rules as `android.icu` that the app uses
 * (the stub of android.jar does not run in a JVM test), so what is checked is [RamadanCalendar] and the dates that ICU gives.
 */
class RamadanTest {
    private val converter = HijriConverter { epochDay ->
        val calendar = Calendar.getInstance(TimeZone.GMT_ZONE, ULocale("en@calendar=islamic-umalqura"))
        calendar.timeInMillis = epochDay * 86_400_000L + 43_200_000L
        HijriDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int): Int = LocalDate(year, month, dayOfMonth).toEpochDays()

    private fun firstOfRamadan2027(): Int = (day(2027, 1, 1)..day(2027, 12, 31)).first {
        (RamadanCalendar.status(it, converter) as? RamadanStatus.Active)?.dayOfMonth == 1
    }

    @Test
    fun ramadanStartsOnTheKnownDatesOfTheNextFiveYears() {
        // Expected first days of Ramadan, with a day of room: the calendar is computed.
        val expected = listOf(
            day(2027, 2, 8), day(2028, 1, 28), day(2029, 1, 16), day(2030, 1, 5), day(2030, 12, 26), day(2031, 12, 15),
        )
        val firsts = (day(2027, 1, 1)..day(2031, 12, 31)).filter {
            val status = RamadanCalendar.status(it, converter)
            status is RamadanStatus.Active && status.dayOfMonth == 1
        }
        assertEquals(expected.size, firsts.size, "found $firsts")
        expected.zip(firsts).forEach { (want, got) ->
            assertTrue(abs(want - got) <= 1, "want ${LocalDate.fromEpochDays(want)} got ${LocalDate.fromEpochDays(got)}")
        }
    }

    @Test
    fun theMonthHasTwentyNineOrThirtyDays() {
        val first = firstOfRamadan2027()
        val active = RamadanCalendar.status(first, converter) as RamadanStatus.Active
        val length = active.lastEpochDay - active.firstEpochDay + 1
        assertTrue(length == 29 || length == 30, "length $length")
        assertEquals(first, active.firstEpochDay)
        assertEquals(1, active.juz)
        val lastDay = RamadanCalendar.status(active.lastEpochDay, converter) as RamadanStatus.Active
        assertEquals(length, lastDay.dayOfMonth)
        assertEquals(length, lastDay.juz)
        assertEquals(1, lastDay.daysLeft(active.lastEpochDay))
    }

    @Test
    fun theCardComesSevenDaysBeforeAndGoesAfterTheLastDay() {
        val first = firstOfRamadan2027()
        assertEquals(RamadanStatus.Outside, RamadanCalendar.status(first - 8, converter))
        val upcoming = RamadanCalendar.status(first - 7, converter)
        assertIs<RamadanStatus.Upcoming>(upcoming)
        assertEquals(7, upcoming.daysUntil)
        assertEquals(first, upcoming.firstEpochDay)
        assertEquals(1, (RamadanCalendar.status(first - 1, converter) as RamadanStatus.Upcoming).daysUntil)
        val active = RamadanCalendar.status(first, converter) as RamadanStatus.Active
        assertEquals(RamadanStatus.Outside, RamadanCalendar.status(active.lastEpochDay + 1, converter))
    }

    @Test
    fun theMonthPlanCoversTheMushafUpToTheLastDayOfRamadan() {
        val first = firstOfRamadan2027()
        val status = RamadanCalendar.status(first, converter) as RamadanStatus.Active
        val plan = RamadanCalendar.monthPlan(status, first)!!
        assertEquals(first, plan.startEpochDay)
        assertEquals(status.lastEpochDay, plan.targetEpochDay)
        assertEquals(604, plan.totalPages)
        // 604 pages over 29 or 30 days: 21 pages on the first day.
        val state = KhatmState(plan, KhatmMath.freshProgress(plan))
        assertEquals(21, KhatmMath.dailyPages(state, first))
        // Before Ramadan the plan starts today and ends with the month.
        val before = RamadanCalendar.status(first - 3, converter)
        val early = RamadanCalendar.monthPlan(before, first - 3)!!
        assertEquals(first - 3, early.startEpochDay)
        assertEquals(status.lastEpochDay, early.targetEpochDay)
        assertNull(RamadanCalendar.monthPlan(RamadanStatus.Outside, first))
    }

    @Test
    fun theJuzOfTheNightIsTheNumberOfTheNightAndOpensItsFirstPage() {
        assertEquals(1, RamadanCalendar.firstPageOfJuz(1))
        assertEquals(22, RamadanCalendar.firstPageOfJuz(2))
        (1..30).forEach { juz -> assertEquals(MushafPages.pagesOfJuz(juz).first, RamadanCalendar.firstPageOfJuz(juz)) }
        assertEquals(RamadanCalendar.firstPageOfJuz(30), RamadanCalendar.firstPageOfJuz(31))
    }
}
