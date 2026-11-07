package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

/**
 * One passage of the listening queue ("Listen to the old", task F1b): the ayahs [startAyah]..[endAyah] (numbers in the surah) of
 * [surah]. [passageId] is the stored passage it comes from (what "I repeated it by ear" is written for); [seconds] is one calm
 * recitation, only for the estimates the screens show.
 */
data class ListenItem(
    val passageId: Long,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val seconds: Int = 0,
)

/**
 * The listening queue: passages of different surahs one after another, with the reciter and the speed of the person.
 * [position] is the passage that plays (or stands paused) now; [heard] are the positions that were read to the end at least once
 * (the ones "I repeated it by ear" is written for); [finished] says the last passage has ended (or the person pressed "Done").
 * [echo] reads every ayah with its silence for the person to repeat after the reciter ([echoRepeats] times).
 */
data class ListenQueue(
    val items: List<ListenItem>,
    val position: Int,
    val echo: Boolean,
    val echoRepeats: Int = 1,
    val heard: Set<Int> = emptySet(),
    val finished: Boolean = false,
) {
    val current: ListenItem? get() = items.getOrNull(position)

    /** The passages the person has heard to the end, in the order of the queue. */
    val heardItems: List<ListenItem> get() = heard.sorted().mapNotNull { items.getOrNull(it) }
}

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
    init {
        // The end of a passage of the listening queue goes on with the next one without the session going idle.
        session.onSequenceEnd = ::onQueueSequenceEnded
    }

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

    private val _listenQueue = MutableStateFlow<ListenQueue?>(null)

    /**
     * The listening queue ("Listen to the old") while there is one, null otherwise. It is dropped by [stop] and by anything
     * else that takes the session over ([start], [playSequence]); opening a reader leaves it alone.
     */
    val listenQueue: StateFlow<ListenQueue?> = _listenQueue.asStateFlow()

    private var queueSurahs: ((Int) -> SurahWithAyahs?)? = null
    private var queuePrefs = SurahReaderPreferences()

    /** The queue is moving to its next passage: the audio is not idle in between. */
    private var queueSwitching = false

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
        _listenQueue.value = null
        startBound(surah, prefs, practice, fromIndex, autoplay)
    }

    private fun startBound(
        surah: SurahWithAyahs,
        prefs: SurahReaderPreferences,
        practice: ReaderPracticeSettings,
        fromIndex: Int,
        autoplay: Boolean,
    ) {
        val index = bind(surah, prefs, practice, fromIndex) ?: return
        session.select(index)
        if (autoplay) session.toggle()
    }

    /**
     * Plays the ayahs at [ayahIndices] (0-based positions in ayah order, repeats allowed) of [surah] in exactly this
     * order, one after the other: the lesson's "every ayah three times, then the whole passage twice". The player,
     * the notification and the mini player behave as for any listening, and the index of [state] stays the index of
     * the ayah being played. Nothing the person saved is touched: the repeat, range and pause settings are not
     * consulted (only [prefs] for the speed), and the next [start] of any reader puts its own settings back.
     * Pause, resume, previous and next work on the sequence; [select] or [start] end it. An empty list does nothing.
     * With [echo] (the lesson's "I repeat after the reciter") every reading of the sequence is followed by the echo silence.
     */
    fun playSequence(
        surah: SurahWithAyahs,
        ayahIndices: List<Int>,
        prefs: SurahReaderPreferences,
        autoplay: Boolean = true,
        echo: Boolean = false,
    ) {
        val first = ayahIndices.firstOrNull() ?: return
        _listenQueue.value = null
        // The practice is plain on purpose (no passage, no pause); the preferences must not advance by themselves.
        val plain = ReaderPracticeSettings(rangeEnabled = false, startAyah = 1, endAyah = 1, pauseSeconds = 0, echo = echo)
        val index = bind(surah, prefs.copy(autoAdvanceAyahs = false, repeatCount = 1), plain, first) ?: return
        session.select(index)
        if (autoplay) session.playScript(ayahIndices)
    }

    /** Configures the session for [surah] and publishes it; returns the clamped start index, or null without ayahs. */
    private fun bind(
        surah: SurahWithAyahs,
        prefs: SurahReaderPreferences,
        practice: ReaderPracticeSettings,
        fromIndex: Int,
    ): Int? {
        val ayahs = surah.ayahs
            .sortedBy { it.numberInSurah }
            .map { ReaderAudioAyah(globalNumber = it.number, numberInSurah = it.numberInSurah) }
        if (ayahs.isEmpty()) return null
        serviceLink?.ensureConnected()
        watchReciter()
        watchIdle()
        // The old passages of the listening queue are not a place to "continue" from.
        session.savesPosition = _listenQueue.value == null
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
        return index
    }

    fun toggle() {
        if (_nowPlaying.value == null) return
        val queue = _listenQueue.value
        if (queue != null && queue.finished && !session.state.value.isPlaying && !session.state.value.canResume) {
            // The queue was over (or ended with "Done"): play restarts the last passage.
            if (playQueueItem(queue.position)) return
        }
        session.toggle()
    }

    /**
     * Next ayah. In the listening queue the last ayah of a passage goes on to the next passage (a headset's "next" and its double
     * press are the same command to Media3, so the double press cannot mean "next passage" on its own).
     */
    fun next() {
        if (_nowPlaying.value == null) return
        val queue = _listenQueue.value
        val item = queue?.current
        if (queue != null && item != null && session.state.value.index >= item.endAyah - 1) {
            nextListenItem()
        } else {
            session.next()
        }
    }

    /**
     * Previous ayah. In the listening queue the first ayah of a passage goes back to the passage before it (the first passage
     * plays its first ayah again).
     */
    fun previous() {
        if (_nowPlaying.value == null) return
        val queue = _listenQueue.value
        val item = queue?.current
        if (queue != null && item != null && queue.position > 0 && session.state.value.index <= item.startAyah - 1) {
            previousListenItem()
        } else {
            session.previous()
        }
    }

    fun select(index: Int) {
        if (_nowPlaying.value != null) session.select(index)
    }

    /**
     * Where the ayah that sounds is, in milliseconds from the start of its file, read from the player at this very moment (the
     * `positionMs` of [state] is refreshed only five times a second, too coarse to follow words). 0 when nothing is loaded.
     */
    fun positionMs(): Long = extras?.currentPositionMs() ?: 0L

    /**
     * The position of the player every [intervalMs] while it is collected: the clock of the word-by-word highlight. It reads the
     * player, so collect it only while something that follows the words is on screen and audio plays.
     */
    fun positions(intervalMs: Long = WordClockIntervalMs): Flow<Long> = flow {
        while (true) {
            emit(positionMs())
            delay(intervalMs)
        }
    }

    /**
     * Moves the ayah that plays (or is paused halfway) to [positionMs] of its file. An ayah that is being prepared starts there
     * once it is ready; nothing happens when no ayah is loaded.
     */
    fun seekInAyah(positionMs: Long) {
        val output = extras ?: return
        if (_nowPlaying.value == null) return
        if (session.state.value.isPreparing) output.startNextAt(positionMs) else output.seekTo(positionMs)
    }

    /**
     * Plays the ayah at [index] starting at [positionMs] of its file ("play from this word"). Like [select] followed by [toggle], and
     * the ayah begins at [positionMs] instead of its start.
     */
    fun playAyahFrom(index: Int, positionMs: Long) {
        if (_nowPlaying.value == null) return
        session.select(index)
        session.toggle()
        // After toggle(): the session has already stopped the player (which forgets an earlier start), and the ayah itself is
        // started a moment later, when its file is ready.
        extras?.startNextAt(positionMs)
    }

    fun updateSettings(prefs: SurahReaderPreferences, practice: ReaderPracticeSettings) {
        if (_nowPlaying.value == null) return
        val queue = _listenQueue.value
        val item = queue?.current
        if (queue != null && item != null) {
            // A reader's own repeat and pause settings must not break the passage of the queue; only the speed is taken.
            queuePrefs = prefs
            session.updateSettings(prefs.copy(autoplayOnOpen = false, repeatCount = 1), queuePractice(queue, item))
            return
        }
        session.updateSettings(prefs.copy(autoplayOnOpen = false), practice)
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
        _listenQueue.value = null
        session.stop()
        _nowPlaying.value = null
        if (_sleepTimer.value != SleepTimer.Off) setSleepTimer(SleepTimer.Off)
    }

    // -- The listening queue ("Listen to the old", task F1b) ---------------------------------------------------------

    /**
     * Plays [items] (passages of different surahs) one after another from [fromPosition], with the reciter and the speed of
     * the person ([prefs]); each passage is played once, or with the echo silences when [echo] is on. [loadSurah] gives the
     * text of a surah (it only needs the ayah numbers; a surah it cannot give is skipped). The queue keeps going when the
     * screen is left, in the background, with the notification showing the surah and the ayah; it ends by itself after the last
     * passage ([ListenQueue.finished]) and is dropped by [stop] or by any other playback that takes the session over.
     */
    fun startListenQueue(
        items: List<ListenItem>,
        loadSurah: (Int) -> SurahWithAyahs?,
        prefs: SurahReaderPreferences,
        echo: Boolean,
        echoRepeats: Int = 1,
        fromPosition: Int = 0,
    ) {
        if (items.isEmpty()) return
        queueSurahs = loadSurah
        queuePrefs = prefs
        _listenQueue.value = ListenQueue(items, position = 0, echo = echo, echoRepeats = echoRepeats)
        for (position in fromPosition.coerceIn(items.indices)..items.lastIndex) {
            if (playQueueItem(position)) return
        }
        _listenQueue.value = null
    }

    /** The next passage of the queue; after the last one the queue is over ([ListenQueue.finished]) and the sound stops. */
    fun nextListenItem() {
        val queue = _listenQueue.value ?: return
        for (position in queue.position + 1..queue.items.lastIndex) {
            if (playQueueItem(position)) return
        }
        finishListenQueue()
    }

    /** The previous passage of the queue; at the first one it starts that one again. */
    fun previousListenItem() {
        val queue = _listenQueue.value ?: return
        playQueueItem((queue.position - 1).coerceAtLeast(0))
    }

    /** Jumps to the passage at [position] of the queue and plays it from its start. */
    fun seekListenItem(position: Int) {
        val queue = _listenQueue.value ?: return
        if (position in queue.items.indices) playQueueItem(position)
    }

    /** Jumps to the ayah [ayahInSurah] of the passage at [position] (what the media session asks for) and plays it. */
    fun seekListenAyah(position: Int, ayahInSurah: Int) {
        val queue = _listenQueue.value ?: return
        val item = queue.items.getOrNull(position) ?: return
        if (!playQueueItem(position)) return
        if (ayahInSurah > item.startAyah && ayahInSurah <= item.endAyah) {
            session.select(ayahInSurah - 1)
            session.toggle()
        }
    }

    /** Switches the echo of the queue on or off (and the rounds); the passage that plays goes on without a restart. */
    fun setListenEcho(echo: Boolean, echoRepeats: Int = 1) {
        val queue = _listenQueue.value ?: return
        val next = queue.copy(echo = echo, echoRepeats = echoRepeats)
        _listenQueue.value = next
        val item = next.current ?: return
        if (_nowPlaying.value != null) {
            session.updateSettings(queuePrefs.copy(autoplayOnOpen = false, repeatCount = 1), queuePractice(next, item))
        }
    }

    /** "Done": silences the queue and keeps it as it is, so that the person can say what they heard ([ListenQueue.heardItems]). */
    fun finishListenQueue() {
        val queue = _listenQueue.value ?: return
        session.stop()
        _listenQueue.value = queue.copy(finished = true)
    }

    /** Drops the queue (the person marked what they heard, or does not want to): the sound stops and the mini player goes. */
    fun endListenQueue() = stop()

    private fun queuePractice(queue: ListenQueue, item: ListenItem) = ReaderPracticeSettings(
        rangeEnabled = true,
        startAyah = item.startAyah,
        endAyah = item.endAyah,
        rangeRepeatCount = 1,
        pauseSeconds = 0,
        echo = queue.echo,
        echoRepeats = queue.echoRepeats,
    )

    /** Binds the session to the passage at [position] and plays it from its first ayah; false when its surah is not available. */
    private fun playQueueItem(position: Int): Boolean {
        val queue = _listenQueue.value ?: return false
        val item = queue.items.getOrNull(position) ?: return false
        val surah = queueSurahs?.invoke(item.surah) ?: return false
        val next = queue.copy(position = position, finished = false)
        queueSwitching = true
        try {
            _listenQueue.value = next
            startBound(surah, queuePrefs.copy(repeatCount = 1), queuePractice(next, item), item.startAyah - 1, autoplay = true)
        } finally {
            queueSwitching = false
        }
        return true
    }

    /** The session says its passage ended: the queue goes on with the next one (true), or is finished (false). */
    private fun onQueueSequenceEnded(): Boolean {
        val queue = _listenQueue.value ?: return false
        _listenQueue.value = queue.copy(heard = queue.heard + queue.position)
        for (position in queue.position + 1..queue.items.lastIndex) {
            if (playQueueItem(position)) return true
        }
        _listenQueue.value = _listenQueue.value?.copy(finished = true)
        return false
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
                .collect { active -> if (!active && !queueSwitching) output.releaseFocus() }
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

/** How often the word-by-word highlight asks the player where it is (about 16 times a second). */
internal const val WordClockIntervalMs = 60L

private fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
