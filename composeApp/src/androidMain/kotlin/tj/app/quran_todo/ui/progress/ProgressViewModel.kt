package tj.app.quran_todo.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.progress.report.ProgressReport
import tj.app.quran_todo.ui.progress.report.ReportBuilder
import tj.app.quran_todo.ui.progress.report.ReportComposer
import tj.app.quran_todo.ui.progress.report.ReportDoc
import tj.app.quran_todo.ui.progress.report.ReportPeriod
import tj.app.quran_todo.ui.progress.report.toPlainText

/** The report of a period made of what the screen shows right now: the words, the text for the messenger and the last day. */
internal class PreparedReport(
    val period: ReportPeriod,
    val doc: ReportDoc,
    val text: String,
    val toDay: Int,
)

/**
 * State of the progress screen, all of it from [HifzRepository]: the derived statuses, the covered and the difficult ayahs,
 * the review log, the streak, the lesson of the day, the plan and its forecast; the day is [EpochDayTicker.day]. The old tables
 * (`ayah_todo`, `ayah_review`) and the weak-ayah keys of the preferences are not read any more.
 *
 * The shared [HomeViewModel] (injected through Koin, never obtained with `koinViewModel`: see docs/redesign/API.md) is only the
 * source of the names of the surahs; the screen's language is forwarded by [setLanguage].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel internal constructor(
    private val home: HomeViewModel,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
) : ViewModel() {

    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())

    @Volatile
    private var latest: ProgressSources? = null

    private val names: Flow<List<String>> = SurahNameSource.names(home, language)

    /** The review log of the last [ProgressBuilder.LogDays] days; it follows the day. */
    private val log: Flow<List<ReviewLogEntry>> = ticker.day.flatMapLatest { today ->
        hifz.reviewLog(today - ProgressBuilder.LogDays + 1, today)
    }

    /** The "listened" records of the same days ("Listen to the old"): shown as a line, never counted as repetitions. */
    private val listenLog: Flow<List<ReviewLogEntry>> = ticker.day.flatMapLatest { today ->
        hifz.listenLog(today - ProgressBuilder.LogDays + 1, today)
    }

    private class HifzSnapshot(
        val today: Int,
        val statuses: Map<Int, SurahHifzStatus>,
        val covered: Map<Int, Set<Int>>,
        val hard: Map<Int, Set<Int>>,
        val log: List<ReviewLogEntry>,
        val listenLog: List<ReviewLogEntry>,
        val streak: Int,
        val lesson: LessonPlan?,
        val plan: HifzPlan?,
        val forecast: PlanForecast?,
    )

    private val hifzSnapshot: Flow<HifzSnapshot> = combine(
        combine(
            hifz.surahStatuses().orFallback(emptyMap()),
            hifz.coveredAyahs().orFallback(emptyMap()),
            hifz.hardAyahs().orFallback(emptyMap()),
            ::Triple,
        ),
        combine(
            combine(log.orFallback(emptyList()), listenLog.orFallback(emptyList()), ::Pair),
            hifz.streak(ticker.day).orFallback(0),
            ticker.day,
            ::Triple,
        ),
        combine(
            hifz.lesson(ticker.day).map<LessonPlan, LessonPlan?> { it }.orFallback(null),
            hifz.plan().orFallback(null),
            hifz.forecast(ticker.day).orFallback(null),
            ::Triple,
        ),
    ) { (statuses, covered, hard), (logs, streak, today), (lesson, plan, forecast) ->
        HifzSnapshot(today, statuses, covered, hard, logs.first, logs.second, streak, lesson, plan, forecast)
    }

    private val sources: Flow<ProgressSources> = combine(hifzSnapshot, names, language) { snapshot, names, language ->
        ProgressSources(
            today = snapshot.today,
            language = language,
            names = names,
            statuses = snapshot.statuses,
            covered = snapshot.covered,
            hard = snapshot.hard,
            log = snapshot.log,
            listenLog = snapshot.listenLog,
            streak = snapshot.streak,
            lesson = snapshot.lesson,
            plan = snapshot.plan,
            forecast = snapshot.forecast,
        )
    }

    /** The screen: a skeleton until the repository has answered, then the content (or the invitation when nothing was started). */
    val ui: StateFlow<ProgressUi> = sources
        .onEach { latest = it }
        .map { ProgressBuilder.build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), ProgressUi())

    init {
        home.loadChapterNames(language.value)
        AppTelemetry.logEvent("progress_opened")
    }

    /** The app language lives in the app root; the screen forwards it. */
    fun setLanguage(value: AppLanguage) {
        if (language.value == value) return
        language.value = value
        home.loadChapterNames(value)
    }

    /** Back on screen: a new day may have begun (the timer may have slept; the date or the time zone may have changed). */
    fun onResume() {
        ticker.refresh()
    }

    /**
     * The report of [period] as the screen sees things now, in the language of the app; null while nothing has been read yet. The days
     * are the last week or month up to today, or [fromDay]..[toDay] for a range of the date picker; the review log of exactly those
     * days is read for it (the screen holds only 30 days). Pure arithmetic over it ([ReportBuilder], [ReportComposer]).
     */
    internal suspend fun prepareReport(period: ReportPeriod, fromDay: Int? = null, toDay: Int? = null): PreparedReport? {
        val sources = latest ?: return null
        val to = (toDay ?: sources.today).coerceAtMost(sources.today)
        val from = (fromDay ?: (to - period.days + 1)).coerceAtMost(to)
        val log = hifz.reviewLog(from, to).first()
        val report: ProgressReport = ReportBuilder.build(sources, period, from, to, log)
        val doc = ReportComposer.compose(report, sources.language, sources.names)
        return PreparedReport(period, doc, doc.toPlainText(), report.toDay)
    }

    /** The report went to the chooser of the system. Only the period is recorded, never text. */
    internal fun onReportShared(period: ReportPeriod) {
        AppTelemetry.logEvent("report_shared", mapOf("period" to period.analyticsName))
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

/** A read of the hifz model that does not end the stream when it fails: it reports and continues with [fallback]. */
private fun <T> Flow<T>.orFallback(fallback: T): Flow<T> = catch { error ->
    AppTelemetry.logError(throwable = error, context = "progress_hifz_read_failed")
    emit(fallback)
}
