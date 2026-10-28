package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.common.audio.ReaderAudioAyah
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.common.audio.ReaderAudioState
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

/** What the mini player and the reader dock show: the surah that currently owns the audio session. */
data class NowPlaying(val surahNumber: Int, val ayahs: List<ReaderAudioAyah>)

/**
 * Audio that lives above the screens (docs/redesign/ANDROID_REDESIGN.md, 4.4). Owns the single
 * [ReaderAudioSession] of the app; leaving a reader does not stop it.
 *
 * Opening surah Y while X plays does not take the session over: the Y reader shows its dock in the
 * "ready" state and calls [start] only when the person presses play there. Use [isActiveFor] to tell
 * which surah the session belongs to.
 *
 * All commands must be called on the main thread (the session runs on the main dispatcher).
 */
class PlaybackController internal constructor(
    private val session: ReaderAudioSession,
) {
    /** Production constructor: one session bound to an application-wide scope. */
    constructor(scope: CoroutineScope = applicationScope()) : this(ReaderAudioSession(scope))

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Null until something was started and again after [stop]. */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Proxies the session state. Meaningful only while [nowPlaying] is not null. */
    val state: StateFlow<ReaderAudioState> = session.state

    fun isActiveFor(surah: Int): Boolean = _nowPlaying.value?.surahNumber == surah

    /**
     * Binds the session to [surah], positions it on [fromIndex] (0-based index in ayah order) and,
     * when [autoplay] is true, starts playing from there. Calling it for the surah that is already
     * active restarts playback from [fromIndex].
     */
    fun start(
        surah: SurahWithAyahs,
        prefs: SurahReaderPreferences,
        practice: ReaderPracticeSettings,
        fromIndex: Int,
        autoplay: Boolean = true,
    ) {
        val ayahs = surah.ayahs
            .sortedBy { it.numberInSurah }
            .map { ReaderAudioAyah(globalNumber = it.number, numberInSurah = it.numberInSurah) }
        if (ayahs.isEmpty()) return
        val index = fromIndex.coerceIn(ayahs.indices)
        // Starting is always explicit here, so the "autoplay when a reader opens" preference must not
        // make configure() begin playback before the position is chosen.
        session.configure(
            surahNumber = surah.surah.number,
            ayahs = ayahs,
            preferences = prefs.copy(autoplayOnOpen = false),
            practice = practice,
            initialAyahGlobalNumber = ayahs[index].globalNumber,
        )
        _nowPlaying.value = NowPlaying(surah.surah.number, ayahs)
        session.select(index)
        if (autoplay) session.toggle()
    }

    fun toggle() {
        if (_nowPlaying.value != null) session.toggle()
    }

    fun next() {
        if (_nowPlaying.value != null) session.next()
    }

    fun previous() {
        if (_nowPlaying.value != null) session.previous()
    }

    fun select(index: Int) {
        if (_nowPlaying.value != null) session.select(index)
    }

    fun updateSettings(prefs: SurahReaderPreferences, practice: ReaderPracticeSettings) {
        if (_nowPlaying.value != null) {
            session.updateSettings(prefs.copy(autoplayOnOpen = false), practice)
        }
    }

    /** Stops the audio and hides the mini player. */
    fun stop() {
        session.stop()
        _nowPlaying.value = null
    }
}

private fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
