package tj.app.quran_todo.khatm

import tj.app.quran_todo.data.database.entity.hifz.HifzMetaEntity
import tj.app.quran_todo.hifz.backup.BackupCodec
import tj.app.quran_todo.hifz.backup.BackupFile
import tj.app.quran_todo.hifz.backup.BackupFormat
import tj.app.quran_todo.hifz.backup.BackupMapping
import tj.app.quran_todo.hifz.backup.BackupParse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The reading plan (task F5b): the portion of the day, being behind or ahead, the end, a new end day, a start from the middle, and the file of the backup. */
class KhatmTest {
    private val today = 20_000

    private fun fresh(days: Int = 30, fromPage: Int = 1, start: Int = today): KhatmState {
        val plan = KhatmMath.planForDays(days, start, fromPage)
        return KhatmState(plan, KhatmMath.freshProgress(plan))
    }

    @Test
    fun thirtyDaysGiveTwentyOneAPageOnTheFirstDay() {
        val state = fresh(30)
        assertEquals(604, state.plan.totalPages)
        assertEquals(30, state.plan.totalDays)
        // 604 / 30 = 20.13: rounded up to 21 pages.
        assertEquals(21, KhatmMath.dailyPages(state, today))
        assertEquals(KhatmPortion(1, 21), KhatmMath.portion(state, today))
        val status = KhatmMath.status(state, today)
        assertIs<KhatmToday.Pending>(status)
        assertEquals(KhatmPortion(1, 21), status.portion)
        assertEquals(27, status.minutes)
    }

    @Test
    fun thePortionIsWhatIsLeftOverTheDaysThatAreLeft() {
        // 20 pages read in a plan of 30 days; on day 2 (29 days left) 584 pages are left: 584 / 29 = 20.14 -> 21.
        val plan = KhatmMath.planForDays(30, today)
        val state = KhatmState(plan, KhatmProgress(lastPage = 20, readDays = setOf(today)))
        assertEquals(KhatmPortion(21, 41), KhatmMath.portion(state, today + 1))
        // The very last day asks for everything that is left, never more than the end of the mushaf.
        val late = KhatmState(plan, KhatmProgress(lastPage = 590))
        assertEquals(KhatmPortion(591, 604), KhatmMath.portion(late, today + 29))
    }

    @Test
    fun theExamplePortionOfTheBriefIsPages121To140() {
        // 120 pages read, 20 a day: the next portion is 121..140.
        val plan = KhatmPlan(startEpochDay = today - 6, targetEpochDay = today + 18, startPage = 1, endPage = 604)
        val state = KhatmState(plan, KhatmProgress(lastPage = 120))
        // 484 left over 19 days = 25.5 -> 26: just checks the first page.
        assertEquals(121, KhatmMath.portion(state, today)!!.firstPage)
        val twenty = KhatmState(KhatmPlan(today, today + 24, 1, 604), KhatmProgress(120))
        // 484 / 25 = 19.36 -> 20.
        assertEquals(KhatmPortion(121, 140), KhatmMath.portion(twenty, today))
    }

    @Test
    fun markingThePortionMovesTheLastPageAndGivesTomorrowsPortion() {
        val state = fresh(30)
        val after = KhatmMath.markPortionRead(state, today)
        assertNotNull(after)
        assertEquals(21, after.lastPage)
        assertEquals(setOf(today), after.readDays)
        val next = state.copy(progress = after)
        val status = KhatmMath.status(next, today)
        assertIs<KhatmToday.DoneToday>(status)
        // 583 left over 29 days = 20.1 -> 21 pages: 22..42.
        assertEquals(KhatmPortion(22, 42), status.next)
        assertEquals(KhatmPortion(22, 42), (KhatmMath.status(next, today + 1) as KhatmToday.Pending).portion)
    }

    @Test
    fun behindAndAhead() {
        val state = fresh(30)
        assertEquals(0, KhatmMath.deltaPages(state, today))
        // Day 11 (10 days elapsed), nothing read: 604 * 10 / 30 = 201 pages behind.
        assertEquals(-201, KhatmMath.deltaPages(state, today + 10))
        // Read 300 pages by day 11: 99 ahead.
        val ahead = state.copy(progress = KhatmProgress(300, setOf(today + 9)))
        assertEquals(99, KhatmMath.deltaPages(ahead, today + 10))
        // The portion of the day is marked: counted up to the end of that day.
        val done = state.copy(progress = KhatmMath.markPortionRead(state, today)!!)
        assertEquals(21 - 20, KhatmMath.deltaPages(done, today))
    }

    @Test
    fun theEndOfThePlan() {
        val plan = KhatmMath.planForDays(7, today)
        val last = KhatmMath.planForDays(1, today)
        val almost = KhatmState(last, KhatmProgress(lastPage = 600))
        val portion = KhatmMath.portion(almost, today)!!
        assertEquals(KhatmPortion(601, 604), portion)
        val finished = almost.copy(progress = KhatmMath.markPortionRead(almost, today)!!)
        assertTrue(KhatmMath.isComplete(finished))
        assertEquals(1f, KhatmMath.fraction(finished))
        assertEquals(KhatmToday.Complete, KhatmMath.status(finished, today))
        assertNull(KhatmMath.portion(finished, today))
        assertNull(KhatmMath.markPortionRead(finished, today))
        assertEquals(1, KhatmMath.daysLeft(plan, today + 20))
        assertEquals(-13, KhatmMath.daysToEnd(plan, today + 19))
    }

    @Test
    fun aPlanPastItsEndDayIsOverdueNotOneHugePortion() {
        val plan = KhatmMath.planForDays(7, today)
        val state = KhatmState(plan, KhatmProgress(lastPage = 100, readDays = setOf(today)))
        // The last day is still a normal day; the day after it is overdue.
        assertIs<KhatmToday.Pending>(KhatmMath.status(state, plan.targetEpochDay))
        assertFalse(KhatmMath.isOverdue(state, plan.targetEpochDay))
        assertTrue(KhatmMath.isOverdue(state, plan.targetEpochDay + 1))
        assertEquals(KhatmToday.Overdue(nextPage = 101), KhatmMath.status(state, plan.targetEpochDay + 1))
        assertEquals(KhatmToday.Overdue(nextPage = 101), KhatmMath.status(state, today + 400))
        // Nothing is marked as "today's portion" any more.
        assertNull(KhatmMath.markPortionRead(state, plan.targetEpochDay + 1))
        // Reading on by hand still works, and the end of the mushaf completes it even when overdue.
        val manual = KhatmMath.markUpTo(state, 604, plan.targetEpochDay + 3)
        assertEquals(KhatmToday.Complete, KhatmMath.status(state.copy(progress = manual), plan.targetEpochDay + 3))
        // From the middle the page to go on from is never before the start page.
        val middle = KhatmState(KhatmMath.planForDays(3, today, fromPage = 301), KhatmMath.freshProgress(KhatmMath.planForDays(3, today, fromPage = 301)))
        assertEquals(KhatmToday.Overdue(nextPage = 301), KhatmMath.status(middle, today + 10))
    }

    @Test
    fun aNewEndDayBringsAnOverduePlanBackToNormalPortions() {
        val plan = KhatmMath.planForDays(7, today)
        val state = KhatmState(plan, KhatmProgress(lastPage = 100))
        val now = today + 20
        val later = state.copy(plan = KhatmMath.withTarget(plan, now + 10, now))
        assertFalse(KhatmMath.isOverdue(later, now))
        val status = KhatmMath.status(later, now)
        assertIs<KhatmToday.Pending>(status)
        // 504 left over 11 days: 46 pages.
        assertEquals(KhatmPortion(101, 146), status.portion)
    }

    @Test
    fun changingTheEndDayKeepsTheProgressAndRecalculates() {
        val plan = KhatmMath.planForDays(30, today)
        val state = KhatmState(plan, KhatmProgress(lastPage = 100, readDays = setOf(today)))
        val later = KhatmMath.withTarget(plan, today + 59, today + 4)
        assertEquals(today + 59, later.targetEpochDay)
        val moved = state.copy(plan = later)
        // 504 left over 56 days (from day 5 to day 60) = 9 pages a day.
        assertEquals(9, KhatmMath.dailyPages(moved, today + 4))
        // A date in the past is cut to today.
        assertEquals(today + 4, KhatmMath.withTarget(plan, today - 3, today + 4).targetEpochDay)
    }

    @Test
    fun startingFromTheMiddle() {
        val state = fresh(days = 10, fromPage = 301)
        assertEquals(304, state.plan.totalPages)
        assertEquals(300, state.progress.lastPage)
        assertEquals(0, KhatmMath.pagesRead(state))
        // 304 / 10 = 30.4 -> 31 pages: 301..331.
        assertEquals(KhatmPortion(301, 331), KhatmMath.portion(state, today))
        val after = KhatmMath.markPortionRead(state, today)!!
        assertEquals(331, after.lastPage)
        assertEquals(31, KhatmMath.pagesRead(state.copy(progress = after)))
        // The last portion stops at page 604.
        val end = KhatmState(state.plan, KhatmProgress(590))
        assertEquals(KhatmPortion(591, 604), KhatmMath.portion(end, today + 9))
    }

    @Test
    fun theManualCorrectionMovesTheLastPageBothWays() {
        val state = fresh(30)
        val forward = KhatmMath.markUpTo(state, 50, today)
        assertEquals(50, forward.lastPage)
        assertEquals(setOf(today), forward.readDays)
        val back = KhatmMath.markUpTo(state.copy(progress = forward), 30, today + 1)
        assertEquals(30, back.lastPage)
        assertEquals(setOf(today), back.readDays)
        assertEquals(0, KhatmMath.markUpTo(state, -5, today).lastPage)
        assertEquals(604, KhatmMath.markUpTo(state, 900, today).lastPage)
        val middle = fresh(10, fromPage = 301)
        assertEquals(300, KhatmMath.markUpTo(middle, 100, today).lastPage)
    }

    @Test
    fun theCalendarShowsReadMissedTodayAndFuture() {
        val plan = KhatmMath.planForDays(5, today)
        val state = KhatmState(plan, KhatmProgress(40, setOf(today, today + 2)))
        val statuses = KhatmMath.calendar(state, today + 3).map { it.status }
        assertEquals(
            listOf(KhatmDayStatus.Read, KhatmDayStatus.Missed, KhatmDayStatus.Read, KhatmDayStatus.Today, KhatmDayStatus.Future),
            statuses,
        )
    }

    @Test
    fun theCardAsksToMarkOnlyOnceThePortionIsReached() {
        val state = fresh(30)
        assertFalse(KhatmMath.portionReachedBy(state, today, null))
        assertFalse(KhatmMath.portionReachedBy(state, today, 20))
        assertTrue(KhatmMath.portionReachedBy(state, today, 21))
        assertTrue(KhatmMath.portionReachedBy(state, today, 22))
        assertFalse(KhatmMath.portionReachedBy(state, today, 300))
        val done = state.copy(progress = KhatmMath.markPortionRead(state, today)!!)
        assertFalse(KhatmMath.portionReachedBy(done, today, 21))
    }

    @Test
    fun minutesAreAboutAMinuteAndAQuarterAPage() {
        assertEquals(0, KhatmMath.minutesFor(0))
        assertEquals(25, KhatmMath.minutesFor(20))
        assertEquals(2, KhatmMath.minutesFor(1))
    }

    // ---- hifz_meta and the backup file ----

    @Test
    fun theStoredValuesRoundTrip() {
        val plan = KhatmPlan(20_000, 20_029, 301, 604)
        assertEquals(plan, KhatmStorage.decodePlan(KhatmStorage.encodePlan(plan)))
        assertEquals(setOf(1, 5, 9), KhatmStorage.decodeDays(KhatmStorage.encodeDays(setOf(9, 1, 5))))
        val state = KhatmStorage.decode(KhatmStorage.encodePlan(plan), "350", "20000,20001")
        assertEquals(KhatmState(plan, KhatmProgress(350, setOf(20_000, 20_001))), state)
        // A page outside the plan is cut back into it; a missing page is "nothing read".
        assertEquals(300, KhatmStorage.decode(KhatmStorage.encodePlan(plan), "5", null)!!.progress.lastPage)
        assertEquals(300, KhatmStorage.decode(KhatmStorage.encodePlan(plan), null, null)!!.progress.lastPage)
        assertEquals(604, KhatmStorage.decode(KhatmStorage.encodePlan(plan), "9999", null)!!.progress.lastPage)
    }

    @Test
    fun brokenValuesGiveNoPlan() {
        assertNull(KhatmStorage.decode(null, null, null))
        assertNull(KhatmStorage.decode("", null, null))
        assertNull(KhatmStorage.decode("1,2,3", null, null))
        assertNull(KhatmStorage.decode("20000,20010,1,700", null, null))
        assertNull(KhatmStorage.decode("20010,20000,1,604", null, null))
        assertNull(KhatmStorage.decode("a,b,c,d", null, null))
    }

    @Test
    fun theKeysAreKhatmKeysOfHifzMeta() {
        assertTrue(KhatmStorage.KEYS.all { it.startsWith("khatm_") })
    }

    @Test
    fun thePlanSurvivesAnExportAndAnImport() {
        val plan = KhatmPlan(20_000, 20_029, 1, 604)
        val progress = KhatmProgress(121, setOf(20_000, 20_002))
        val meta = listOf(
            HifzMetaEntity("legacy_import", "done"),
            HifzMetaEntity(KhatmStorage.PLAN_KEY, KhatmStorage.encodePlan(plan)),
            HifzMetaEntity(KhatmStorage.PAGE_KEY, progress.lastPage.toString()),
            HifzMetaEntity(KhatmStorage.DAYS_KEY, KhatmStorage.encodeDays(progress.readDays)),
        )
        val data = BackupMapping.toBackupData(null, emptyList(), emptyList(), meta, emptyList())
        val file = BackupFile(
            format = BackupFormat.NAME,
            formatVersion = BackupFormat.VERSION,
            appVersion = "1.0.2 (7)",
            exportedAt = 1_773_312_000_000,
            dbVersion = BackupFormat.DB_VERSION,
            hifz = data.hifz,
            notes = data.notes,
        )
        val parse = BackupCodec.decode(BackupCodec.encode(file))
        assertIs<BackupParse.Ok>(parse)
        val byKey = BackupMapping.toRows(parse.backup.file).meta.associate { it.key to it.value }
        val state = KhatmStorage.decode(byKey[KhatmStorage.PLAN_KEY], byKey[KhatmStorage.PAGE_KEY], byKey[KhatmStorage.DAYS_KEY])
        assertEquals(KhatmState(plan, progress), state)
    }
}
