package tj.app.quran_todo.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.Manzil
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.home.HomeRows
import tj.app.quran_todo.ui.home.QuranIndex
import tj.app.quran_todo.ui.home.RowsInput
import tj.app.quran_todo.ui.home.SurahRowUi

/** Things the plan screen reacts to once. */
internal sealed interface PlanEvent {
    /** The plan was saved: the screen goes back and says so. */
    data object Saved : PlanEvent

    /** The plan was deleted (the memorized surahs stay). */
    data object Cleared : PlanEvent

    data object Failed : PlanEvent

    /** The plan was paused (or the pause extended): the toast says until when. The screen stays. */
    data class Paused(val untilEpochDay: Int) : PlanEvent

    /** "Continue now": the pause ended. */
    data object Resumed : PlanEvent

    /** Pausing or continuing did not work. */
    data object PauseFailed : PlanEvent
}

/**
 * State of "My plan". The person edits a [PlanDraft], a copy of the plan; the real plan changes only on
 * [save] (through [HifzRepository.setPlan]) or [clear]. The forecast and the time estimate are recomputed
 * live from the draft (the forecast through `HifzRepository.forecast(plan, today)`, which stores nothing).
 *
 * The shared [HomeViewModel] only provides the names of the surahs; it is injected, never put into this
 * entry's ViewModelStore (see docs/redesign/API.md).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class PlanViewModel(
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val home: HomeViewModel,
) : ViewModel() {
    private val draft = MutableStateFlow<PlanDraft?>(null)
    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())
    private val eventChannel = Channel<PlanEvent>(Channel.BUFFERED)

    /** One-shot events. */
    internal val events: Flow<PlanEvent> = eventChannel.receiveAsFlow()

    private class HifzData(
        val plan: HifzPlan?,
        val covered: Map<Int, Int>,
        val statuses: Map<Int, SurahHifzStatus>,
        val oldWeight: Int,
    )

    private val hifzData: Flow<HifzData> = combine(
        hifz.plan().orElse(null),
        hifz.coveredCounts().orElse(emptyMap()),
        hifz.coverageStatuses().orElse(emptyMap()),
        hifz.oldWeight().orElse(0),
        ::HifzData,
    )

    @Volatile
    private var cachedIndex: Pair<List<SurahWithAyahs>, QuranIndex>? = null

    private fun indexFor(complete: List<SurahWithAyahs>): QuranIndex {
        cachedIndex?.takeIf { it.first === complete }?.let { return it.second }
        return QuranIndex.from(complete).also { cachedIndex = complete to it }
    }

    /** The 114 rows with the names in the language of the app; the queue and the search of "add a surah" read them. */
    private val catalog: Flow<List<SurahRowUi>> = combine(
        home.uiState
            .map { Triple(it.surahList, it.chapterNames, it.completeQuran) }
            .distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second && a.third === b.third },
        language,
    ) { (surahList, chapterNames, complete), language ->
        HomeRows.build(
            RowsInput(
                language = language,
                surahList = surahList,
                index = indexFor(complete),
                chapterNames = chapterNames,
                statuses = emptyMap(),
                coveredAyahs = emptyMap(),
                currentPlanSurah = null,
                pinned = emptySet(),
            ),
        )
    }

    private val draftData: Flow<Pair<PlanDraft, HifzData>> = combine(draft.filterNotNull(), hifzData, ::Pair)

    /** The forecast of the draft, recomputed when the draft, the covered ayahs or the day change. */
    private val forecast: Flow<PlanForecast?> = combine(draftData, ticker.day, ::Pair)
        .map { (data, day) -> ForecastKey(data.first, data.second.plan, data.second.covered, day) }
        .distinctUntilChanged()
        .debounce(ComputeDebounceMillis)
        .flatMapLatest { key ->
            if (key.draft.queue.isEmpty()) {
                flowOf(null)
            } else {
                val plan = key.draft.toPlan(
                    startedEpochDay = PlanMath.startDay(key.draft, key.saved, key.today),
                    perDayToSave = PlanMath.effectivePerDay(key.draft, key.covered, key.today),
                )
                hifz.forecast(plan, key.today).orNull()
            }
        }

    /** The new passage of the current surah at the draft's pace and its estimate in minutes. */
    private val estimate: Flow<PaceEstimate?> = combine(draftData, ticker.day, ::Pair)
        .map { (data, day) -> ForecastKey(data.first, data.second.plan, data.second.covered, day) }
        .distinctUntilChanged()
        .debounce(ComputeDebounceMillis)
        .mapLatest { key -> estimateOf(key) }

    /** The pause of the plan (task F2b) that is on or still ahead of today; null when there is none. It follows the changing day. */
    val pause: StateFlow<PlanPause?> = hifz.pause(ticker.day)
        .catch { error ->
            AppTelemetry.logError(throwable = error, context = "plan_pause_read_failed")
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), null)

    val ui: StateFlow<PlanUi> = combine(
        draftData,
        catalog,
        forecast,
        estimate,
        ticker.day,
    ) { (draft, data), catalog, forecast, estimate, today ->
        PlanUiBuilder.build(
            draft = draft,
            saved = data.plan,
            covered = data.covered,
            statuses = data.statuses,
            catalog = catalog,
            forecast = forecast,
            estimate = estimate,
            oldWeight = data.oldWeight,
            today = today,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), PlanUi())

    init {
        // The draft starts as the saved plan (or the first-plan defaults) and is the person's own after that. A saved plan keeps its
        // own circle of the old; a first plan starts with the circle suggested for what is memorized (never silently for a saved one).
        viewModelScope.launch {
            val saved = try {
                hifz.plan().first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "plan_read_failed")
                null
            }
            val suggestedDays = if (saved != null) {
                HifzConstants.MANZIL_DAYS_DEFAULT
            } else {
                try {
                    Manzil.suggestedDays(hifz.oldWeight().first())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppTelemetry.logError(throwable = error, context = "plan_old_weight_failed")
                    HifzConstants.MANZIL_DAYS_DEFAULT
                }
            }
            draft.value = PlanDraft.of(saved, suggestedDays)
        }
    }

    fun setLanguage(next: AppLanguage) {
        language.value = next
    }

    // -- Editing ------------------------------------------------------------------------------

    private fun edit(change: (PlanDraft) -> PlanDraft) {
        draft.value = draft.value?.let(change)
    }

    fun selectScope(scope: HifzScope) = edit { it.withScope(scope) }

    fun editOrder() = edit { it.editOrder() }

    fun move(from: Int, to: Int) = edit { it.move(from, to) }

    fun remove(surah: Int) = edit { it.remove(surah) }

    fun add(surah: Int) = edit { it.add(surah) }

    fun setPerDay(value: Int) = edit { it.withPerDay(value) }

    fun setMode(mode: PaceMode) {
        val current = ui.value
        edit { it.withMode(mode, current.suggestedTarget.takeIf { day -> day > 0 } ?: (ticker.day.value + DefaultDaysAhead)) }
    }

    fun setTarget(epochDay: Int) = edit { it.withTarget(epochDay) }

    fun setManzilDays(days: Int) = edit { it.withManzilDays(days) }

    /** The chip of the hint: sets the circle to the suggested length (nothing is saved until "Save"). */
    fun applySuggestedManzilDays() {
        val hint = ui.value.oldHint ?: return
        edit { it.withManzilDays(hint.suggestedDays) }
    }

    // -- Saving -------------------------------------------------------------------------------

    /** Saves the draft as the plan of the person. */
    fun save() {
        val state = ui.value
        val current = draft.value ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            try {
                hifz.setPlan(
                    scope = current.scope,
                    surahOrder = current.queue,
                    dailyNewAyahs = state.perDay,
                    targetEpochDay = current.savedTarget,
                    manzilDays = current.manzilDays,
                    today = ticker.day.value,
                )
                eventChannel.trySend(PlanEvent.Saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "plan_save_failed")
                eventChannel.trySend(PlanEvent.Failed)
            }
        }
    }

    // -- Pause --------------------------------------------------------------------------------

    /**
     * Pauses the plan for [days] from today, or extends the pause that is on (task F2b): the lessons stop and the calendar of the
     * plan moves (`HifzRepository.pausePlan`, one transaction). Not part of the draft: it is an action of its own.
     */
    fun pausePlan(days: Int) {
        viewModelScope.launch {
            try {
                val today = ticker.day.value
                hifz.pausePlan(days, today)
                AppTelemetry.logEvent("plan_paused", mapOf("days" to days.toString()))
                val until = hifz.pause(today).first()?.untilEpochDay ?: (today + days - 1)
                eventChannel.trySend(PlanEvent.Paused(until))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "plan_pause_failed")
                eventChannel.trySend(PlanEvent.PauseFailed)
            }
        }
    }

    /** "Continue now": the pause ends today and the calendar moves back by the days that were left. */
    fun resumePlan() {
        viewModelScope.launch {
            try {
                hifz.resumePlan(ticker.day.value)
                AppTelemetry.logEvent("plan_resumed_early")
                eventChannel.trySend(PlanEvent.Resumed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "plan_resume_failed")
                eventChannel.trySend(PlanEvent.PauseFailed)
            }
        }
    }

    /** Deletes the plan; what is memorized stays. */
    fun clear() {
        viewModelScope.launch {
            try {
                hifz.clearPlan()
                eventChannel.trySend(PlanEvent.Cleared)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "plan_clear_failed")
                eventChannel.trySend(PlanEvent.Failed)
            }
        }
    }

    // -- Helpers ------------------------------------------------------------------------------

    private suspend fun estimateOf(key: ForecastKey): PaceEstimate? {
        return try {
            val surah = PlanMath.currentSurah(key.draft.queue, key.covered) ?: return null
            val perDay = PlanMath.effectivePerDay(key.draft, key.covered, key.today)
            val coverage = hifz.surahCoverage(surah).first()
            val range = PlanMath.nextPassage(surah, coverage.ayahCount, coverage.passages, perDay) ?: return null
            PaceEstimate(
                surah = surah,
                startAyah = range.startAyah,
                endAyah = range.endAyah,
                minutes = hifz.estimateMinutes(surah, range.startAyah, range.endAyah, isNew = true),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppTelemetry.logError(throwable = error, context = "plan_estimate_failed")
            null
        }
    }

    private class ForecastKey(
        val draft: PlanDraft,
        val saved: HifzPlan?,
        val covered: Map<Int, Int>,
        val today: Int,
    ) {
        override fun equals(other: Any?): Boolean =
            other is ForecastKey && draft == other.draft && saved == other.saved && covered == other.covered && today == other.today

        override fun hashCode(): Int = ((draft.hashCode() * 31 + (saved?.hashCode() ?: 0)) * 31 + covered.hashCode()) * 31 + today
    }

    private companion object {
        const val ComputeDebounceMillis = 120L
        const val StopTimeoutMillis = 5_000L
        const val DefaultDaysAhead = 60
    }
}

/** The same, with "nothing" as the fallback for a read whose value is not nullable. */
private fun <T : Any> Flow<T>.orNull(): Flow<T?> = map<T, T?> { it }.orElse(null)

/** A read that fails reports and continues with [fallback] instead of ending the stream. */
private fun <T> Flow<T>.orElse(fallback: T): Flow<T> = catch { error ->
    AppTelemetry.logError(throwable = error, context = "plan_hifz_read_failed")
    emit(fallback)
}
