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
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasError: Boolean = false,
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
    private var sequenceCompleted = false
    private var pendingStep: ReaderPlaybackStep? = null
    private var gapRemainingMs = 0L
    private var stopAfterIndex: Int? = null
    private val ticker = scope.launch {
        state.map { it.isPlaying }.distinctUntilChanged().collectLatest { playing ->
            if (playing) {
                while (currentCoroutineContext().isActive) {
                    if (pendingStep == null) {
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
        this.surahNumber = surahNumber
        this.ayahs = ayahs
        this.preferences = preferences
        this.practice = practice.normalized(ayahs.size)
        val index = ayahs.indexOfFirst { it.globalNumber == initialAyahGlobalNumber }.coerceAtLeast(0)
        mutableState.value = ReaderAudioState(index = index)
        if (preferences.autoplayOnOpen) play()
    }

    fun select(index: Int) {
        stop()
        if (index !in ayahs.indices) return
        mutableState.value = ReaderAudioState(index = index)
        savePosition(index)
    }

    fun toggle() {
        when {
            state.value.isPlaying -> pause()
            state.value.canResume && pendingStep != null -> {
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

    private fun play() {
        if (ayahs.isEmpty()) return
        val bounds = playbackBounds()
        val index = if (practice.rangeEnabled && sequenceCompleted) bounds.first else
            state.value.index.takeIf { it in bounds } ?: bounds.first
        start(ReaderPlaybackStep(index, if (practice.rangeEnabled) practice.rangeRepeatCount else preferences.repeatCount))
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
        val bounds = playbackBounds()
        val index = (state.value.index + delta).coerceIn(bounds.first, bounds.last)
        start(ReaderPlaybackStep(index, if (practice.rangeEnabled) practice.rangeRepeatCount else preferences.repeatCount))
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
        } else if (wasPlaying && pendingStep == null) {
            player.setPlaybackSpeed(preferences.playbackSpeed)
        }
    }

    private fun start(step: ReaderPlaybackStep) {
        val ayah = ayahs.getOrNull(step.index) ?: return
        invalidate()
        val request = generation
        player.stop()
        pendingStep = null
        gapRemainingMs = 0
        repeatsRemaining = step.repeatsRemaining
        sequenceCompleted = false
        mutableState.value = ReaderAudioState(index = step.index, isPlaying = true, isPreparing = true)
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
        val step = if (state.value.index == stopAfterIndex) {
            null
        } else {
            nextReaderPlaybackStep(state.value.index, repeatsRemaining, ayahs.size, preferences, practice)
        }
        if (step == null) {
            sequenceCompleted = true
            mutableState.value = state.value.copy(isPlaying = false, canResume = false, positionMs = state.value.durationMs)
            return
        }
        pendingStep = step
        gapRemainingMs = practice.pauseSeconds * 1000L
        if (state.value.isPlaying) waitForNext() else {
            mutableState.value = state.value.copy(pauseRemainingSeconds = practice.pauseSeconds)
        }
    }

    private fun waitForNext() {
        pendingJob?.cancel()
        val request = generation
        pendingJob = scope.launch {
            var tick = nowMs()
            while (gapRemainingMs > 0) {
                mutableState.value = state.value.copy(pauseRemainingSeconds = ((gapRemainingMs + 999) / 1000).toInt())
                delay(minOf(100L, gapRemainingMs))
                gapRemainingMs = (gapRemainingMs - (nowMs() - tick)).coerceAtLeast(0)
                tick = nowMs()
            }
            if (request == generation && state.value.isPlaying) pendingStep?.let(::start)
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

    private fun savePosition(index: Int) {
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
