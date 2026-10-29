package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.ReaderAudioAyah
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.ReaderAudioState
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

/** What the mini player and the reader dock show: the surah that currently owns the audio session. */
data class NowPlaying(val surahNumber: Int, val ayahs: List<ReaderAudioAyah>)

/** When playback should pause by itself. */
sealed interface SleepTimer {
    data object Off : SleepTimer

    /** Pause once the last ayah of the playing surah has finished, even when it is set to repeat. */
    data object EndOfSurah : SleepTimer

    /** Pause after [minutes] minutes. */
    data class Minutes(val minutes: Int) : SleepTimer
}

/** Connects the controller to the background playback service (the media session); a fake in tests. */
internal interface PlaybackServiceLink {
    /** Makes sure the service exists and is bound; cheap when it already is. */
    fun ensureConnected()

    /** Unbinds from the service, which then stops itself. */
    fun release()
}

/**
 * Audio that lives above the screens (docs/redesign/ANDROID_REDESIGN.md, 4.4). Owns the single
 * [ReaderAudioSession] of the app; leaving a reader does not stop it.
 *
 * The audio is played by one ExoPlayer ([ExoReaderAudioOutput]) and shown to the system by
 * `PlaybackService` (a Media3 media session: notification, lock screen, headset buttons), which mirrors
 * [nowPlaying] and [state] and sends its commands back through this class.
 *
 * Opening surah Y while X plays does not take the session over: the Y reader shows its dock in the
 * "ready" state and calls [start] only when the person presses play there. Use [isActiveFor] to tell
 * which surah the session belongs to.
 *
 * All commands must be called on the main thread (the session runs on the main dispatcher).
 */
class PlaybackController internal constructor(
    private val session: ReaderAudioSession,
    timerScope: CoroutineScope? = null,
    private val reciter: StateFlow<Reciter>? = null,
    private val serviceLink: PlaybackServiceLink? = null,
    private val extras: OutputExtras? = null,
) {
    // Created on first use: tests that never arm a timer must not touch Dispatchers.Main.
    private val scope: CoroutineScope by lazy { timerScope ?: applicationScope() }

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Null until something was started and again after [stop]. */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Proxies the session state. Meaningful only while [nowPlaying] is not null. */
    val state: StateFlow<ReaderAudioState> = session.state

    private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)

    /** The armed sleep timer; it goes back to [SleepTimer.Off] once it has paused playback. */
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()

    private val _sleepRemainingSeconds = MutableStateFlow(0)

    /** Seconds left of a [SleepTimer.Minutes] timer, 0 for the other timers. */
    val sleepRemainingSeconds: StateFlow<Int> = _sleepRemainingSeconds.asStateFlow()

    private var sleepJob: Job? = null
    private var reciterJob: Job? = null
    private var idleJob: Job? = null

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
        serviceLink?.ensureConnected()
        watchReciter()
        watchIdle()
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
        applyEndOfSurahLimit()
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

    /**
     * Arms, replaces or (with [SleepTimer.Off]) cancels the sleep timer. When it fires, playback pauses
     * and the timer returns to [SleepTimer.Off]. A [SleepTimer.EndOfSurah] timer set while nothing plays
     * applies to the next surah that is started.
     */
    fun setSleepTimer(timer: SleepTimer) {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingSeconds.value = 0
        val normalized = if (timer is SleepTimer.Minutes && timer.minutes <= 0) SleepTimer.Off else timer
        _sleepTimer.value = normalized
        when (normalized) {
            SleepTimer.Off -> Unit
            SleepTimer.EndOfSurah -> sleepJob = scope.launch { waitForEndOfSurah() }
            is SleepTimer.Minutes -> sleepJob = scope.launch { countDown(normalized.minutes * 60) }
        }
        applyEndOfSurahLimit()
    }

    /**
     * The system paused the audio (audio focus lost for good, headphones unplugged, a headset or
     * notification pause): shows the paused state and keeps the position so that play resumes it.
     */
    fun pauseBySystem() {
        if (_nowPlaying.value != null && session.state.value.isPlaying) session.toggle()
    }

    /**
     * Plays the sample of a reciter ([source] is a file or URL) on the same player. Audio of the reader
     * stops where it is (the mini player and the dock show the same ayah, stopped); [onFinished] is told
     * when the sample ended or failed, but not when [stopSample] or other audio cuts it.
     */
    fun playSample(source: String, onFinished: (failed: Boolean) -> Unit) {
        val output = extras
        if (output == null) {
            onFinished(true)
            return
        }
        if (_nowPlaying.value != null) session.select(session.state.value.index)
        output.playSample(source, onFinished)
    }

    fun stopSample() {
        extras?.stopSample()
    }

    /** The service was dismissed by the system or the person (the app was swiped away): lets go of it. */
    internal fun disconnectService() {
        serviceLink?.release()
    }

    /** Stops the audio, cancels the sleep timer and hides the mini player. */
    fun stop() {
        session.stop()
        _nowPlaying.value = null
        if (_sleepTimer.value != SleepTimer.Off) setSleepTimer(SleepTimer.Off)
    }

    /**
     * A changed reciter takes effect at once: the ayah that is playing (or paused halfway) starts over
     * with the new voice. Nothing happens while the player is idle: the next play uses the new reciter.
     */
    private fun watchReciter() {
        val source = reciter ?: return
        if (reciterJob != null) return
        reciterJob = scope.launch {
            var last = source.value
            source.collect { next ->
                if (next == last) return@collect
                last = next
                if (_nowPlaying.value == null) return@collect
                val current = session.state.value
                session.select(current.index)
                if (current.isPlaying) session.toggle()
            }
        }
    }

    /**
     * When the ayah sequence is over (the last ayah has ended; nothing is playing or paused halfway) the
     * player lets go of the audio focus, so that music or a podcast of another app can go on.
     */
    private fun watchIdle() {
        val output = extras ?: return
        if (idleJob != null) return
        idleJob = scope.launch {
            session.state
                .map { it.isPlaying || it.canResume }
                .distinctUntilChanged()
                .collect { active -> if (!active) output.releaseFocus() }
        }
    }

    private suspend fun countDown(seconds: Int) {
        var left = seconds
        while (left > 0) {
            _sleepRemainingSeconds.value = left
            delay(1_000)
            left--
        }
        fireSleepTimer()
    }

    /**
     * Returns once the session has played the last ayah of the surah to its end. A state that is already
     * there when the timer is armed (for example the end of an earlier run) does not count.
     */
    private suspend fun waitForEndOfSurah() {
        session.state.drop(1).first { s ->
            val last = _nowPlaying.value?.ayahs?.lastIndex
            last != null && !s.isPlaying && !s.canResume && s.index == last &&
                s.durationMs > 0 && s.positionMs >= s.durationMs
        }
        fireSleepTimer()
    }

    private fun fireSleepTimer() {
        _sleepTimer.value = SleepTimer.Off
        _sleepRemainingSeconds.value = 0
        sleepJob = null
        session.stopAfterAyah(null)
        if (session.state.value.isPlaying) session.toggle()
    }

    /** Tells the session where a [SleepTimer.EndOfSurah] timer has to cut playback, or removes the limit. */
    private fun applyEndOfSurahLimit() {
        val limit = if (_sleepTimer.value == SleepTimer.EndOfSurah) _nowPlaying.value?.ayahs?.lastIndex else null
        session.stopAfterAyah(limit)
    }
}

private fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
