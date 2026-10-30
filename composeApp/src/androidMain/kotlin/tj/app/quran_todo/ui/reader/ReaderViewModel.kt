package tj.app.quran_todo.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.home.StatusStep
import tj.app.quran_todo.ui.home.SurahStatusChange
import tj.app.quran_todo.ui.home.SurahTarget
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.playback.ListenQueue
import tj.app.quran_todo.ui.playback.NowPlaying
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.playback.SleepTimer
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * What the player dock and the list know about audio. It deliberately carries no position or duration:
 * those change every 200 ms and live in their own flows ([ReaderViewModel.audioProgress], ...), read by
 * the leaves that draw them, so the list never recomposes because of the audio clock.
 */
@Immutable
data class PlayerUi(
    /** The shared audio session belongs to this surah. */
    val active: Boolean,
    /** Ayah index the dock shows: the session's when [active], else the reader's own "ready" position. */
    val index: Int,
    val isPlaying: Boolean,
    val canResume: Boolean,
    val isPreparing: Boolean,
    val hasError: Boolean,
    /** The person picked an ayah (tap, previous/next) as opposed to the index just following reading. */
    val selected: Boolean,
    /** Another surah is playing: its number and the number (in the surah) of its current ayah. */
    val elsewhereSurah: Int? = null,
    val elsewhereAyah: Int? = null,
) {
    /** Audio of this surah is playing or paused in the middle of an ayah. */
    val running: Boolean get() = active && (isPlaying || canResume || isPreparing)

    /** Index of the ayah the list marks as current, or null. */
    fun highlightIndex(highlightPlaying: Boolean): Int? = when {
        running -> if (highlightPlaying) index else null
        selected -> index
        else -> null
    }
}

@Immutable
data class NextSurahDisplay(val title: String, val meaning: String, val arabicName: String)

@Immutable
data class AudioTime(val positionSeconds: Int, val durationSeconds: Int)

/** One-off things the screen reacts to with a toast, a haptic or a celebration. */
sealed interface ReaderEvent {
    data object SurahLearned : ReaderEvent
    data object StatusFailed : ReaderEvent
}

/** When an ayah that is learned comes back (the line under the title of the ayah sheet). */
sealed interface AyahSchedule {
    /** The passage is OLD: it is in the manzil circle. */
    data object Circle : AyahSchedule

    /** The passage is on the ladder and is due in [days] days. */
    data class InDays(val days: Int) : AyahSchedule

    /** The passage is on the ladder and is due today or overdue. */
    data object Due : AyahSchedule
}

/**
 * State of one reader (one `Dest.Reader` back stack entry). The shared HomeViewModel and SurahViewModel
 * (Koin singletons) come in through the constructor; the notes of this surah are read from the DAO here so no
 * collector piles up in a singleton. What is learned, difficult or due comes from the [HifzRepository] (the only
 * writer of progress), never from the old tables.
 *
 * Audio belongs to [PlaybackController]: while the shared session plays another surah this reader keeps its
 * own "ready" position and only takes the session over when the person presses play or picks an ayah to
 * hear.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    val route: Dest.Reader,
    private val homeViewModel: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    ayahNoteDao: AyahNoteDao,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
) : ViewModel() {
    val surahNumber: Int = route.surah

    // -- Data ---------------------------------------------------------------------------------------

    private val _surah = MutableStateFlow<SurahWithAyahs?>(null)

    /** The surah with its ayahs sorted by number in the surah; null while the Quran is loading. */
    val surah: StateFlow<SurahWithAyahs?> = _surah

    /**
     * Index of the ayah the list opens on. Valid once [surah] is not null, and then never changes: the list
     * state is created from it before the first composition.
     */
    var initialAyahIndex: Int = 0
        private set

    /** The ayah to flash for a moment on opening (the one a search result pointed at), or null. */
    var initialFlashIndex: Int? = null
        private set

    /**
     * Where a juz, a quarter of a hizb or a page starts at each ayah, and the sajda signs (D1): one entry per ayah of [surah], in
     * its order. Worked out once, when the surah arrives, so the rows only read it. Valid once [surah] is not null.
     */
    var marks: List<AyahMarks> = emptyList()
        private set

    /** The juz, hizb and page of each ayah of [surah] (the "Juz 2 · Hizb 3 · p. 22" line of the top bar); valid with [marks]. */
    var places: List<MushafPlace> = emptyList()
        private set

    private val lastReadAtOpen = uiPrefs.lastReadPosition.value

    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = surahViewModel.chapterNames

    /** Covered, difficult and due ayahs and the passages of this surah; null until the first read. */
    val coverage: StateFlow<SurahCoverage?> = hifz.surahCoverage(surahNumber)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Learned ayahs by GLOBAL ayah number (`AyahEntity.number`), for the rosette rings. */
    val ayahStatuses: StateFlow<Map<Int, AyahTodoStatus>> = coverage
        .map { c -> c?.coveredAyahs.orEmpty().associate { globalNumber(it) to AyahTodoStatus.LEARNED } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Notes by global ayah number. */
    val notes: StateFlow<Map<Int, AyahNoteEntity>> = ayahNoteDao.getBySurahNumber(surahNumber)
        .map { list -> list.associateBy { it.ayahNumber } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Global numbers of the difficult ayahs of this surah (the ones marked while checking oneself). */
    val weakAyahs: StateFlow<Set<Int>> = coverage
        .map { c -> c?.hardAyahs.orEmpty().mapTo(HashSet()) { globalNumber(it) } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private fun globalNumber(ayahInSurah: Int): Int = QuranFacts.firstAyahNumber(surahNumber) + ayahInSurah - 1

    /** Loading the Quran failed (and so there is no surah to show). */
    val loadFailed: StateFlow<Boolean> = homeViewModel.uiState
        .map { it.quranLoadState is QuranLoadState.Error }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retryLoading() = homeViewModel.refreshQuran()

    private val _translations = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** Translations by global ayah number; kept across a language change until the new ones arrive. */
    val translations: StateFlow<Map<Int, String>> = _translations

    private var language: AppLanguage? = null

    // -- Surah status -----------------------------------------------------------------------------------

    /** The status of the surah, derived from its passages (and from the plan: the surah being learned now). */
    val surahStatus: StateFlow<SurahTodoStatus?> = coverage
        .map { c ->
            when (c?.status) {
                SurahHifzStatus.LEARNED -> SurahTodoStatus.LEARNED
                SurahHifzStatus.LEARNING -> SurahTodoStatus.LEARNING
                null -> null
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val eventChannel = Channel<ReaderEvent>(Channel.BUFFERED)
    val events: Flow<ReaderEvent> = eventChannel.receiveAsFlow()

    // -- Settings -----------------------------------------------------------------------------------------

    private val _prefs = MutableStateFlow(SurahReaderPreferences.load())
    val prefs: StateFlow<SurahReaderPreferences> = _prefs

    private val _practice = MutableStateFlow(ReaderPracticeSettings())

    /** Repeat/pause settings of this surah. */
    val practice: StateFlow<ReaderPracticeSettings> = _practice

    /**
     * The translation is switched on but could not be loaded (offline, server error): the first ayah then shows
     * "Translation not loaded · Retry" in place of the translation.
     */
    val translationMissing: StateFlow<Boolean> =
        combine(surahViewModel.translationFailedSurah, _prefs, _translations) { failedSurah, prefs, translations ->
            failedSurah == surahNumber && prefs.showTranslation && translations.isEmpty()
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** "Retry" of the placeholder: loads the translation again, past the memory of failed requests. */
    fun retryTranslation() {
        val language = language ?: return
        val surah = _surah.value ?: return
        surahViewModel.loadTranslations(surahNumber, language, surah.ayahs.size, force = true)
    }

    // -- Player -------------------------------------------------------------------------------------------

    private val readyIndex = MutableStateFlow(0)
    private val selected = MutableStateFlow(false)

    private data class SessionCore(
        val index: Int,
        val isPlaying: Boolean,
        val canResume: Boolean,
        val isPreparing: Boolean,
        val hasError: Boolean,
    )

    private val sessionCore: Flow<SessionCore> = playback.state
        .map { SessionCore(it.index, it.isPlaying, it.canResume, it.isPreparing, it.hasError) }
        .distinctUntilChanged()

    val player: StateFlow<PlayerUi> = combine(playback.nowPlaying, sessionCore, readyIndex, selected) { now, core, ready, picked ->
        playerUi(now, core, ready, picked)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playerUi(playback.nowPlaying.value, playback.state.value.toCore(), readyIndex.value, selected.value),
    )

    /** Progress (0..1) of the ayah being played; 0 unless the session belongs to this surah. */
    val audioProgress: StateFlow<Float> = combine(playback.nowPlaying, playback.state) { now, s ->
        if (now?.surahNumber == surahNumber && s.durationMs > 0) {
            (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val audioTime: StateFlow<AudioTime> = combine(playback.nowPlaying, playback.state) { now, s ->
        if (now?.surahNumber == surahNumber) {
            AudioTime((s.positionMs / 1000).toInt(), (s.durationMs / 1000).toInt())
        } else {
            AudioTime(0, 0)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioTime(0, 0))

    /** Seconds left of the pause between ayahs (the countdown ring around play); 0 when there is none. */
    val pauseRemainingSeconds: StateFlow<Int> = combine(playback.nowPlaying, playback.state) { now, s ->
        if (now?.surahNumber == surahNumber) s.pauseRemainingSeconds else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The whole silence the countdown above runs in (the pause, or the echo silence): the ring around play is full at this. */
    val pauseTotalSeconds: StateFlow<Int> = combine(playback.nowPlaying, playback.state) { now, s ->
        if (now?.surahNumber == surahNumber) s.pauseTotalSeconds else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The listening queue ("Listen to the old") while there is one; the dock says which passage it is at. */
    val listenQueue: StateFlow<ListenQueue?> = playback.listenQueue

    val sleepTimer: StateFlow<SleepTimer> = playback.sleepTimer
    val sleepRemainingSeconds: StateFlow<Int> = playback.sleepRemainingSeconds

    /** Audio plays right now, in this or in another surah (keeps the screen awake). */
    val anythingPlaying: StateFlow<Boolean> = playback.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playback.state.value.isPlaying)

    private fun playerUi(now: NowPlaying?, core: SessionCore, ready: Int, picked: Boolean): PlayerUi {
        val active = now?.surahNumber == surahNumber
        return if (active) {
            PlayerUi(
                active = true,
                index = core.index,
                isPlaying = core.isPlaying,
                canResume = core.canResume,
                isPreparing = core.isPreparing,
                hasError = core.hasError,
                selected = picked,
            )
        } else {
            val playingElsewhere = now != null && core.isPlaying
            PlayerUi(
                active = false,
                index = ready,
                isPlaying = false,
                canResume = false,
                isPreparing = false,
                hasError = false,
                selected = picked,
                elsewhereSurah = if (playingElsewhere) now?.surahNumber else null,
                elsewhereAyah = if (playingElsewhere) now?.ayahs?.getOrNull(core.index)?.numberInSurah else null,
            )
        }
    }

    private fun tj.app.quran_todo.common.audio.ReaderAudioState.toCore() =
        SessionCore(index, isPlaying, canResume, isPreparing, hasError)

    // -- Reading position ---------------------------------------------------------------------------------

    /** [NotMeasured] until the list was laid out, [HeaderOnTop] while the header covers the top, else an ayah index. */
    private val visibleAyah = MutableStateFlow(NotMeasured)
    private var lastSavedAyah = -1
    private var initialPositionSaved = false

    init {
        uiPrefs.recordRecentSurah(surahNumber)

        homeViewModel.uiState.value.completeQuran.findSurah()?.let(::onSurahAvailable)
        viewModelScope.launch {
            homeViewModel.uiState
                .map { it.completeQuran }
                .distinctUntilChanged { old, new -> old === new }
                .collect { quran ->
                    if (_surah.value == null) quran.findSurah()?.let(::onSurahAvailable)
                }
        }
        viewModelScope.launch {
            surahViewModel.ayahTranslations.collect { map ->
                val ayahs = _surah.value?.ayahs ?: return@collect
                if (map.isEmpty()) return@collect
                val own = ayahs.mapNotNull { ayah -> map[ayah.number]?.let { ayah.number to it } }.toMap()
                if (own.isNotEmpty()) _translations.value = own
            }
        }
        viewModelScope.launch {
            // The translation of this surah could not be loaded: the one of the previous language (kept while the
            // new one is on its way) must not stay on screen, the retry hint takes its place.
            surahViewModel.translationFailedSurah.collect { failedSurah ->
                if (failedSurah == surahNumber) _translations.value = emptyMap()
            }
        }
        viewModelScope.launch {
            // Reading position: written 400 ms after the first visible ayah stops changing.
            visibleAyah.debounce(400).collect { index -> commitVisibleAyah(index) }
        }
    }

    private fun List<SurahWithAyahs>.findSurah(): SurahWithAyahs? = firstOrNull { it.surah.number == surahNumber }

    private fun onSurahAvailable(raw: SurahWithAyahs) {
        if (_surah.value != null) return
        val sorted = raw.copy(ayahs = raw.ayahs.sortedBy { it.numberInSurah })
        val ayahs = sorted.ayahs
        initialAyahIndex = resolveInitialAyahIndex(
            surahNumber = surahNumber,
            requestedAyah = route.ayah,
            rangeStart = route.rangeStart,
            lastRead = lastReadAtOpen,
            ayahs = ayahs,
        )
        initialFlashIndex = if (route.ayah != null) initialAyahIndex else null
        readyIndex.value = initialAyahIndex
        marks = MushafMarks.marksOfSurah(surahNumber, ayahs, homeViewModel.uiState.value.completeQuran)
        places = ayahs.map(MushafMarks::placeOf)
        _practice.value = ReaderPracticeSettings.load(surahNumber, ayahs.size)
        _surah.value = sorted
        // The language may have been reported before the Quran was there.
        ensureTranslations()
    }

    private var openHandled = false

    /**
     * Called once by the screen when it is shown for the first time. Starts the audio when the person asked
     * for "autoplay on open" and nothing is playing; a session that already belongs to this surah (opened
     * from the mini player) or plays another surah is left alone.
     */
    fun onOpened() {
        if (openHandled || _surah.value == null) return
        openHandled = true
        if (_prefs.value.autoplayOnOpen && !route.hifz && !isActive() && !playback.state.value.isPlaying) {
            startHere(initialAyahIndex, autoplay = true)
        }
    }

    /** Called with the language of the app; loads chapter names and, when shown, the translation. */
    fun onLanguage(language: AppLanguage) {
        this.language = language
        surahViewModel.loadChapterNames(language)
        ensureTranslations()
    }

    /** Loads the translation again if another reader replaced it in the shared SurahViewModel. */
    fun ensureTranslations() {
        val language = language ?: return
        val surah = _surah.value ?: return
        if (_prefs.value.showTranslation) {
            surahViewModel.loadTranslations(surahNumber, language, surah.ayahs.size)
        }
    }

    /** Re-reads the player preferences that another screen (settings) may have changed. */
    fun reloadPrefs() {
        val loaded = SurahReaderPreferences.load()
        if (loaded != _prefs.value) {
            _prefs.value = loaded
            ensureTranslations()
        }
    }

    // -- Reading position ---------------------------------------------------------------------------------

    /** Index of the first ayah on screen, [HeaderOnTop] while the header covers the top of the list. */
    fun onVisibleAyah(index: Int) {
        visibleAyah.value = index
        if (!initialPositionSaved && index != NotMeasured) {
            // The list has just been positioned: the place the reader opened on is already the reading
            // position (Home's "Continue" must not wait for a scroll).
            initialPositionSaved = true
            commitVisibleAyah(index)
        }
    }

    /** Writes the pending reading position right away (the screen is being left). */
    fun flushReadingPosition() {
        commitVisibleAyah(visibleAyah.value)
    }

    private fun commitVisibleAyah(index: Int) {
        val ayahs = _surah.value?.ayahs ?: return
        if (index == NotMeasured) return
        // While the header covers the top the person is at the start of the surah: ayah 1.
        val ayahIndex = if (index == HeaderOnTop) 0 else index
        val ayah = ayahs.getOrNull(ayahIndex) ?: return
        if (!selected.value && !isActive()) readyIndex.value = ayahIndex
        if (ayah.numberInSurah != lastSavedAyah) {
            lastSavedAyah = ayah.numberInSurah
            uiPrefs.saveLastRead(surahNumber, ayah.numberInSurah)
        }
    }

    // -- Surah and ayah state -----------------------------------------------------------------------------

    /**
     * "Not started" / "Learning" / "I know it by heart" of the surah sheet, with the meaning of the Home sheet: the repository
     * calls are the steps of [SurahStatusChange] (one copy of the rules). The sheet has asked for the confirmation already
     * when learned ayahs leave the repetitions.
     */
    fun chooseSurah(target: SurahTarget) {
        viewModelScope.launch {
            try {
                val today = ticker.day.value
                val current = hifz.surahCoverage(surahNumber).first().status
                val plan = hifz.plan().first()
                val steps = SurahStatusChange.steps(current, target, inPlan = plan != null && surahNumber in plan.surahOrder)
                for (step in steps) {
                    when (step) {
                        StatusStep.Forget -> hifz.forget(surahNumber)
                        StatusStep.LearnNext -> hifz.learnNext(surahNumber, today)
                        StatusStep.MarkKnown -> hifz.markKnown(surahNumber, today = today)
                        StatusStep.LeaveThePlan -> if (plan != null) {
                            val rest = plan.surahOrder - surahNumber
                            if (rest.isEmpty()) {
                                hifz.clearPlan()
                            } else {
                                hifz.setPlan(
                                    scope = HifzScope.CUSTOM,
                                    surahOrder = rest,
                                    dailyNewAyahs = plan.dailyNewAyahs,
                                    targetEpochDay = plan.targetEpochDay,
                                    manzilDays = plan.manzilDays,
                                    today = today,
                                )
                            }
                        }
                    }
                }
                if (SurahStatusChange.resulting(target) == SurahHifzStatus.LEARNED) {
                    eventChannel.trySend(ReaderEvent.SurahLearned)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(ReaderEvent.StatusFailed)
            }
        }
    }

    /** "I know this ayah": the ayah becomes an OLD passage of its own (or joins the neighbours). */
    fun markAyahKnown(ayah: AyahEntity) {
        viewModelScope.launch {
            try {
                hifz.markKnown(surahNumber, ayah.numberInSurah, ayah.numberInSurah, ticker.day.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(ReaderEvent.StatusFailed)
            }
        }
    }

    /** "Remove from learned": the ayah leaves its passage (a passage can be cut in two). */
    fun forgetAyah(ayah: AyahEntity) {
        viewModelScope.launch {
            try {
                hifz.forget(surahNumber, ayah.numberInSurah, ayah.numberInSurah)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(ReaderEvent.StatusFailed)
            }
        }
    }

    /** When the ayah comes back, from the passage that covers it; null when the ayah is not learned. */
    fun scheduleOf(ayahInSurah: Int): AyahSchedule? {
        val passage = coverage.value?.passages?.firstOrNull { ayahInSurah in it } ?: return null
        if (passage.stage == PassageStage.OLD) return AyahSchedule.Circle
        val days = (passage.nextDueEpochDay ?: return AyahSchedule.Circle) - ticker.day.value
        return if (days <= 0) AyahSchedule.Due else AyahSchedule.InDays(days)
    }

    fun saveNote(ayah: AyahEntity, note: String) {
        surahViewModel.upsertNote(ayah.number, surahNumber, note)
    }

    // -- Playback -----------------------------------------------------------------------------------------

    private fun isActive(): Boolean = playback.isActiveFor(surahNumber)

    private fun startHere(index: Int, autoplay: Boolean) {
        val surah = _surah.value ?: return
        playback.start(surah, _prefs.value, _practice.value, index, autoplay)
        selected.value = true
        readyIndex.value = index
    }

    /** The play button of the dock. */
    fun togglePlayback() {
        val surah = _surah.value ?: return
        if (!isActive()) {
            startHere(readyIndex.value, autoplay = true)
            return
        }
        val s = playback.state.value
        val finishedWholeSurah = !s.isPlaying && !s.canResume && !_practice.value.rangeEnabled &&
            s.index == surah.ayahs.lastIndex && s.durationMs > 0 && s.positionMs >= s.durationMs
        if (finishedWholeSurah) playback.select(0)
        playback.toggle()
        selected.value = true
    }

    fun nextAyah() {
        if (isActive()) {
            playback.next()
        } else {
            moveReadyIndex(+1)
        }
    }

    fun previousAyah() {
        if (isActive()) {
            playback.previous()
        } else {
            moveReadyIndex(-1)
        }
    }

    private fun moveReadyIndex(delta: Int) {
        val last = (_surah.value?.ayahs?.lastIndex ?: return)
        readyIndex.value = (readyIndex.value + delta).coerceIn(0, last)
        selected.value = true
    }

    /**
     * A tap on an ayah. In this surah's own session it moves there (and plays on when it was playing).
     * While another surah plays it takes the audio over from that ayah. Otherwise it only marks the ayah:
     * the dock then starts from it.
     */
    fun selectAyah(index: Int) {
        val state = playback.state.value
        selected.value = true
        when {
            isActive() -> {
                val wasPlaying = state.isPlaying || state.isPreparing
                playback.select(index)
                if (wasPlaying) playback.toggle()
            }
            playback.nowPlaying.value != null && state.isPlaying -> startHere(index, autoplay = true)
            else -> readyIndex.value = index
        }
    }

    /** "Listen from here" in the ayah sheet. */
    fun listenFrom(index: Int) {
        selected.value = true
        if (isActive()) {
            playback.select(index)
            playback.toggle()
        } else {
            startHere(index, autoplay = true)
        }
    }

    /** "Repeat this ayah": a passage of that one ayah, repeated with the repeat count already chosen. */
    fun repeatAyah(index: Int) {
        val number = _surah.value?.ayahs?.getOrNull(index)?.numberInSurah ?: return
        val count = _practice.value.rangeRepeatCount.takeIf { it != 1 } ?: 3
        applyPassage(from = number, to = number, repeatCount = count)
    }

    /** Repeat the passage [from]..[to] (numbers in the surah) and start playing at its beginning. */
    fun applyPassage(from: Int, to: Int, repeatCount: Int) {
        val surah = _surah.value ?: return
        val practice = _practice.value.copy(
            rangeEnabled = true,
            startAyah = from,
            endAyah = to,
            rangeRepeatCount = repeatCount,
        ).normalized(surah.ayahs.size)
        // A passage repeat replaces the per-ayah repeat of the preferences.
        val prefs = _prefs.value.copy(repeatCount = 1)
        commitSettings(prefs, practice)
        val startIndex = practice.startAyah - 1
        selected.value = true
        if (isActive()) {
            playback.select(startIndex)
            playback.toggle()
        } else {
            startHere(startIndex, autoplay = true)
        }
    }

    fun stopPassage() {
        val surah = _surah.value ?: return
        commitSettings(_prefs.value, _practice.value.copy(rangeEnabled = false).normalized(surah.ayahs.size))
    }

    fun setPauseSeconds(seconds: Int) {
        val surah = _surah.value ?: return
        commitSettings(_prefs.value, _practice.value.copy(pauseSeconds = seconds).normalized(surah.ayahs.size))
    }

    /**
     * The echo of the dock: [repeats] 0 is off, 1..3 is on with that many rounds per ayah (the reciter reads, then silence for
     * the person to repeat). It replaces the plain pause and goes on without a restart of the ayah that is playing.
     */
    fun setEcho(repeats: Int) {
        val surah = _surah.value ?: return
        val practice = if (repeats <= 0) _practice.value.copy(echo = false) else _practice.value.copy(echo = true, echoRepeats = repeats)
        commitSettings(_prefs.value, practice.normalized(surah.ayahs.size))
    }

    fun setPlaybackSpeed(speed: Float) {
        commitSettings(_prefs.value.copy(playbackSpeed = speed), _practice.value)
    }

    /** Turns the translation under the Arabic text on or off. */
    fun setShowTranslation(show: Boolean) {
        commitSettings(_prefs.value.copy(showTranslation = show), _practice.value)
        SettingsChangeNotifier.notifyChanged()
        ensureTranslations()
    }

    fun setSleepTimer(timer: SleepTimer) = playback.setSleepTimer(timer)

    /** "No connection · Retry". */
    fun retry() {
        if (isActive()) playback.toggle()
    }

    private fun commitSettings(prefs: SurahReaderPreferences, practice: ReaderPracticeSettings) {
        _prefs.value = prefs
        _practice.value = practice
        prefs.save()
        practice.save(surahNumber)
        if (isActive()) playback.updateSettings(prefs, practice)
    }

    /** Basic data of any surah (for the "next surah" card and the "playing" chip), or null while loading. */
    fun surahInfo(number: Int): SurahEntity? =
        homeViewModel.uiState.value.completeQuran.firstOrNull { it.surah.number == number }?.surah

    /** Bundled Russian surah names by number (`SurahModel.name`), the source of the RU surah title. */
    val russianNames: StateFlow<Map<Int, String>> = homeViewModel.uiState
        .map { it.surahList }
        .distinctUntilChanged()
        .map { list -> list.associate { it.surahNumber to it.name } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            homeViewModel.uiState.value.surahList.associate { it.surahNumber to it.name },
        )

    /** Name of a surah by the one rule of the Android UI ([SurahNames]): the same as on the home screen. */
    fun surahDisplayName(
        number: Int,
        language: AppLanguage,
        names: Map<Int, ChapterNameModel>,
        russian: Map<Int, String>,
    ): String = SurahNames.displayName(
        language = language,
        russianName = russian[number],
        transliteration = names[number]?.transliteration,
        fallback = surahInfo(number)?.englishName?.takeIf { it.isNotBlank() } ?: "№ $number",
    )

    /** Names for the "next surah" card. */
    fun nextSurahDisplay(
        number: Int,
        language: AppLanguage,
        names: Map<Int, ChapterNameModel>,
        russian: Map<Int, String>,
    ): NextSurahDisplay {
        val chapter = names[number]
        val info = surahInfo(number)
        return NextSurahDisplay(
            title = surahDisplayName(number, language, names, russian),
            meaning = chapter?.translated ?: info?.englishNameTranslation.orEmpty(),
            arabicName = chapter?.arabic ?: info?.name.orEmpty(),
        )
    }

    /** Opens the player of the surah that currently plays (the chip above the dock). */
    fun routeToPlayingSurah(): Dest.Reader? {
        val now = playback.nowPlaying.value ?: return null
        if (now.surahNumber == surahNumber) return null
        val ayah = now.ayahs.getOrNull(playback.state.value.index)?.numberInSurah
        return Dest.Reader(surah = now.surahNumber, ayah = ayah, origin = Origin.PLAYER)
    }

    companion object {
        /** [onVisibleAyah] value before the first layout. */
        const val NotMeasured = -2

        /** [onVisibleAyah] value while the header is still the first thing on screen. */
        const val HeaderOnTop = -1
    }

    override fun onCleared() {
        flushReadingPosition()
        super.onCleared()
    }
}
