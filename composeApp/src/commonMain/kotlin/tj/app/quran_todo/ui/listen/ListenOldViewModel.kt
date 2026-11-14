package tj.app.quran_todo.ui.listen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.LessonItem
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.playback.ListenItem
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.UiPrefs

/** One passage of today's portion of the circle in the list of the sheet. */
@Immutable
data class ListenRow(
    val passageId: Long,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    /** About how long it takes to listen to it, whole minutes, at least 1. */
    val minutes: Int,
    /** Recalled today (the repetition of the lesson is done); shown, it changes nothing here. */
    val recalled: Boolean,
    /** Has a "listened" record today. */
    val listened: Boolean,
)

/** The queue as the sheet shows it. [ayah] is the ayah of the passage that is heard now (number in the surah), 0 before the first. */
@Immutable
data class ListenQueueUi(
    val position: Int,
    val total: Int,
    val ayah: Int,
    val playing: Boolean,
    val finished: Boolean,
    /** Positions read to the end at least once in this run. */
    val heard: Set<Int>,
    /** Seconds left of the echo silence ("Your turn") and its length; 0 when the reciter reads. */
    val silenceSeconds: Int,
    val silenceTotalSeconds: Int,
    val hasError: Boolean,
    /** The queue reads every ayah with its echo silence. */
    val echo: Boolean,
) {
    val yourTurn: Boolean get() = playing && silenceSeconds > 0
}

@Immutable
data class ListenOldUi(
    val loaded: Boolean = false,
    val rows: List<ListenRow> = emptyList(),
    /** Minutes of one calm recitation of the whole portion. */
    val minutes: Int = 0,
    /** The way chosen for the next start, and for the queue that runs: with the echo silences or once. */
    val echo: Boolean = false,
    val queue: ListenQueueUi? = null,
)

/** One-off things the sheet reacts to. */
sealed interface ListenEvent {
    /** What was heard is written ("Listened"): [count] passages. */
    data class Marked(val count: Int) : ListenEvent
    data object MarkFailed : ListenEvent
}

/**
 * "Listen to the old" (task F1b): the portion of today's manzil circle as a queue of audio. It reads the lesson of the
 * [HifzRepository], starts and steers the queue of the [PlaybackController] (which keeps playing when the sheet is closed, with the
 * screen off) and, when the person says "I repeated it by ear", writes `LISTEN` records through the repository. It never grades:
 * the circle, the ladder and the streak do not know about it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenOldViewModel(
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val home: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
) : ViewModel() {
    private val lesson: Flow<LessonPlan?> = hifz.lesson(ticker.day)
        .map<LessonPlan, LessonPlan?> { it }
        .catch { error ->
            AppTelemetry.logError(throwable = error, context = "listen_old_read_failed")
            emit(null)
        }

    private val listenedToday: Flow<Set<Long>> = ticker.day
        .flatMapLatest { day -> hifz.listenLog(day, day) }
        .map { log -> log.mapNotNullTo(HashSet<Long>()) { it.passageId } as Set<Long> }
        .catch { emit(emptySet()) }

    /** The queue and the sound, boiled down to what the sheet shows (the 5-a-second clock of the audio is left out). */
    private val queueUi: Flow<ListenQueueUi?> = combine(
        playback.listenQueue,
        playback.state
            .map { Core(it.index, it.isPlaying || it.isPreparing, it.pauseRemainingSeconds, it.pauseTotalSeconds, it.hasError) }
            .distinctUntilChanged(),
    ) { queue, core ->
        queue ?: return@combine null
        val item = queue.current
        // The ayah heard now is the session's, but only while the session is on the surah of this passage.
        val ayah = if (item != null && playback.nowPlaying.value?.surahNumber == item.surah) core.index + 1 else item?.startAyah ?: 0
        ListenQueueUi(
            position = queue.position,
            total = queue.items.size,
            ayah = ayah,
            playing = core.playing,
            finished = queue.finished,
            heard = queue.heard,
            silenceSeconds = core.silenceSeconds,
            silenceTotalSeconds = core.silenceTotalSeconds,
            hasError = core.hasError,
            echo = queue.echo,
        )
    }

    private data class Core(
        val index: Int,
        val playing: Boolean,
        val silenceSeconds: Int,
        val silenceTotalSeconds: Int,
        val hasError: Boolean,
    )

    val ui: StateFlow<ListenOldUi> = combine(lesson, listenedToday, queueUi, uiPrefs.listenEcho) { plan, listened, queue, echo ->
        val old = plan?.old.orEmpty()
        ListenOldUi(
            loaded = plan != null,
            rows = old.map { it.toRow(listened) },
            minutes = HifzConstants.minutes(old.sumOf { it.listenSeconds }).coerceAtLeast(if (old.isEmpty()) 0 else 1),
            echo = queue?.echo ?: echo,
            queue = queue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListenOldUi())

    private fun LessonItem.toRow(listened: Set<Long>) = ListenRow(
        passageId = passageId,
        surah = surah,
        startAyah = startAyah,
        endAyah = endAyah,
        minutes = HifzConstants.minutes(listenSeconds).coerceAtLeast(1),
        recalled = done,
        listened = passageId in listened,
    )

    // -- Text -------------------------------------------------------------------------------------------

    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = surahViewModel.chapterNames

    fun onLanguage(language: AppLanguage) {
        surahViewModel.loadChapterNames(language)
    }

    /** The name of a surah by the one rule of the Android UI ([SurahNames]). */
    fun surahTitle(number: Int, language: AppLanguage, names: Map<Int, ChapterNameModel>): String {
        val state = home.uiState.value
        val info = state.completeQuran.firstOrNull { it.surah.number == number }?.surah
        return SurahNames.displayName(
            language = language,
            russianName = state.surahList.firstOrNull { it.surahNumber == number }?.name,
            transliteration = names[number]?.transliteration,
            fallback = info?.englishName?.takeIf { it.isNotBlank() } ?: "№ $number",
        )
    }

    // -- Commands ---------------------------------------------------------------------------------------

    private val events = Channel<ListenEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ListenEvent> = events.receiveAsFlow()

    /** Starts the queue from the passage at [position] (the first by default), once or with the echo as chosen. */
    fun start(position: Int = 0) {
        val rows = ui.value.rows
        if (rows.isEmpty()) return
        val items = rows.map { ListenItem(it.passageId, it.surah, it.startAyah, it.endAyah) }
        playback.startListenQueue(
            items = items,
            loadSurah = ::surahOf,
            prefs = SurahReaderPreferences.load(),
            echo = uiPrefs.listenEcho.value,
            echoRepeats = 1,
            fromPosition = position,
        )
    }

    /** The big button: starts the queue, or pauses and resumes the one that runs. */
    fun toggle() {
        if (playback.listenQueue.value == null) start() else playback.toggle()
    }

    fun next() = playback.nextListenItem()

    fun previous() = playback.previousListenItem()

    /** A tap on a passage of the list: plays from it (starts the queue there when there is none). */
    fun play(position: Int) {
        if (playback.listenQueue.value == null) start(position) else playback.seekListenItem(position)
    }

    /** "Once" or "With echo"; a queue that runs goes on with the new way without a restart. */
    fun setEcho(echo: Boolean) {
        uiPrefs.setListenEcho(echo)
        playback.setListenEcho(echo, echoRepeats = 1)
    }

    /** "Done": the sound stops, what was heard to the end stays to be marked. */
    fun finish() = playback.finishListenQueue()

    /**
     * "Mark: I repeated it by ear": a `LISTEN` record for each passage heard to the end, then the queue is put away. Nothing of
     * the lesson changes.
     */
    fun mark() {
        val queue = playback.listenQueue.value ?: return
        val ids = queue.heardItems.map { it.passageId }
        viewModelScope.launch {
            try {
                hifz.logListened(ids, ticker.day.value)
                playback.endListenQueue()
                events.trySend(ListenEvent.Marked(ids.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppTelemetry.logError(e, context = "listen_old_mark_failed")
                events.trySend(ListenEvent.MarkFailed)
            }
        }
    }

    /** Puts the finished queue away without marking anything. */
    fun discard() = playback.endListenQueue()

    private fun surahOf(number: Int): SurahWithAyahs? =
        home.uiState.value.completeQuran.firstOrNull { it.surah.number == number }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
