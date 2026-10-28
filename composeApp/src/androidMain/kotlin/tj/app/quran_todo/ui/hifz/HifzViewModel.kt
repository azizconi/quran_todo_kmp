package tj.app.quran_todo.ui.hifz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.dao.AyahTodoDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * State and commands of the memorize mode of one reader (one `Dest.Reader` back stack entry). The session
 * lives here, so it survives a rotation; [HifzMode] keeps a text copy of it for the case the process dies.
 *
 * The shared view models (Koin singletons, injected, never obtained through `koinViewModel`) write the
 * ratings: [HomeViewModel.completeReview] moves the SM-2 schedule of the ayah and refreshes the due reviews
 * and weak ayahs that Home's "Today" reads; an ayah that has no status yet becomes "memorizing" first
 * through [SurahViewModel.updateAyahStatus] (without scheduling a review: `completeReview` does that with
 * the rating).
 *
 * Audio belongs to the shared [PlaybackController]. "Listen" plays only the ayah in question, for which the
 * session is configured without auto-advance, repeats and passage; the person's own settings are put back as
 * soon as that ayah is over, the mode is left or this view model is cleared.
 */
class HifzViewModel(
    private val route: Dest.Reader,
    private val surahViewModel: SurahViewModel,
    private val homeViewModel: HomeViewModel,
    ayahTodoDao: AyahTodoDao,
    uiPrefs: UiPrefs,
    private val playback: PlaybackController,
) : ViewModel() {
    private val surahNumber = route.surah

    private val _session = MutableStateFlow<HifzSession?>(null)

    /** The running session; null when the mode is off. */
    val session: StateFlow<HifzSession?> = _session

    private val _exitPrompt = MutableStateFlow(false)

    /** The "leave memorize mode?" question is open. */
    val exitPrompt: StateFlow<Boolean> = _exitPrompt

    /** Whether the first word of a hidden ayah stays readable. */
    val hintMode: StateFlow<HifzHintMode> = uiPrefs.hifzHintMode

    /** Statuses of the ayahs of this surah by global number; always current, so a rating reads the truth. */
    private val statuses: StateFlow<Map<Int, AyahTodoStatus>> = ayahTodoDao.getBySurahNumber(surahNumber)
        .map { list -> list.associate { it.ayahNumber to it.status } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The audio of the ayah being recalled plays (or is being prepared). */
    val listening: StateFlow<Boolean> = combine(_session, playback.nowPlaying, playback.state) { session, now, audio ->
        val current = session?.current
        current != null && now?.surahNumber == surahNumber &&
            audio.index == current.inSurah - 1 && (audio.isPlaying || audio.isPreparing)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var routeHandled = false

    // -- Starting -----------------------------------------------------------------------------------------

    /**
     * Called once when the surah is there. [saved] is what the screen kept as text across a process death:
     * a session to put back, [SavedOff] when the mode had been left, or empty for a first start. A reader
     * opened with `hifz = true` then starts its session on the range of the task.
     */
    fun enter(surah: SurahWithAyahs, saved: String) {
        if (routeHandled) return
        routeHandled = true
        if (saved == SavedOff) return
        val restored = HifzSession.decode(saved)?.takeIf { it.surah == surahNumber }
        if (restored != null) {
            _session.value = restored
            return
        }
        if (!route.hifz || _session.value != null) return
        val ayahs = surah.ayahs.sortedBy { it.numberInSurah }
        val start = route.rangeStart ?: route.ayah ?: 1
        val end = route.rangeEnd ?: start
        _session.value = HifzSessions.range(surahNumber, ayahs, start, end)
            ?: HifzSessions.fromAyah(surahNumber, ayahs, startIndex = (start - 1).coerceAtLeast(0))
    }

    /**
     * The eye button: from the ayah at list index [startIndex] to the end of the surah, or to [rangeEndIndex]
     * when a repeat passage is on.
     */
    fun startFromAyah(surah: SurahWithAyahs, startIndex: Int, rangeEndIndex: Int?) {
        routeHandled = true
        val ayahs = surah.ayahs.sortedBy { it.numberInSurah }
        HifzSessions.fromAyah(surahNumber, ayahs, startIndex, rangeEndIndex)?.let { _session.value = it }
    }

    /** "Repeat the hard ones" from the summary. */
    fun startRetry() {
        val retry = _session.value?.retryHard() ?: return
        endListening()
        _session.value = retry
    }

    // -- Recalling and rating -----------------------------------------------------------------------------

    fun reveal() {
        _session.value = _session.value?.reveal()
    }

    /**
     * Rates the ayah at session position [position] and moves on. Ignored when the session has moved
     * already (a second tap in the same frame) or the ayah is not revealed yet.
     */
    fun rate(quality: ReviewQuality, position: Int) {
        val session = _session.value ?: return
        if (session.finished || session.index != position || !session.revealed) return
        val item = session.items[position]
        endListening()
        if (statuses.value[item.number] == null) {
            surahViewModel.updateAyahStatus(
                ayahNumber = item.number,
                surahNumber = session.surah,
                totalAyahs = session.totalInSurah,
                status = AyahTodoStatus.LEARNING,
                scheduleNextReview = false,
            )
        }
        homeViewModel.completeReview(item.number, session.surah, quality)
        _session.value = session.rate(quality)
    }

    // -- Leaving ------------------------------------------------------------------------------------------

    /** The close button and the back gesture: asks first when ratings were given but ayahs remain. */
    fun requestExit() {
        val session = _session.value ?: return
        if (!session.finished && session.ratedCount > 0) {
            _exitPrompt.value = true
        } else {
            exit()
        }
    }

    fun dismissExitPrompt() {
        _exitPrompt.value = false
    }

    fun exit() {
        endListening()
        _exitPrompt.value = false
        _session.value = null
    }

    // -- Listening ----------------------------------------------------------------------------------------

    private class Original(val prefs: SurahReaderPreferences, val practice: ReaderPracticeSettings)

    private var original: Original? = null
    private var listenSeenPlaying = false

    init {
        viewModelScope.launch {
            // The single ayah is over (or failed): the person's own settings come back.
            playback.state.collect { audio ->
                if (original == null) return@collect
                if (audio.isPlaying) {
                    listenSeenPlaying = true
                } else if (!audio.isPreparing && !audio.canResume && (listenSeenPlaying || audio.hasError)) {
                    restoreSettings()
                }
            }
        }
    }

    /** The "Listen" button: plays the current ayah, pauses it while it plays, resumes it when paused. */
    fun toggleListen(surah: SurahWithAyahs) {
        val current = _session.value?.current ?: return
        val ayahs = surah.ayahs.sortedBy { it.numberInSurah }
        val index = ayahs.indexOfFirst { it.number == current.number }
        if (index < 0) return
        val audio = playback.state.value
        val ours = playback.isActiveFor(surahNumber) && audio.index == index
        if (ours && (audio.isPlaying || audio.isPreparing || audio.canResume)) {
            playback.toggle()
            return
        }
        val saved = original ?: Original(
            SurahReaderPreferences.load(),
            ReaderPracticeSettings.load(surahNumber, ayahs.size),
        )
        playback.start(
            surah = surah,
            prefs = saved.prefs.copy(autoAdvanceAyahs = false, repeatCount = 1),
            practice = saved.practice.copy(rangeEnabled = false),
            fromIndex = index,
            autoplay = true,
        )
        // Set only now: while start() runs the audio state passes through "idle" and would look like the end.
        original = saved
        listenSeenPlaying = false
    }

    /** Stops what "Listen" started and gives the person's own player settings back. */
    private fun endListening() {
        if (original == null) return
        val audio = playback.state.value
        if (playback.isActiveFor(surahNumber) && (audio.isPlaying || audio.isPreparing)) playback.toggle()
        restoreSettings()
    }

    private fun restoreSettings() {
        val saved = original ?: return
        original = null
        listenSeenPlaying = false
        if (playback.isActiveFor(surahNumber)) playback.updateSettings(saved.prefs, saved.practice)
    }

    override fun onCleared() {
        endListening()
        super.onCleared()
    }

    companion object {
        /** Saved text of "the mode was left": a restored reader must not start the route's session again. */
        const val SavedOff = "off"
    }
}
