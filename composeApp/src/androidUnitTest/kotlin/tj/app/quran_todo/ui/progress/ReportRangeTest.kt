package tj.app.quran_todo.ui.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.ui.progress.report.ReportBuilder
import tj.app.quran_todo.ui.progress.report.ReportComposer
import tj.app.quran_todo.ui.progress.report.ReportPeriod
import tj.app.quran_todo.ui.progress.report.toPlainText

/** The report of a custom range and the heat strip of its days (task F5a). */
class ReportRangeTest {
    private val today = 20_000

    private fun sources() = ProgressSources(
        today = today,
        language = AppLanguage.EN,
        names = List(114) { "S${it + 1}" },
        statuses = emptyMap(),
        covered = emptyMap(),
        hard = emptyMap(),
        log = emptyList(),
        streak = 0,
        lesson = null,
        plan = null,
        forecast = null,
    )

    private fun rec(kind: ReviewKind, day: Int, surah: Int = 112, start: Int = 1, end: Int = 4, grade: ReviewGrade = ReviewGrade.GOOD) =
        Fx.log(null, surah, start, end, kind, grade, day)

    @Test
    fun customRangeCountsOnlyItsOwnDaysFromTheGivenLog() {
        val log = listOf(
            rec(ReviewKind.NEW, today - 100, surah = 111, start = 1, end = 5),
            rec(ReviewKind.RECENT, today - 99),
            rec(ReviewKind.RECENT, today - 98, grade = ReviewGrade.FORGOT),
            rec(ReviewKind.RECENT, today - 50),
        )
        val report = ReportBuilder.build(sources(), ReportPeriod.Custom, today - 100, today - 98, log)
        assertEquals(today - 100, report.fromDay)
        assertEquals(today - 98, report.toDay)
        assertEquals(3, report.dayCount)
        assertEquals(5, report.newAyahs)
        assertEquals(2, report.repetitions)
        assertEquals(3, report.activeDays)
        assertEquals(50, report.retentionPercent)
        assertEquals(listOf(1, 1, 1), report.strip)
    }

    @Test
    fun stripHasOneCellPerDayAndKeepsEmptyDays() {
        val log = listOf(rec(ReviewKind.RECENT, today), rec(ReviewKind.OLD, today), rec(ReviewKind.RECENT, today - 6))
        val week = ReportBuilder.build(sources(), ReportPeriod.Week, log = log)
        assertEquals(7, week.strip.size)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 2), week.strip)
    }

    @Test
    fun listenedRecordsDoNotTouchTheStrip() {
        val log = listOf(rec(ReviewKind.LISTEN, today), rec(ReviewKind.RECENT, today - 1))
        assertEquals(listOf(0, 1, 0), ReportBuilder.strip(log, today - 2, today))
        assertEquals(0, ReportBuilder.strip(log, today, today).single())
    }

    @Test
    fun aTooLongRangeHasNoStripButStillAReport() {
        val long = ReportBuilder.build(sources(), ReportPeriod.Custom, today - 200, today, emptyList())
        assertTrue(long.strip.isEmpty())
        assertEquals(201, long.dayCount)
        val edge = ReportBuilder.build(sources(), ReportPeriod.Custom, today - ReportBuilder.MaxStripDays + 1, today, emptyList())
        assertEquals(ReportBuilder.MaxStripDays, edge.strip.size)
        val over = ReportBuilder.build(sources(), ReportPeriod.Custom, today - ReportBuilder.MaxStripDays, today, emptyList())
        assertTrue(over.strip.isEmpty())
    }

    @Test
    fun oneDayRangeIsAValidReportAndTheDocCarriesTheStrip() {
        val log = listOf(rec(ReviewKind.RECENT, today))
        val report = ReportBuilder.build(sources(), ReportPeriod.Custom, today, today, log)
        assertEquals(1, report.dayCount)
        for (language in AppLanguage.entries) {
            val doc = ReportComposer.compose(report, language, List(114) { "S${it + 1}" })
            assertEquals(listOf(1), doc.strip)
            assertTrue(doc.stripLabel.isNotBlank())
            assertTrue(doc.toPlainText().isNotBlank())
        }
    }

    @Test
    fun emptyRangeReportsNothingWithoutFailing() {
        val report = ReportBuilder.build(sources(), ReportPeriod.Custom, today - 3, today, emptyList())
        assertEquals(0, report.newAyahs)
        assertEquals(0, report.repetitions)
        assertEquals(0, report.activeDays)
        assertEquals(listOf(0, 0, 0, 0), report.strip)
    }
}
