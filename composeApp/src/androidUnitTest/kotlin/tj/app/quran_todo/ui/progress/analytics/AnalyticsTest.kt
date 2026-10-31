package tj.app.quran_todo.ui.progress.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.PlanForecast

class AnalyticsTest {
    private val today = LocalDate(2026, 10, 31).toEpochDays()

    private fun rec(
        kind: ReviewKind,
        surah: Int,
        start: Int,
        end: Int,
        day: Int = today,
        grade: ReviewGrade = ReviewGrade.GOOD,
    ): ReviewLogEntry = Fx.log(null, surah, start, end, kind, grade, day)

    // -- Retention -------------------------------------------------------------------------------------

    @Test
    fun retentionNeedsThreeRepetitionsAndListsWeakFirst() {
        val log = buildList {
            repeat(3) { add(rec(ReviewKind.RECENT, 112, 1, 4)) }
            add(rec(ReviewKind.OLD, 113, 1, 5, grade = ReviewGrade.FORGOT))
            repeat(3) { add(rec(ReviewKind.OLD, 114, 1, 6, grade = if (it == 0) ReviewGrade.FORGOT else ReviewGrade.GOOD)) }
            repeat(5) { add(rec(ReviewKind.NEW, 111, 1, 5)) }
        }
        val rows = Analytics.retentionBySurah(log, today)
        assertEquals(listOf(114, 112), rows.map { it.id })
        assertEquals(67, rows[0].percent)
        assertEquals(100, rows[1].percent)
    }

    @Test
    fun retentionWindowIs90DaysInclusive() {
        val log = List(3) { rec(ReviewKind.RECENT, 112, 1, 4, day = today - 89) } +
            List(3) { rec(ReviewKind.RECENT, 113, 1, 4, day = today - 90) }
        assertEquals(listOf(112), Analytics.retentionBySurah(log, today).map { it.id })
    }

    @Test
    fun listenedRecordsAreNeverRepetitions() {
        val log = List(5) { rec(ReviewKind.LISTEN, 112, 1, 4, grade = ReviewGrade.FORGOT) }
        assertTrue(Analytics.retentionBySurah(log, today).isEmpty())
        assertTrue(Analytics.retentionByJuz(log, today).isEmpty())
    }

    @Test
    fun percentIsHonestAtTheEdges() {
        assertEquals(99, Analytics.percentOf(299, 300))
        assertEquals(1, Analytics.percentOf(1, 1000))
        assertEquals(0, Analytics.percentOf(0, 3))
        assertEquals(100, Analytics.percentOf(3, 3))
    }

    @Test
    fun juzGroupsByTheFirstAyahOfTheRecord() {
        // Surah 78 is in juz 30, surah 2 starts in juz 1 and surah 2:142 is in juz 2.
        val log = List(3) { rec(ReviewKind.OLD, 78, 1, 5) } +
            List(3) { rec(ReviewKind.OLD, 2, 142, 150, grade = ReviewGrade.FORGOT) } +
            List(3) { rec(ReviewKind.OLD, 2, 1, 5) }
        val rows = Analytics.retentionByJuz(log, today)
        assertEquals(listOf(2, 1, 30), rows.map { it.id })
        assertEquals(0, rows[0].percent)
    }

    @Test
    fun emptyLogGivesEmptyLists() {
        assertTrue(Analytics.retentionBySurah(emptyList(), today).isEmpty())
        assertTrue(Analytics.hardPlaces(emptyList(), emptyMap(), today).isEmpty())
    }

    // -- Heat map --------------------------------------------------------------------------------------

    @Test
    fun heatYearHasWholeWeeksStartingOnMonday() {
        // 1 Jan 2026 is a Thursday: 3 empty cells in the first week.
        val year = Analytics.heatmap(2026, today, emptyList(), emptyList(), null)
        assertTrue(year.weeks.all { it.size == 7 })
        assertEquals(53, year.weeks.size)
        assertEquals(listOf(null, null, null), year.weeks[0].take(3))
        assertEquals(LocalDate(2026, 1, 1).toEpochDays(), year.weeks[0][3]?.epochDay)
        // Nothing after today.
        assertNull(year.weeks.flatten().filterNotNull().firstOrNull { it.epochDay > today })
        assertEquals(today, year.weeks.flatten().filterNotNull().last().epochDay)
    }

    @Test
    fun heatCellsCountStudyRecordsAndKeepListenedApart() {
        val log = listOf(
            rec(ReviewKind.NEW, 112, 1, 4),
            rec(ReviewKind.RECENT, 112, 1, 4),
            rec(ReviewKind.CHECK, 113, 1, 5),
            rec(ReviewKind.RECENT, 112, 1, 4, day = today - 1),
        )
        val listen = listOf(rec(ReviewKind.LISTEN, 1, 1, 7), rec(ReviewKind.LISTEN, 1, 1, 7), rec(ReviewKind.LISTEN, 1, 1, 7, day = today - 5))
        val year = Analytics.heatmap(2026, today, log, listen, null)
        val days = year.weeks.flatten().filterNotNull().associateBy { it.epochDay }
        val d = days.getValue(today)
        assertEquals(3, d.count)
        assertEquals(1, d.newCount)
        assertEquals(2, d.reviewedCount)
        assertEquals(2, d.listened)
        assertEquals(1, days.getValue(today - 1).count)
        // A day with listening only is not an active day and has no colour.
        assertEquals(0, days.getValue(today - 5).count)
        assertEquals(1, days.getValue(today - 5).listened)
        assertEquals(2, year.activeDays)
        assertEquals(3, year.max)
    }

    @Test
    fun pauseMarksOnlyDaysWithoutStudying() {
        val pause = PlanPause(today - 3, today - 1)
        val log = listOf(rec(ReviewKind.RECENT, 112, 1, 4, day = today - 2))
        val days = Analytics.heatmap(2026, today, log, emptyList(), pause).weeks.flatten().filterNotNull().associateBy { it.epochDay }
        assertTrue(days.getValue(today - 3).paused)
        assertFalse(days.getValue(today - 2).paused)
        assertTrue(days.getValue(today - 1).paused)
        assertFalse(days.getValue(today).paused)
        assertFalse(days.getValue(today - 4).paused)
    }

    @Test
    fun yearBoundariesBelongToTheirOwnYear() {
        val newYearsEve = LocalDate(2025, 12, 31).toEpochDays()
        val newYearsDay = LocalDate(2026, 1, 1).toEpochDays()
        val log = listOf(rec(ReviewKind.RECENT, 1, 1, 7, day = newYearsEve), rec(ReviewKind.RECENT, 1, 1, 7, day = newYearsDay))
        val y25 = Analytics.heatmap(2025, today, log, emptyList(), null)
        val y26 = Analytics.heatmap(2026, today, log, emptyList(), null)
        assertEquals(1, y25.activeDays)
        assertEquals(1, y26.activeDays)
        assertEquals(newYearsEve, y25.weeks.flatten().filterNotNull().last().epochDay)
        assertEquals(newYearsDay, y26.weeks.flatten().filterNotNull().first().epochDay)
    }

    @Test
    fun leapYearStartingOnSundayNeeds54Weeks() {
        // 2012 started on a Sunday and had 366 days.
        val year = Analytics.heatmap(2012, LocalDate(2013, 1, 1).toEpochDays(), emptyList(), emptyList(), null)
        assertEquals(54, year.weeks.size)
        assertEquals(366, year.weeks.flatten().filterNotNull().size)
    }

    @Test
    fun yearsRunFromTheFirstRecordToThisYear() {
        assertEquals(listOf(2026), Analytics.years(emptyList(), emptyList(), today))
        val old = rec(ReviewKind.RECENT, 1, 1, 7, day = LocalDate(2024, 6, 1).toEpochDays())
        assertEquals(listOf(2024, 2025, 2026), Analytics.years(listOf(old), emptyList(), today))
        // Listening alone counts as data for the year list too.
        val listen = rec(ReviewKind.LISTEN, 1, 1, 7, day = LocalDate(2025, 2, 1).toEpochDays())
        assertEquals(listOf(2025, 2026), Analytics.years(emptyList(), listOf(listen), today))
    }

    @Test
    fun levelsAreFourSteps() {
        assertEquals(0, Analytics.level(0, 10))
        assertEquals(1, Analytics.level(1, 10))
        assertEquals(1, Analytics.level(2, 10))
        assertEquals(2, Analytics.level(3, 10))
        assertEquals(2, Analytics.level(5, 10))
        assertEquals(3, Analytics.level(6, 10))
        assertEquals(3, Analytics.level(7, 10))
        assertEquals(4, Analytics.level(8, 10))
        assertEquals(4, Analytics.level(10, 10))
        assertEquals(0, Analytics.level(5, 0))
    }

    // -- Forecast by surah -----------------------------------------------------------------------------

    private fun forecast(remaining: Int, daily: Int, finish: Int?, finished: Boolean = false) =
        PlanForecast(remaining, remaining, daily, finished, finish, null, null, 0)

    @Test
    fun surahForecastsEndOnTheFinishDayOfThePlan() {
        // 114 (6 ayahs), 113 (5), 112 (4): 15 ayahs at 5 a day: three days, finish = start + 3.
        val plan = Fx.plan(listOf(114, 113, 112), daily = 5)
        val f = forecast(15, 5, finish = today + 3)
        val rows = Analytics.surahForecasts(plan, f, emptyMap())
        assertEquals(listOf(114, 113, 112), rows.map { it.surah })
        assertEquals(QueueState.Current, rows[0].state)
        assertEquals(QueueState.Queued, rows[1].state)
        assertEquals(today + 2, rows[0].finishEpochDay) // start = today, 6 ayahs = 2 days
        assertEquals(today + 3, rows[1].finishEpochDay) // 11 ayahs = 3 days
        assertEquals(today + 3, rows[2].finishEpochDay) // 15 ayahs = 3 days, the finish of the plan
        assertEquals(f.finishEpochDay, rows.last().finishEpochDay)
    }

    @Test
    fun learnedSurahsAreMarkedAndTheCurrentIsTheFirstUnfinished() {
        val plan = Fx.plan(listOf(114, 113, 112), daily = 5)
        val covered = mapOf(114 to (1..6).toSet(), 113 to setOf(1, 2))
        val remaining = 3 + 4
        val rows = Analytics.surahForecasts(plan, forecast(remaining, 5, today + 2), covered)
        assertEquals(QueueState.Learned, rows[0].state)
        assertNull(rows[0].finishEpochDay)
        assertEquals(QueueState.Current, rows[1].state)
        assertEquals(3, rows[1].remainingAyahs)
        assertEquals(QueueState.Queued, rows[2].state)
        assertEquals(today + 2, rows[2].finishEpochDay)
    }

    @Test
    fun finishedPlanHasNoDatesAndNoCurrent() {
        val plan = Fx.plan(listOf(114, 113))
        val covered = mapOf(114 to (1..6).toSet(), 113 to (1..5).toSet())
        val rows = Analytics.surahForecasts(plan, forecast(0, 5, null, finished = true), covered)
        assertTrue(rows.all { it.state == QueueState.Learned && it.finishEpochDay == null })
    }

    @Test
    fun noPlanOrNoForecastIsHandled() {
        assertTrue(Analytics.surahForecasts(null, null, emptyMap()).isEmpty())
        val rows = Analytics.surahForecasts(Fx.plan(listOf(114)), null, emptyMap())
        assertEquals(1, rows.size)
        assertNull(rows[0].finishEpochDay)
        assertNotNull(rows[0].remainingAyahs.takeIf { it == 6 })
    }

    // -- Hard places -----------------------------------------------------------------------------------

    @Test
    fun hardPlacesRankMissesThenMarks() {
        val log = listOf(
            rec(ReviewKind.CHECK, 112, 2, 2, grade = ReviewGrade.FORGOT),
            rec(ReviewKind.CHECK, 112, 2, 2, grade = ReviewGrade.HARD),
            rec(ReviewKind.CHECK, 113, 3, 3, grade = ReviewGrade.HARD),
            rec(ReviewKind.CHECK, 114, 1, 1, grade = ReviewGrade.GOOD),
        )
        val places = Analytics.hardPlaces(log, mapOf(110 to setOf(1)), today)
        assertEquals(listOf(112 to 2, 113 to 3, 110 to 1), places.map { it.surah to it.ayah })
        assertEquals(2, places[0].misses)
        assertFalse(places[0].current)
        assertTrue(places[2].current)
    }

    @Test
    fun longRecordBlamesOnlyMarkedAyahsOrShortRanges() {
        val marked = mapOf(112 to setOf(3))
        val log = listOf(
            rec(ReviewKind.RECENT, 112, 1, 4, grade = ReviewGrade.HARD), // marked 3 inside: blames 3 only
            rec(ReviewKind.RECENT, 113, 1, 5, grade = ReviewGrade.HARD), // nothing marked, 5 ayahs: blames all
            rec(ReviewKind.OLD, 2, 1, 40, grade = ReviewGrade.FORGOT), // long and nothing marked: blames nobody
        )
        val places = Analytics.hardPlaces(log, marked, today)
        assertEquals(setOf(112 to 3) + (1..5).map { 113 to it }, places.map { it.surah to it.ayah }.toSet())
    }

    @Test
    fun hardPlacesAreCappedAndOldMissesFallOut() {
        val log = (1..15).map { rec(ReviewKind.CHECK, 2, it, it, grade = ReviewGrade.HARD) } +
            rec(ReviewKind.CHECK, 3, 1, 1, day = today - 90, grade = ReviewGrade.FORGOT)
        val places = Analytics.hardPlaces(log, emptyMap(), today)
        assertEquals(10, places.size)
        assertTrue(places.none { it.surah == 3 })
        assertTrue(Analytics.hardPlaces(log, emptyMap(), today, limit = 3).size == 3)
    }

    @Test
    fun listenedRecordsAreNotMisses() {
        val log = listOf(rec(ReviewKind.LISTEN, 112, 2, 2, grade = ReviewGrade.FORGOT))
        assertTrue(Analytics.hardPlaces(log, emptyMap(), today).isEmpty())
    }
}
