package tj.app.quran_todo.ui.hifz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.UiPrefs

/** What a finished check did: counted as the repetition of a passage of today's lesson, or only logged. */
enum class CheckSaved { Review, Check }

/**
 * State and commands of "Check yourself" in one reader (one `Dest.Reader` back stack entry). The session lives
 * here, so it survives a rotation; [HifzMode] keeps a text copy of it for the case the process dies.
 *
 * The answers of the session are not written one by one: at the end the person grades the passage and
 * [finish] writes ONE result through the [HifzRepository] (the only writer of progress): [HifzRepository.review]
 * when the checked range is exactly a passage of today's lesson that is not done yet ([CheckRouting]), otherwise
 * [HifzRepository.logCheck] (stages and due days do not change).
 *
 * Audio belongs to the shared [PlaybackController] (see [RecallAudio]): "Listen" plays only the ayah in question
 * and the person's own player settings are put back as soon as it is over or the mode is left.
 */
class HifzViewModel(
    private val route: Dest.Reader,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
) : ViewModel() {
    private val surahNumber = route.surah

    private val _session = MutableStateFlow<HifzSession?>(null)

    /** The running session; null when the mode is off. */
    val session: StateFlow<HifzSession?> = _session

    private val _exitPrompt = MutableStateFlow(false)

    /** The "leave Check yourself?" question is open. */
    val exitPrompt: StateFlow<Boolean> = _exitPrompt

    private val _saving = MutableStateFlow(false)

    /** The result is being written. */
    val saving: StateFlow<Boolean> = _saving

    private val savedChannel = Channel<CheckSaved>(Channel.BUFFERED)

    /** One value per result written. */
    val saved: Flow<CheckSaved> = savedChannel.receiveAsFlow()

    /** How much of a hidden ayah stays readable (the five levels of the hint). */
    val hintMode: StateFlow<HifzHintMode> = uiPrefs.hifzHintMode

    fun setHintMode(mode: HifzHintMode) = uiPrefs.setHifzHintMode(mode)

    private val audio = RecallAudio(playback, surahNumber, viewModelScope)

    /** The audio of the ayah being recalled plays (or is being prepared). */
    val listening: StateFlow<Boolean> = combine(_session, audio.state) { session, sound ->
        val current = session?.current
        current != null && sound.ayah == current.inSurah && sound.playing
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var routeHandled = false

    // -- Starting -----------------------------------------------------------------------------------------

    /**
     * Called once when the surah is there. [saved] is what the screen kept as text across a process death:
     * a session to put back, [SavedOff] when the mode had been left, or empty for a first start. A reader
     * opened with `hifz = true` then starts its session on the range of the route.
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
     * The eye button and "Check yourself from here": from the ayah at list index [startIndex], to [rangeEndIndex]
     * when a repeat passage is on, otherwise at most [HifzSessions.MaxFromHere] ayahs.
     */
    fun startFromAyah(surah: SurahWithAyahs, startIndex: Int, rangeEndIndex: Int?) {
        routeHandled = true
        val ayahs = surah.ayahs.sortedBy { it.numberInSurah }
        HifzSessions.fromAyah(surahNumber, ayahs, startIndex, rangeEndIndex)?.let {
            audio.endListening()
            _session.value = it
        }
    }

    // -- Recalling ----------------------------------------------------------------------------------------

    fun reveal() {
        _session.value = _session.value?.reveal()
    }

    /**
     * Answers the ayah at session position [position] and moves on. Ignored when the session has moved
     * already (a second tap in the same frame) or the ayah is not revealed yet.
     */
    fun mark(mark: AyahMark, position: Int) {
        val session = _session.value ?: return
        if (session.finished || session.index != position || !session.revealed) return
        audio.endListening()
        _session.value = session.mark(mark)
    }

    /**
     * Answers the ayah at [position] before the reveal: the progressive hint offers "I remember" / "Hard" while the ayah is still
     * partly shown. Ignored when the session has moved on already.
     */
    fun markEarly(mark: AyahMark, position: Int) {
        val session = _session.value ?: return
        if (session.finished || session.index != position) return
        audio.endListening()
        _session.value = session.markWithoutReveal(mark)
    }

    /** A tap on the hidden word [word] of the ayah at [position]: it opens and counts as one peek. */
    fun peekWord(position: Int, word: Int) {
        _session.value = _session.value?.peekWord(position, word)
    }

    /** Holding the whole ayah at [position] to peek: counts as two peeks. */
    fun peekAyah(position: Int) {
        _session.value = _session.value?.peekAyah(position)
    }

    /** "Hide more" of the progressive hint on the ayah at [position], which has [wordCount] words. */
    fun hideMore(position: Int, wordCount: Int) {
        _session.value = _session.value?.hideMore(position, wordCount)
    }

    /**
     * Writes the result of the finished session: [grade] for the passage and [hard] (numbers in the surah) as its
     * difficult ayahs. Counts as the lesson's repetition when the range is exactly a passage that is due today.
     */
    fun finish(grade: ReviewGrade, hard: Set<Int>) {
        val session = _session.value ?: return
        if (!session.finished || _saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val range = AyahRange(session.surah, session.firstAyah, session.lastAyah)
            val today = ticker.day.value
            try {
                val lesson = runCatching { hifz.lesson(today).first() }.getOrNull()
                when (val action = CheckRouting.choose(lesson, range)) {
                    is CheckAction.Review -> {
                        hifz.review(action.passageId, grade, hard, today)
                        savedChannel.trySend(CheckSaved.Review)
                    }
                    CheckAction.LogCheck -> {
                        hifz.logCheck(range.surah, range.startAyah, range.endAyah, grade, hard, today)
                        savedChannel.trySend(CheckSaved.Check)
                    }
                }
                AppTelemetry.logEvent("check_completed", mapOf("grade" to grade.name.lowercase()))
                exit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the sheet open: the person can try again.
                AppTelemetry.logError(e, context = "check_save_failed")
            } finally {
                _saving.value = false
            }
        }
    }

    // -- Leaving ------------------------------------------------------------------------------------------

    /** The close button and the back gesture: asks first when ayahs were answered but some remain. */
    fun requestExit() {
        val session = _session.value ?: return
        if (!session.finished && session.markedCount > 0) {
            _exitPrompt.value = true
        } else {
            exit()
        }
    }

    fun dismissExitPrompt() {
        _exitPrompt.value = false
    }

    fun exit() {
        audio.endListening()
        _exitPrompt.value = false
        _session.value = null
    }

    // -- Listening ----------------------------------------------------------------------------------------

    /** "Listen to the reciter" of the sheet "My reading": the passage of the session, every ayah once. */
    fun listenPassage(surah: SurahWithAyahs) {
        val session = _session.value ?: return
        audio.toggleSequence(surah, session.items.map { it.inSurah })
    }

    /** The "Listen" button: plays the current ayah, pauses it while it plays, resumes it when paused. */
    fun toggleListen(surah: SurahWithAyahs) {
        val current = _session.value?.current ?: return
        audio.toggleAyah(surah, current.inSurah)
    }

    override fun onCleared() {
        audio.endListening()
        super.onCleared()
    }

    companion object {
        /** Saved text of "the mode was left": a restored reader must not start the route's session again. */
        const val SavedOff = "off"
    }
}
