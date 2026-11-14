package tj.app.quran_todo.ui.progress.analytics

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.progress.SurahNameSource

/** What the detailed screen shows, built from the whole review log. */
@Immutable
data class AnalyticsUi(
    val ready: Boolean = false,
    val today: Int = 0,
    /** The 114 names, index 0 is surah 1. */
    val names: List<String> = emptyList(),
    val bySurah: List<RetentionRow> = emptyList(),
    val byJuz: List<RetentionRow> = emptyList(),
    /** Years the heat map can show, ascending, and the one it shows. */
    val years: List<Int> = emptyList(),
    val heat: HeatYear? = null,
    val hasPlan: Boolean = false,
    val forecasts: List<SurahForecast> = emptyList(),
    val hardPlaces: List<HardPlace> = emptyList(),
) {
    fun nameOf(surah: Int): String = names.getOrNull(surah - 1) ?: "№ $surah"
}

/**
 * The detailed analytics of the progress (task F5a) on the answers of [HifzRepository]: the whole review log (new, repeated and checked
 * records; "listened" records come from `listenLog` and only tint nothing), the plan with its forecast, the pause, the covered and the
 * difficult ayahs. The arithmetic is [Analytics]; this class only wires the flows and the year the heat map shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel internal constructor(
    private val home: HomeViewModel,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
) : ViewModel() {

    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())
    private val names: Flow<List<String>> = SurahNameSource.names(home, language)
    private val selectedYear = MutableStateFlow<Int?>(null)

    private class Data(
        val today: Int,
        val log: List<ReviewLogEntry>,
        val listenLog: List<ReviewLogEntry>,
        val pause: PlanPause?,
        val plan: HifzPlan?,
        val forecast: PlanForecast?,
        val covered: Map<Int, Set<Int>>,
        val hard: Map<Int, Set<Int>>,
    )

    private val data: Flow<Data> = ticker.day.flatMapLatest { today ->
        combine(
            combine(
                hifz.reviewLog(0, today).orFallback(emptyList()),
                hifz.listenLog(0, today).orFallback(emptyList()),
                hifz.pause(today).orFallback(null),
                ::Triple,
            ),
            combine(
                hifz.plan().orFallback(null),
                hifz.forecast(today).orFallback(null),
                hifz.coveredAyahs().orFallback(emptyMap()),
                hifz.hardAyahs().orFallback(emptyMap()),
            ) { plan, forecast, covered, hard -> arrayOf(plan, forecast, covered, hard) },
        ) { (log, listen, pause), rest ->
            @Suppress("UNCHECKED_CAST")
            Data(
                today = today,
                log = log,
                listenLog = listen,
                pause = pause,
                plan = rest[0] as HifzPlan?,
                forecast = rest[1] as PlanForecast?,
                covered = rest[2] as Map<Int, Set<Int>>,
                hard = rest[3] as Map<Int, Set<Int>>,
            )
        }
    }

    val ui: StateFlow<AnalyticsUi> = combine(data, names, selectedYear) { d, names, picked ->
        val years = Analytics.years(d.log, d.listenLog, d.today)
        val year = picked?.takeIf { it in years } ?: years.last()
        AnalyticsUi(
            ready = true,
            today = d.today,
            names = names,
            bySurah = Analytics.retentionBySurah(d.log, d.today),
            byJuz = Analytics.retentionByJuz(d.log, d.today),
            years = years,
            heat = Analytics.heatmap(year, d.today, d.log, d.listenLog, d.pause),
            hasPlan = d.plan != null,
            forecasts = Analytics.surahForecasts(d.plan, d.forecast, d.covered),
            hardPlaces = Analytics.hardPlaces(d.log, d.hard, d.today),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), AnalyticsUi())

    init {
        home.loadChapterNames(language.value)
        AppTelemetry.logEvent("progress_details_opened")
    }

    fun setLanguage(value: AppLanguage) {
        if (language.value == value) return
        language.value = value
        home.loadChapterNames(value)
    }

    fun onResume() {
        ticker.refresh()
    }

    /** The heat map shows [year] (one of [AnalyticsUi.years]; anything else falls back to the last). */
    fun selectYear(year: Int) {
        selectedYear.value = year
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}

private fun <T> Flow<T>.orFallback(fallback: T): Flow<T> = catch { error ->
    AppTelemetry.logError(throwable = error, context = "analytics_hifz_read_failed")
    emit(fallback)
}
