package tj.app.quran_todo.ui.khatm

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.khatm.JuzPassages
import tj.app.quran_todo.khatm.KhatmMath
import tj.app.quran_todo.khatm.KhatmPlan
import tj.app.quran_todo.khatm.KhatmState
import tj.app.quran_todo.khatm.RamadanCalendar
import tj.app.quran_todo.khatm.RamadanStatus
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.playback.ListenItem
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.UiPrefs

/** What the cards of the home screen and the plan screen need. */
@Immutable
data class KhatmUi(
    val loaded: Boolean = false,
    /** Today as the reading plan sees it (the debug build may move it, see [DebugToday]). */
    val today: Int = 0,
    val state: KhatmState? = null,
    val ramadan: RamadanStatus = RamadanStatus.Outside,
    /** The page the person last read in the mushaf or the text (from the saved position), null when unknown. */
    val readPage: Int? = null,
)

enum class KhatmEvent { Marked, Created, Deleted, MonthPlanCreated, Failed, ListenFailed }

/**
 * The reading plan (khatm) and Ramadan (task F5b). The plan lives in `hifz_meta` ([HifzRepository.khatm]); nothing here writes the review
 * log or touches the lesson. "Juz of the night" plays the juz as a listening queue ([PlaybackController.startListenQueue]) whose items carry a
 * negative `passageId`: they are not passages of the lesson, so nothing is ever marked from them.
 */
class KhatmViewModel(
    context: Context,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
    private val home: HomeViewModel,
) : ViewModel() {
    init {
        DebugToday.load(context)
    }

    private val today: StateFlow<Int> = combine(ticker.day, DebugToday.override) { real, override -> override ?: real }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ticker.day.value)

    private val state: Flow<KhatmState?> = hifz.khatm().catch { error ->
        AppTelemetry.logError(throwable = error, context = "khatm_read_failed")
        emit(null)
    }

    val ui: StateFlow<KhatmUi> = combine(state, today, uiPrefs.lastReadPosition) { plan, day, last ->
        KhatmUi(
            loaded = true,
            today = day,
            state = plan,
            ramadan = RamadanCalendar.status(day, IcuHijriConverter),
            readPage = last?.takeIf { it.surah in 1..114 }?.let { MushafPages.pageOf(it.surah, it.ayahInSurah ?: 1) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KhatmUi(today = today.value))

    private val events = Channel<KhatmEvent>(Channel.BUFFERED)
    val eventFlow: Flow<KhatmEvent> = events.receiveAsFlow()

    init {
        // The queue of "Juz of the night" ends by itself after its last passage: the mini player and the notification go away.
        viewModelScope.launch {
            playback.listenQueue.collect { queue ->
                if (queue != null && queue.finished && queue.items.firstOrNull()?.passageId?.let { it < 0 } == true) {
                    playback.endListenQueue()
                }
            }
        }
    }

    /** A new plan: [days] days from today (a preset), or to the day [targetEpochDay], from the first page or from [fromPage]. */
    fun create(days: Int?, targetEpochDay: Int?, fromPage: Int) {
        val day = today.value
        val plan = when {
            days != null -> KhatmMath.planForDays(days, day, fromPage)
            targetEpochDay != null -> KhatmMath.planToDate(targetEpochDay, day, fromPage)
            else -> return
        }
        save(plan, keepProgress = false, event = KhatmEvent.Created)
    }

    /** The plan of the whole mushaf for the month of Ramadan, from today to its last day. */
    fun createMonthPlan() {
        val status = ui.value.ramadan
        val plan = RamadanCalendar.monthPlan(status, today.value) ?: return
        save(plan, keepProgress = false, event = KhatmEvent.MonthPlanCreated)
    }

    /** Another end day for the plan: what is read stays, the daily portion is worked out again. */
    fun changeTarget(targetEpochDay: Int) {
        val current = ui.value.state ?: return
        save(KhatmMath.withTarget(current.plan, targetEpochDay, today.value), keepProgress = true, event = null)
    }

    fun markPortionRead() = run(KhatmEvent.Marked) { hifz.markKhatmPortionRead(today.value) }

    fun markUpTo(page: Int) = run(KhatmEvent.Marked) { hifz.markKhatmUpTo(page, today.value) }

    fun delete() = run(KhatmEvent.Deleted) { hifz.clearKhatm() }

    /** "Listen to the juz": the juz of the night as a queue by surah; the notification shows the surah and the ayah. */
    fun listenJuz(juz: Int) {
        val quran = home.uiState.value.completeQuran
        val items = JuzPassages.of(juz).map { range ->
            ListenItem(passageId = -juz.toLong(), surah = range.surah, startAyah = range.startAyah, endAyah = range.endAyah)
        }
        if (quran.isEmpty() || items.isEmpty()) {
            events.trySend(KhatmEvent.ListenFailed)
            return
        }
        playback.startListenQueue(
            items = items,
            loadSurah = { number -> quran.firstOrNull { it.surah.number == number } },
            prefs = SurahReaderPreferences.load(),
            echo = false,
        )
        AppTelemetry.logEvent("ramadan_juz_listen", mapOf("juz" to juz.toString()))
    }

    private fun save(plan: KhatmPlan, keepProgress: Boolean, event: KhatmEvent?) =
        run(event) { hifz.saveKhatm(plan, keepProgress) }

    private fun run(success: KhatmEvent?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (success != null) events.trySend(success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "khatm_write_failed")
                events.trySend(KhatmEvent.Failed)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
