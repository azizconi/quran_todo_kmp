package tj.app.quran_todo.ui.lesson

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.HifzSession
import tj.app.quran_todo.ui.hifz.HifzSessions
import tj.app.quran_todo.ui.hifz.RecallAudio
import tj.app.quran_todo.ui.hifz.RecallAudioState
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.LessonClock
import tj.app.quran_todo.ui.state.UiPrefs

/** What the lesson screen shows. */
@Immutable
sealed interface LessonUi {
    /** The lesson is being read from the database. */
    data object Loading : LessonUi

    /** No plan and nothing to repeat. */
    data object NoPlan : LessonUi

    /** A plan, but nothing is due today (a finished plan, or all of it was moved to later). */
    data class NothingToday(val plan: LessonPlan) : LessonUi

    /** A card to work on. [surah] and [ayahs] are the text of the card's passage; null/empty while the Quran loads. */
    data class Working(
        val plan: LessonPlan,
        val card: LessonCard,
        val progress: CardProgress,
        val surah: SurahWithAyahs?,
        val ayahs: List<AyahEntity>,
    ) : LessonUi

    /** Everything is done. [closedNow] says the last card was finished in this visit (the celebration plays). */
    data class Summary(val plan: LessonPlan, val closedNow: Boolean) : LessonUi
}

/** One-off things the screen reacts to with a toast. */
sealed interface LessonEvent {
    data object SaveFailed : LessonEvent
}

/**
 * State and commands of the lesson screen. The lesson itself comes from the [HifzRepository] for the day of the
 * [EpochDayTicker]; every card is written the moment it is graded (`completeNew`, `review`), so leaving in the middle
 * loses nothing and coming back goes on with the first card that is not done. Only the position INSIDE the current
 * card (the phase, the answers so far) is kept here, and as text by the screen for a process death.
 *
 * The shared view models (Koin singletons) are injected, never obtained through `koinViewModel`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModel(
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val home: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
) : ViewModel() {
    // -- Data -------------------------------------------------------------------------------------------

    private val lesson: StateFlow<LessonPlan?> = hifz.lesson(ticker.day)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The forecast of the plan, for the summary. */
    val forecast: StateFlow<PlanForecast?> = hifz.forecast(ticker.day)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** How many ayahs are marked difficult right now: the summary offers the drill of them (task F2a) when there are any. */
    val hardCount: StateFlow<Int> = hifz.hardAyahs()
        .map { hard -> hard.values.sumOf { it.size } }
        .distinctUntilChanged()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Days in a row with a record in the log, for the summary. */
    val streak: StateFlow<Int> = hifz.streak(ticker.day)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The passages with a "listened" record today ("Listen to the old", task F1b). Only the card of an old passage reads it, to say
     * "Listened"; the lesson itself (what is done, what is left) never does.
     */
    val listenedToday: StateFlow<Set<Long>> = ticker.day
        .flatMapLatest { day -> hifz.listenLog(day, day) }
        .map { log -> log.mapNotNullTo(HashSet<Long>()) { it.passageId } as Set<Long> }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val quran: StateFlow<List<SurahWithAyahs>> = home.uiState
        .map { it.completeQuran }
        .distinctUntilChanged { old, new -> old === new }
        .stateIn(viewModelScope, SharingStarted.Eagerly, home.uiState.value.completeQuran)

    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = surahViewModel.chapterNames

    private val russianNames: StateFlow<Map<Int, String>> = home.uiState
        .map { state -> state.surahList.associate { it.surahNumber to it.name } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, home.uiState.value.surahList.associate { it.surahNumber to it.name })

    fun onLanguage(language: AppLanguage) {
        surahViewModel.loadChapterNames(language)
    }

    /** The name of a surah by the one rule of the Android UI ([SurahNames]). */
    fun surahTitle(number: Int, language: AppLanguage, names: Map<Int, ChapterNameModel>): String {
        val info = quran.value.firstOrNull { it.surah.number == number }?.surah
        return SurahNames.displayName(
            language = language,
            russianName = russianNames.value[number],
            transliteration = names[number]?.transliteration,
            fallback = info?.englishName?.takeIf { it.isNotBlank() } ?: "№ $number",
        )
    }

    fun surahArabicName(number: Int, names: Map<Int, ChapterNameModel>): String =
        names[number]?.arabic ?: quran.value.firstOrNull { it.surah.number == number }?.surah?.name.orEmpty()

    // -- Position inside the card -----------------------------------------------------------------------

    private val progress = MutableStateFlow<CardProgress?>(null)
    private val saving = MutableStateFlow(false)

    /** The position inside the current card, as text for `rememberSaveable`; empty when there is none. */
    val progressText: Flow<String> = progress.map { it?.encode().orEmpty() }.distinctUntilChanged()

    /** The result of the current card is being written. */
    val isSaving: StateFlow<Boolean> = saving

    /** Puts back a position the screen saved as text (a process death); used only before anything else was done. */
    fun restore(text: String) {
        if (progress.value == null) progress.value = CardProgress.decode(text)
    }

    private val celebrating = MutableStateFlow(false)

    /** What the screen shows. */
    val ui: StateFlow<LessonUi> = combine(lesson, progress, quran, celebrating) { plan, saved, quran, closedNow ->
        buildUi(plan, saved, quran, closedNow)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LessonUi.Loading)

    private fun buildUi(plan: LessonPlan?, saved: CardProgress?, quran: List<SurahWithAyahs>, closedNow: Boolean): LessonUi {
        plan ?: return LessonUi.Loading
        if (plan.isEmpty) return if (plan.hasPlan) LessonUi.NothingToday(plan) else LessonUi.NoPlan
        val card = LessonCards.current(plan) ?: return LessonUi.Summary(plan, closedNow)
        val own = saved?.takeIf { it.key == card.key } ?: CardProgress.first(card)
        val surah = quran.firstOrNull { it.surah.number == card.range.surah }
        val ayahs = surah?.ayahs.orEmpty()
            .filter { it.numberInSurah in card.range.startAyah..card.range.endAyah }
            .sortedBy { it.numberInSurah }
        return LessonUi.Working(plan, card, own, surah, ayahs)
    }

    // -- Phases -----------------------------------------------------------------------------------------

    /** "Next" of a phase: goes on to the next phase of the card; entering Recall starts its session. */
    fun next(work: LessonUi.Working) {
        val phase = work.progress.phase.next() ?: return
        goTo(work, phase)
    }

    /** Jumps to a phase of the card (the phase chips of a new passage). */
    fun goTo(work: LessonUi.Working, phase: CardPhase) {
        stopAudio()
        progress.value = when (phase) {
            CardPhase.Recall -> CardProgress(work.card.key, phase, recallSession(work))
            else -> CardProgress(work.card.key, phase, work.progress.recall)
        }
    }

    private fun recallSession(work: LessonUi.Working): HifzSession? {
        val existing = work.progress.recall
        if (existing != null) return existing
        val range = work.card.range
        return HifzSessions.range(range.surah, work.ayahs, range.startAyah, range.endAyah)
    }

    /** Starts the Recall of a card again from its first ayah (after the grade sheet was closed without a result). */
    fun restartRecall(work: LessonUi.Working) {
        stopAudio()
        val range = work.card.range
        progress.value = CardProgress(
            work.card.key,
            CardPhase.Recall,
            HifzSessions.range(range.surah, work.ayahs, range.startAyah, range.endAyah),
        )
    }

    /** How much of a hidden ayah stays readable (the five levels of the hint). */
    val hintMode: StateFlow<HifzHintMode> = uiPrefs.hifzHintMode

    fun setHintMode(mode: HifzHintMode) = uiPrefs.setHifzHintMode(mode)

    /** A tap on the hidden word [word] of the ayah at session position [position]: it opens and counts as one peek. */
    fun peekWord(key: String, position: Int, word: Int) = updateRecall(key, stopsAudio = false) { it.peekWord(position, word) }

    /** Holding the whole ayah at [position] to peek: counts as two peeks. */
    fun peekAyah(key: String, position: Int) = updateRecall(key, stopsAudio = false) { it.peekAyah(position) }

    /** "Hide more" of the progressive hint on the ayah at [position], which has [wordCount] words. */
    fun hideMore(key: String, position: Int, wordCount: Int) = updateRecall(key, stopsAudio = false) { it.hideMore(position, wordCount) }

    /** Answers the ayah at [position] before the reveal (the progressive hint offers the answers while the ayah is partly shown). */
    fun markEarly(key: String, mark: AyahMark, position: Int) = updateRecall(key, stopsAudio = true) { session ->
        if (session.finished || session.index != position) session else session.markWithoutReveal(mark)
    }

    fun reveal(key: String) = updateRecall(key, stopsAudio = false) { it.reveal() }

    /** Answers the ayah at session position [position]; ignored when the session has moved on. */
    fun mark(key: String, mark: AyahMark, position: Int) = updateRecall(key, stopsAudio = true) { session ->
        if (session.finished || session.index != position || !session.revealed) session else session.mark(mark)
    }

    private fun updateRecall(key: String, stopsAudio: Boolean, change: (HifzSession) -> HifzSession) {
        val current = progress.value ?: return
        if (current.key != key || current.phase != CardPhase.Recall) return
        val session = current.recall ?: return
        if (session.finished) return
        val next = change(session)
        if (next == session) return
        if (stopsAudio) stopAudio()
        progress.value = current.copy(recall = next)
    }

    // -- Writing ----------------------------------------------------------------------------------------

    private val events = Channel<LessonEvent>(Channel.BUFFERED)
    val eventFlow: Flow<LessonEvent> = events.receiveAsFlow()

    /**
     * Grades the card: [grade] for the passage and [hard] (numbers in the surah) as its difficult ayahs. A new passage
     * is stored by `completeNew`, a repetition goes to `review`. The lesson recomputes by itself and moves on to the
     * next card; the position inside this card is dropped with the card.
     */
    fun submit(card: LessonCard, grade: ReviewGrade, hard: Set<Int>) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try {
                val today = ticker.day.value
                when (card) {
                    is LessonCard.New -> hifz.completeNew(
                        card.item.surah, card.item.startAyah, card.item.endAyah, grade, hard, today,
                    )
                    is LessonCard.Review -> hifz.review(card.item.passageId, grade, hard, today)
                }
                stopAudio()
                // Hold the controls until the lesson has moved to the next card (the database flow is asynchronous).
                withTimeoutOrNull(NextCardTimeoutMillis) {
                    lesson.filterNotNull().first { plan -> LessonCards.current(plan)?.key != card.key }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppTelemetry.logError(e, context = "lesson_save_failed")
                events.trySend(LessonEvent.SaveFailed)
            } finally {
                saving.value = false
            }
        }
    }

    // -- Telemetry --------------------------------------------------------------------------------------

    private var baselineSeen = false
    private var startedLogged = false
    private var previousComplete = false
    private var previousBlocks: Set<LessonBlock> = emptySet()

    // -- Real lesson time (task U3) ---------------------------------------------------------------------

    private val stopwatch = LessonStopwatch(System::currentTimeMillis).also {
        uiPrefs.lessonClock().let { saved -> it.restore(saved.day, saved.accumulatedMs) }
    }

    /** The lesson of the day may be counted: not while it is known to be over (or to have nothing to do). */
    private var countingAllowed = true

    /** The last durations of completed lessons (seconds), for the line on the summary. */
    val lessonDurations: StateFlow<List<Int>> = uiPrefs.lessonDurations

    /** The lesson screen is in the foreground: the clock runs. */
    fun onForeground() {
        if (!countingAllowed) return
        stopwatch.resume(ticker.day.value)
    }

    /** The screen went to the background or was left: the clock stops and what it counted is kept for the same day. */
    fun onBackground() {
        stopwatch.pause()
        saveClock()
    }

    private fun saveClock() {
        uiPrefs.setLessonClock(LessonClock(stopwatch.day, stopwatch.accumulatedMs))
    }

    private fun discardClock() {
        countingAllowed = false
        stopwatch.clear()
        saveClock()
    }

    init {
        viewModelScope.launch {
            lesson.filterNotNull().collect(::observe)
        }
    }

    /**
     * Events without text: `lesson_started` (the first card is shown and nothing was done today),
     * `lesson_block_done` for each block closed in this visit and `lesson_completed` when the whole lesson is.
     */
    private fun observe(plan: LessonPlan) {
        val blocks = LessonCards.completeBlocks(plan)
        if (!baselineSeen) {
            baselineSeen = true
            previousComplete = plan.isComplete
            previousBlocks = blocks
            // Opened on a finished lesson (or an empty one): there is nothing to measure.
            if (plan.isComplete || plan.isEmpty) discardClock()
            val nothingDone = plan.recent.none { it.done } && plan.old.none { it.done } &&
                plan.newBlock?.state != LessonNewState.DONE
            if (!plan.isEmpty && !plan.isComplete && nothingDone && !startedLogged) {
                startedLogged = true
                AppTelemetry.logEvent("lesson_started")
            }
            return
        }
        (blocks - previousBlocks).forEach { block ->
            AppTelemetry.logEvent("lesson_block_done", mapOf("block" to block.name.lowercase()))
        }
        if (!previousComplete && plan.isComplete) {
            celebrating.value = true
            val elapsedMs = stopwatch.elapsedMs()
            discardClock()
            val seconds = (elapsedMs / 1000L).toInt()
            uiPrefs.addLessonDuration(seconds)
            AppTelemetry.logEvent(
                "lesson_completed",
                mapOf(
                    "minutes_bucket" to LessonCards.minutesBucket(plan.totalMinutes),
                    "minutes" to LessonDurations.minutesOf(elapsedMs).toString(),
                ),
            )
        }
        previousComplete = plan.isComplete
        previousBlocks = blocks
    }

    // -- Listening --------------------------------------------------------------------------------------

    private val audioHolder = MutableStateFlow<RecallAudio?>(null)
    private var audioSurah = 0

    /** What the lesson's own sound is doing (the ayah being heard and whether it plays). */
    val audioState: StateFlow<RecallAudioState> = audioHolder
        .flatMapLatest { it?.state ?: flowOf(RecallAudioState.Idle) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecallAudioState.Idle)

    /** The sound of the ayah being recalled plays (or is being prepared): the state of its headphones button. */
    val listeningCurrent: StateFlow<Boolean> = combine(progress, audioState) { position, sound ->
        val current = position?.recall?.current
        current != null && sound.playing && sound.ayah == current.inSurah
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun audioFor(surah: Int): RecallAudio {
        val current = audioHolder.value
        if (current != null && audioSurah == surah) return current
        current?.stopAll()
        audioSurah = surah
        return RecallAudio(playback, surah, viewModelScope).also { audioHolder.value = it }
    }

    /** "Listen" phase: plays or pauses the passage (every ayah N times, then the passage M times). */
    fun toggleListen(work: LessonUi.Working) {
        val surah = work.surah ?: return
        val range = work.card.range
        val sequence = ListenPlan.sequence(
            range.startAyah, range.endAyah, uiPrefs.lessonAyahRepeats.value, uiPrefs.lessonPassageRepeats.value,
        )
        audioFor(range.surah).toggleSequence(surah, sequence, echo = uiPrefs.lessonEcho.value)
    }

    /** "Listen first" of a repetition: the passage once. */
    fun toggleListenOnce(work: LessonUi.Working) {
        val surah = work.surah ?: return
        val range = work.card.range
        audioFor(range.surah).toggleSequence(surah, ListenPlan.once(range.startAyah, range.endAyah))
    }

    /** One ayah: a tap in the "Read" phase, the headphones in "Recall". */
    fun toggleListenAyah(work: LessonUi.Working, inSurah: Int) {
        val surah = work.surah ?: return
        audioFor(work.card.range.surah).toggleAyah(surah, inSurah)
    }

    /** Starts the Listen sound again from the beginning. */
    fun replayListen(work: LessonUi.Working) {
        val surah = work.surah ?: return
        val range = work.card.range
        val sequence = ListenPlan.sequence(
            range.startAyah, range.endAyah, uiPrefs.lessonAyahRepeats.value, uiPrefs.lessonPassageRepeats.value,
        )
        audioFor(range.surah).restartSequence(surah, sequence, echo = uiPrefs.lessonEcho.value)
    }

    /** Silences the lesson's own sound (a phase or a card is over). */
    fun stopAudio() {
        audioHolder.value?.stopAll()
    }

    override fun onCleared() {
        stopAudio()
        super.onCleared()
    }

    private companion object {
        /** The most the controls wait for the lesson to show the next card after a result was written. */
        const val NextCardTimeoutMillis = 2_500L
    }
}
