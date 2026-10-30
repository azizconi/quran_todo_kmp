package tj.app.quran_todo.ui.progress

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.ui.progress.report.ReportBuilder
import tj.app.quran_todo.ui.progress.report.ReportPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Listened" records (task F1b) on the progress screen and in the report: they are shown as a line of their own ("Listened: N
 * passages") and are never counted as repetitions - not in the bars, the retention, the active days, the report. Whatever the log
 * holds of them, those numbers are what they are without them.
 */
class ListenProgressTest {
    private val today = 20_000

    private val passages: List<Passage> = listOf(
        Fx.passage(3, 113, 1, 5),
        Fx.passage(4, 114, 1, 6),
    )
    private val plan: HifzPlan = Fx.plan(listOf(114, 113, 112), daily = 5, started = today - 20, scope = HifzScope.CUSTOM)

    private fun entry(kind: ReviewKind, passageId: Long?, surah: Int, start: Int, end: Int, day: Int, grade: ReviewGrade = ReviewGrade.GOOD) =
        Fx.log(passageId, surah, start, end, kind, grade, day)

    private val study = listOf(
        entry(ReviewKind.NEW, null, 112, 1, 4, today),
        entry(ReviewKind.OLD, 3, 113, 1, 5, today - 1),
        entry(ReviewKind.RECENT, 4, 114, 1, 6, today - 2, ReviewGrade.FORGOT),
    )

    // Listening to every passage on several days, one of them twice on the same day.
    private val listened = listOf(
        entry(ReviewKind.LISTEN, 3, 113, 1, 5, today),
        entry(ReviewKind.LISTEN, 4, 114, 1, 6, today),
        entry(ReviewKind.LISTEN, 3, 113, 1, 5, today),
        entry(ReviewKind.LISTEN, 3, 113, 1, 5, today - 3),
        entry(ReviewKind.LISTEN, 4, 114, 1, 6, today - 40), // out of the window
    )

    private fun sources(log: List<ReviewLogEntry>, listenLog: List<ReviewLogEntry> = emptyList(), covered: Boolean = true): ProgressSources {
        val metrics = Fx.flat
        val shown = if (covered) passages else emptyList()
        val coverage = Coverage(shown, metrics)
        return ProgressSources(
            today = today,
            language = AppLanguage.EN,
            names = List(114) { "S${it + 1}" },
            statuses = HifzStatuses.all(shown, plan, metrics),
            covered = (1..114).filter { coverage.coveredCount(it) > 0 }.associateWith { coverage.coveredAyahs(it) },
            hard = HifzStatuses.hardAyahs(shown),
            log = log,
            streak = 2,
            lesson = null,
            plan = plan,
            forecast = Forecast.compute(plan, coverage, log.filter { it.kind == ReviewKind.NEW }, today),
            listenLog = listenLog,
        )
    }

    @Test
    fun retentionIgnoresListening() {
        val without = ProgressBuilder.retention(study, today - 29, today)
        val with = ProgressBuilder.retention(study + listened, today - 29, today)
        assertEquals(without, with)
        assertEquals(2, with.repetitions)
        // Only listening: no repetition at all, so no number.
        assertEquals(Retention(null, 0), ProgressBuilder.retention(listened, today - 29, today))
    }

    @Test
    fun theBarsAndTheActiveDaysIgnoreListening() {
        assertEquals(ProgressBuilder.activity(study, today), ProgressBuilder.activity(study + listened, today))
        assertEquals(
            ProgressBuilder.activeDays(study, today - 29, today),
            ProgressBuilder.activeDays(study + listened, today - 29, today),
        )
        // A day of listening alone is not an active day.
        assertEquals(0, ProgressBuilder.activeDays(listened, today - 29, today))
        assertTrue(ProgressBuilder.activity(listened, today).all { it.total == 0 })
    }

    @Test
    fun listenedCountsDifferentPassagesPerDay() {
        val perDay = ProgressBuilder.listened(listened, today)
        // Today: passages 3 and 4 (3 listened twice counts once); three days ago: passage 3; 40 days ago: out of the window.
        assertEquals(mapOf(today to 2, today - 3 to 1), perDay)
        // Records of the study are not "listened".
        assertTrue(ProgressBuilder.listened(study, today).isEmpty())
    }

    @Test
    fun theScreenShowsListenedOnTheDayAndLeavesTheBarsAlone() {
        val ui = ProgressBuilder.build(sources(study, listened))
        val last = ui.activity.days.last()
        assertEquals(2, last.listened)
        assertEquals(2, ui.activity.listenedToday)
        assertEquals(3, ui.activity.totalListened)
        assertEquals(ProgressBuilder.activity(study, today).map { it.total }, ui.activity.days.map { it.total })
        assertEquals(ProgressBuilder.activity(study, today).map { it.learned to it.reviewed }, ui.activity.days.map { it.learned to it.reviewed })
        assertEquals(1, ui.activity.days[ui.activity.days.size - 4].listened)
        // The tiles that are about studying do not move.
        val plain = ProgressBuilder.build(sources(study))
        assertEquals(plain.tiles, ui.tiles)
        assertEquals(plain.activity.peak, ui.activity.peak)
        assertEquals(plain.activity.isEmpty, ui.activity.isEmpty)
    }

    @Test
    fun nothingButListeningDoesNotLeaveTheEmptyState() {
        val ui = ProgressBuilder.build(sources(listened, listened, covered = false))
        assertEquals(ProgressPhase.Empty, ui.phase)
        assertTrue(ui.activity.isEmpty)
    }

    @Test
    fun theReportOfTheStudyIsTheSameWithListeningInTheLog() {
        listOf(ReportPeriod.Week, ReportPeriod.Month).forEach { period ->
            assertEquals(
                ReportBuilder.build(sources(study), period),
                ReportBuilder.build(sources(study + listened), period),
                period.name,
            )
        }
    }
}
