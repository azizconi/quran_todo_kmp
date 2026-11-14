package tj.app.quran_todo.ui.drill

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.HifzItem
import tj.app.quran_todo.ui.hifz.HifzSession
import tj.app.quran_todo.ui.hifz.RecallAudio
import tj.app.quran_todo.ui.hifz.RecallAudioState
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.UiPrefs

/** What the screen of the drill shows. */
@Immutable
sealed interface DrillUi {
    data object Loading : DrillUi

    /** There is no difficult ayah to train (they were all cleared, or none was ever marked). */
    data object Empty : DrillUi

    /**
     * The card of one difficult ayah: [item] being recalled as number [index] of [total], the [session] of its "Recall" (one ayah), the
     * text of the ayah and of the one before it ([previous], null for the first ayah of a surah), the surah it is in.
     */
    data class Card(
        val index: Int,
        val total: Int,
        val item: DrillItem,
        val session: HifzSession,
        val surah: SurahWithAyahs,
        val ayah: AyahEntity,
        val previous: AyahEntity?,
    ) : DrillUi

    /** The end: how many were remembered, how many are still hard, how many were left for the next time. */
    data class Done(val remembered: Int, val stillHard: Int, val more: Int) : DrillUi
}

/** One-off things the screen reacts to. */
sealed interface DrillEvent {
    data object SaveFailed : DrillEvent
}

/**
 * State and commands of the drill of the difficult ayahs (task F2a). The queue is a snapshot of `HifzRepository.hardAyahs()` taken
 * when the screen opens (the marks change while the drill runs, the queue does not). Each ayah is recalled with the same component as
 * the lesson ("Recall": the hint, peeking, "Show", "I remember" / "Hard"); the answers of one surah are written together by
 * `HifzRepository.drillHard` as soon as the last of its ayahs is answered, and what was answered of a group that is still going is
 * written when the person leaves ([exit]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrillViewModel(
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    home: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
) : ViewModel() {
    private val state = MutableStateFlow<DrillState?>(null)
    private val session = MutableStateFlow<HifzSession?>(null)

    private val quran: StateFlow<List<SurahWithAyahs>> = home.uiState
        .map { it.completeQuran }
        .distinctUntilChanged { old, new -> old === new }
        .stateIn(viewModelScope, SharingStarted.Eagerly, home.uiState.value.completeQuran)

    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = surahViewModel.chapterNames

    private val russianNames: StateFlow<Map<Int, String>> = home.uiState
        .map { ui -> ui.surahList.associate { it.surahNumber to it.name } }
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

    /** What the screen shows. */
    val ui: StateFlow<DrillUi> = combine(state, session, quran) { current, card, quran -> build(current, card, quran) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DrillUi.Loading)

    private fun build(current: DrillState?, card: HifzSession?, quran: List<SurahWithAyahs>): DrillUi {
        current ?: return DrillUi.Loading
        if (current.total == 0) return DrillUi.Empty
        if (current.finished) return DrillUi.Done(current.remembered, current.stillHard, current.queue.more)
        val item = current.current ?: return DrillUi.Loading
        card ?: return DrillUi.Loading
        val surah = quran.firstOrNull { it.surah.number == item.surah } ?: return DrillUi.Loading
        val ayahs = surah.ayahs
        val ayah = ayahs.firstOrNull { it.numberInSurah == item.ayah } ?: return DrillUi.Loading
        val previous = current.previousAyah?.let { number -> ayahs.firstOrNull { it.numberInSurah == number } }
        return DrillUi.Card(current.index, current.total, item, card, surah, ayah, previous)
    }

    // -- Starting ---------------------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            val queue = DrillQueues.build(hifz.hardAyahs().first())
            val first = DrillState(queue)
            session.value = first.current?.let(::sessionOf)
            state.value = first
        }
    }

    private fun sessionOf(item: DrillItem): HifzSession =
        HifzSession(item.surah, listOf(HifzItem(QuranFacts.firstAyahNumber(item.surah) + item.ayah - 1, item.ayah)))

    // -- The hint and the peeks --------------------------------------------------------------------------

    val hintMode: StateFlow<HifzHintMode> = uiPrefs.hifzHintMode

    fun setHintMode(mode: HifzHintMode) = uiPrefs.setHifzHintMode(mode)

    fun reveal() {
        session.value = session.value?.reveal()
    }

    fun peekWord(position: Int, word: Int) {
        session.value = session.value?.peekWord(position, word)
    }

    fun peekAyah(position: Int) {
        session.value = session.value?.peekAyah(position)
    }

    fun hideMore(position: Int, wordCount: Int) {
        session.value = session.value?.hideMore(position, wordCount)
    }

    // -- Answering ---------------------------------------------------------------------------------------

    /**
     * Answers the ayah being recalled with [mark]. [early] answers before the reveal (the progressive hint offers that). When this was
     * the last ayah of its surah the answers of the surah are written; after the last ayah the drill is over.
     */
    fun mark(mark: AyahMark, position: Int, early: Boolean) {
        val card = session.value ?: return
        if (card.finished || card.index != position || (!early && !card.revealed)) return
        val current = state.value ?: return
        endListening()
        val step = current.answer(mark)
        state.value = step.state
        session.value = step.state.current?.let(::sessionOf)
        step.write?.let(::write)
        if (step.state.finished) {
            AppTelemetry.logEvent("drill_completed", mapOf("count_bucket" to DrillQueues.countBucket(step.state.total)))
        }
    }

    private val events = Channel<DrillEvent>(Channel.BUFFERED)
    val eventFlow: Flow<DrillEvent> = events.receiveAsFlow()

    private val writes = MutableStateFlow(0)

    /** Something is being written: "Done" waits for it. */
    val saving: StateFlow<Boolean> = writes.map { it > 0 }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val writeMutex = Mutex()

    private fun write(group: DrillWrite) {
        writes.value++
        viewModelScope.launch {
            try {
                // Once started a write is finished, also when the screen is gone.
                withContext(NonCancellable) {
                    writeMutex.withLock {
                        hifz.drillHard(group.surah, group.remembered, group.stillHard, ticker.day.value)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppTelemetry.logError(e, context = "drill_save_failed")
                events.trySend(DrillEvent.SaveFailed)
            } finally {
                writes.value--
            }
        }
    }

    /** The person leaves: what was answered in the group that is still going is written. */
    fun exit() {
        endListening()
        val current = state.value ?: return
        val pending = current.pending() ?: return
        state.value = current.flushed()
        write(pending)
    }

    // -- Listening --------------------------------------------------------------------------------------

    private val audioHolder = MutableStateFlow<RecallAudio?>(null)
    private var audioSurah = 0

    private val audioState: StateFlow<RecallAudioState> = audioHolder
        .flatMapLatest { it?.state ?: flowOf(RecallAudioState.Idle) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecallAudioState.Idle)

    /** The sound of the ayah being recalled plays (or is being prepared): the state of its headphones button. */
    val listening: StateFlow<Boolean> = combine(session, audioState) { card, sound ->
        val current = card?.current
        current != null && sound.playing && sound.ayah == current.inSurah
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The headphones button: plays the ayah being recalled (the reciter of the settings), pauses it while it plays. */
    fun toggleListen(surah: SurahWithAyahs, inSurah: Int) {
        val number = surah.surah.number
        val existing = audioHolder.value
        val audio = if (existing != null && audioSurah == number) {
            existing
        } else {
            existing?.stopAll()
            audioSurah = number
            RecallAudio(playback, number, viewModelScope).also { audioHolder.value = it }
        }
        audio.toggleAyah(surah, inSurah)
    }

    private fun endListening() {
        audioHolder.value?.endListening()
    }

    override fun onCleared() {
        audioHolder.value?.stopAll()
        super.onCleared()
    }
}
