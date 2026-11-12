package tj.app.quran_todo.common.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import kotlin.time.TimeSource

data class ReaderAudioAyah(val globalNumber: Int, val numberInSurah: Int)

data class ReaderAudioState(
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val canResume: Boolean = false,
    val isPreparing: Boolean = false,
    val pauseRemainingSeconds: Int = 0,
    /** The whole silence [pauseRemainingSeconds] counts down in (the pause, or the echo silence): the ring around play is full at this. */
    val pauseTotalSeconds: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasError: Boolean = false,
    /** While an explicit script ([ReaderAudioSession.playScript]) is on: the place in it (0-based), else -1. */
    val scriptPosition: Int = -1,
    /** The number of steps of that script; 0 outside a script. */
    val scriptLength: Int = 0,
    /** The script has been played to its very end by itself (not stopped, skipped away or restarted). */
    val scriptDone: Boolean = false,
)

/** Owned by a reader screen. All commands and callbacks run on the main dispatcher. */
class ReaderAudioSession internal constructor(
    private val scope: CoroutineScope,
    private val player: ReaderAudioOutput,
    private val source: suspend (ReaderAudioAyah, Int) -> String,
    private val nowMs: () -> Long,
    private val persistPosition: (Int, Int) -> Unit,
) {
    constructor(scope: CoroutineScope) : this(
        scope, PlatformReaderAudioOutput(), cachedAudioSource(), monotonicClock(),
        { surah, ayah -> UserSettingsStorage.saveLastPlaybackPosition(surah, ayah) },
    )
    private val mutableState = MutableStateFlow(ReaderAudioState())
    val state = mutableState.asStateFlow()
    private var ayahs = emptyList<ReaderAudioAyah>()
    private var surahNumber = 0
    private var preferences = SurahReaderPreferences()
    private var practice = ReaderPracticeSettings()
    private var pendingJob: Job? = null
    private var generation = 0L
    private var repeatsRemaining = 1

    /** The round of the echo mode of the reading that is playing (see [EchoPlan]); 0 outside the echo mode. */
    private var echoRound = 0
    private var sequenceCompleted = false
    private var pendingStep: ReaderPlaybackStep? = null

    /** The silence that is running (or paused) is the last one: when it is over the sequence is over (the echo mode). */
    private var pendingEnd = false
    private var gapRemainingMs = 0L
    private var stopAfterIndex: Int? = null

    /** The silence between two readings (a pause or the echo silence) is running or is paused. */
    private val inGap: Boolean get() = pendingStep != null || pendingEnd

    /**
     * Told when the sequence ends by itself (the last ayah has been read, the echo silence after it included), before the state
     * says "stopped". Returning `true` means the owner has started something else at once (the next passage of a listening
     * queue), so the session is not to go idle. The sleep timer's "end of surah" does not call it: that is a stop, not an end.
     */
    var onSequenceEnd: (() -> Boolean)? = null

    /**
     * An explicit order of ayah indices ([playScript]); while it is set, the position in it travels in
     * [repeatsRemaining] and replaces the repeat/advance rules of the preferences. Any other way to start
     * playing ([select], a different surah) drops it.
     */
    private var script: List<Int>? = null
    private val ticker = scope.launch {
        state.map { it.isPlaying }.distinctUntilChanged().collectLatest { playing ->
            if (playing) {
                while (currentCoroutineContext().isActive) {
                    if (!inGap) {
                        mutableState.value = state.value.copy(
                            positionMs = player.getPositionMs(), durationMs = player.getDurationMs(),
                        )
                    }
                    delay(200)
                }
            }
        }
    }

    fun configure(
        surahNumber: Int,
        ayahs: List<ReaderAudioAyah>,
        preferences: SurahReaderPreferences,
        practice: ReaderPracticeSettings,
        initialAyahGlobalNumber: Int,
    ) {
        if (this.surahNumber == surahNumber && this.ayahs == ayahs) {
            updateSettings(preferences, practice)
            return
        }
        stop()
        script = null
        this.surahNumber = surahNumber
        this.ayahs = ayahs
        this.preferences = preferences
        this.practice = practice.normalized(ayahs.size)
        val index = ayahs.indexOfFirst { it.globalNumber == initialAyahGlobalNumber }.coerceAtLeast(0)
        mutableState.value = ReaderAudioState(index = index)
        if (preferences.autoplayOnOpen) play()
    }

    fun select(index: Int) {
        script = null
        stop()
        if (index !in ayahs.indices) return
        mutableState.value = ReaderAudioState(index = index)
        savePosition(index)
    }

    fun toggle() {
        when {
            state.value.isPlaying -> pause()
            state.value.canResume && inGap -> {
                mutableState.value = state.value.copy(isPlaying = true, canResume = false)
                waitForNext()
            }
            state.value.canResume -> {
                player.resume()
                mutableState.value = state.value.copy(isPlaying = true, canResume = false)
            }
            else -> play()
        }
    }

    /**
     * Plays the ayahs at [indices] (positions in the ayah list, repeats allowed) in exactly this order, one after the
     * other, regardless of the repeat and advance preferences; the pause between ayahs still applies. This is how the
     * lesson plays "every ayah three times, then the whole passage twice" without a gap or a change of settings.
     * Pause, resume, next and previous work on the sequence; [select] or another surah ends it. Indices that are not
     * ayahs are dropped; nothing happens when none is left.
     */
    fun playScript(indices: List<Int>) {
        val valid = indices.filter { it in ayahs.indices }
        if (valid.isEmpty()) return
        script = valid
        start(ReaderPlaybackStep(valid[0], 0))
    }

    private fun play() {
        if (ayahs.isEmpty()) return
        script?.let { order ->
            val position = if (sequenceCompleted) 0 else repeatsRemaining.coerceIn(0, order.lastIndex)
            start(ReaderPlaybackStep(order[position], position))
            return
        }
        val bounds = playbackBounds()
        val index = if (practice.rangeEnabled && sequenceCompleted) bounds.first else
            state.value.index.takeIf { it in bounds } ?: bounds.first
        start(freshStep(index, preferences, practice))
    }

    /**
     * Makes playback end for good once the ayah at [index] has finished, instead of repeating it or moving
     * on (the sleep timer's "end of surah"). `null` removes the limit. Does nothing until set.
     */
    fun stopAfterAyah(index: Int?) {
        stopAfterIndex = index
    }

    fun previous() = skip(-1)
    fun next() = skip(1)

    private fun skip(delta: Int) {
        if (ayahs.isEmpty()) return
        script?.let { order ->
            val position = (repeatsRemaining + delta).coerceIn(0, order.lastIndex)
            start(ReaderPlaybackStep(order[position], position))
            return
        }
        val bounds = playbackBounds()
        val index = (state.value.index + delta).coerceIn(bounds.first, bounds.last)
        start(freshStep(index, preferences, practice))
    }

    fun updateSettings(preferences: SurahReaderPreferences, practice: ReaderPracticeSettings) {
        val normalized = practice.normalized(ayahs.size)
        val rangeChanged = this.practice.rangeEnabled != normalized.rangeEnabled ||
            this.practice.startAyah != normalized.startAyah || this.practice.endAyah != normalized.endAyah
        val sequencingChanged = rangeChanged || this.practice.rangeRepeatCount != normalized.rangeRepeatCount ||
            this.preferences.repeatCount != preferences.repeatCount ||
            this.preferences.autoAdvanceAyahs != preferences.autoAdvanceAyahs
        val wasPlaying = state.value.isPlaying
        this.preferences = preferences
        this.practice = normalized
        if (sequencingChanged) {
            val index = if (rangeChanged && normalized.rangeEnabled) normalized.startAyah - 1 else state.value.index
            select(index)
            if (wasPlaying) play()
        } else if (wasPlaying && !inGap) {
            player.setPlaybackSpeed(preferences.playbackSpeed)
        }
    }

    private fun start(step: ReaderPlaybackStep) {
        val ayah = ayahs.getOrNull(step.index) ?: return
        invalidate()
        val request = generation
        player.stop()
        pendingStep = null
        pendingEnd = false
        gapRemainingMs = 0
        repeatsRemaining = step.repeatsRemaining
        echoRound = step.echoRound
        sequenceCompleted = false
        mutableState.value = ReaderAudioState(
            index = step.index, isPlaying = true, isPreparing = true,
            scriptPosition = if (script != null) step.repeatsRemaining else -1, scriptLength = script?.size ?: 0,
        )
        savePosition(step.index)
        pendingJob = scope.launch {
            val audioSource = source(ayah, surahNumber)
            ensureActive()
            if (request != generation) return@launch
            player.setPlaybackSpeed(preferences.playbackSpeed)
            player.play(audioSource, onError = {
                scope.launch {
                    if (request == generation) {
                        stop()
                        mutableState.value = state.value.copy(hasError = true)
                    }
                }
            }) {
                scope.launch {
                    if (request == generation) finishedAyah()
                }
            }
            mutableState.value = state.value.copy(isPreparing = false)
        }
    }

    private fun finishedAyah() {
        val order = script
        val limited = state.value.index == stopAfterIndex
        val step = when {
            limited -> null
            order != null -> order.getOrNull(repeatsRemaining + 1)?.let { ReaderPlaybackStep(it, repeatsRemaining + 1) }
            else -> nextReaderPlaybackStep(state.value.index, repeatsRemaining, ayahs.size, preferences, practice, echoRound)
        }
        // The echo silence takes the place of the pause and also follows the last reading: the person repeats it before the end.
        val echoing = practice.echo && !limited
        val gapMs = when {
            echoing -> EchoPlan.silenceMs(maxOf(state.value.durationMs, player.getDurationMs()), preferences.playbackSpeed)
            else -> practice.pauseSeconds * 1000L
        }
        if (step == null && !(echoing && gapMs > 0)) {
            completeSequence(natural = !limited)
            return
        }
        pendingStep = step
        pendingEnd = step == null
        gapRemainingMs = gapMs
        val total = EchoPlan.seconds(gapMs)
        if (state.value.isPlaying) waitForNext() else {
            mutableState.value = state.value.copy(pauseRemainingSeconds = total, pauseTotalSeconds = total)
        }
    }

    /** The sequence is over: the owner may carry on with something else, otherwise the state says "stopped". */
    private fun completeSequence(natural: Boolean) {
        pendingStep = null
        pendingEnd = false
        gapRemainingMs = 0
        if (state.value.pauseRemainingSeconds != 0 || state.value.pauseTotalSeconds != 0) {
            mutableState.value = state.value.copy(pauseRemainingSeconds = 0, pauseTotalSeconds = 0)
        }
        if (natural && onSequenceEnd?.invoke() == true) return
        sequenceCompleted = true
        mutableState.value = state.value.copy(
            isPlaying = false, canResume = false, positionMs = state.value.durationMs,
            pauseRemainingSeconds = 0, pauseTotalSeconds = 0,
            scriptDone = natural && script != null,
        )
    }

    private fun waitForNext() {
        pendingJob?.cancel()
        val request = generation
        val total = EchoPlan.seconds(gapRemainingMs)
        pendingJob = scope.launch {
            var tick = nowMs()
            while (gapRemainingMs > 0) {
                mutableState.value = state.value.copy(
                    pauseRemainingSeconds = ((gapRemainingMs + 999) / 1000).toInt(),
                    pauseTotalSeconds = maxOf(total, state.value.pauseTotalSeconds),
                )
                delay(minOf(100L, gapRemainingMs))
                gapRemainingMs = (gapRemainingMs - (nowMs() - tick)).coerceAtLeast(0)
                tick = nowMs()
            }
            if (request == generation && state.value.isPlaying) {
                if (pendingEnd) completeSequence(natural = true) else pendingStep?.let(::start)
            }
        }
    }

    private fun pause() {
        if (state.value.isPreparing) {
            stop()
        } else {
            pendingJob?.cancel()
            pendingJob = null
            player.pause()
            mutableState.value = state.value.copy(isPlaying = false, canResume = true)
        }
    }

    fun stop() {
        invalidate()
        player.stop()
        pendingStep = null
        pendingEnd = false
        gapRemainingMs = 0
        sequenceCompleted = false
        mutableState.value = ReaderAudioState(index = state.value.index)
    }

    fun close() {
        stop()
        ticker.cancel()
    }

    private fun invalidate() {
        generation++
        pendingJob?.cancel()
        pendingJob = null
    }

    private fun playbackBounds(): IntRange = if (practice.rangeEnabled) {
        (practice.startAyah - 1)..(practice.endAyah - 1)
    } else ayahs.indices

    /**
     * Whether the ayah that plays is remembered as "where the person stopped" (the "Continue" of the home screen). The listening
     * queue of "Listen to the old" turns it off: old passages are not a place to continue from.
     */
    var savesPosition: Boolean = true

    private fun savePosition(index: Int) {
        if (!savesPosition) return
        ayahs.getOrNull(index)?.let { persistPosition(surahNumber, it.globalNumber) }
    }
}

internal interface ReaderAudioOutput {
    fun play(url: String, onError: () -> Unit, onComplete: () -> Unit)
    fun pause()
    fun resume()
    fun setPlaybackSpeed(speed: Float)
    fun getPositionMs(): Long
    fun getDurationMs(): Long
    fun stop()
}

private class PlatformReaderAudioOutput : ReaderAudioOutput {
    private val audio = AudioPlayer()
    override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) = audio.play(url, onError, onComplete)
    override fun pause() = audio.pause()
    override fun resume() = audio.resume()
    override fun setPlaybackSpeed(speed: Float) = audio.setPlaybackSpeed(speed)
    override fun getPositionMs() = audio.getPositionMs()
    override fun getDurationMs() = audio.getDurationMs()
    override fun stop() = audio.stop()
}

private fun cachedAudioSource(): suspend (ReaderAudioAyah, Int) -> String {
    val cache = AudioCache()
    return { ayah, surah ->
        val remote = Reciters.default.audioUrl(ayah.globalNumber)
        cache.getOrFetch(remote, ayahAudioCacheKey(surah, ayah.numberInSurah)) ?: remote
    }
}

internal fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
