package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.ReaderAudioOutput
import java.io.File

/** Pauses before the first and the second retry of an ayah that failed to play; then it is an error. */
internal val DefaultRetryDelaysMs: List<Long> = listOf(1_000L, 2_000L)

/**
 * The thin layer under [ExoReaderAudioOutput]: one playable file or URL at a time. The production
 * implementation is [Media3Engine] (one ExoPlayer for the whole app); tests use a fake.
 * Everything is called on the main thread.
 */
internal interface PlayerEngine {
    var listener: Listener?

    /** Loads [source] (a file path or an http(s) URL), prepares it and starts as soon as it is ready. */
    fun load(source: String)

    /** Prepares the loaded source again after an error. */
    fun prepare()

    fun play()
    fun pause()

    /** Stops and forgets the loaded source; the player lets go of the audio focus. */
    fun stop()
    fun setSpeed(speed: Float)
    val positionMs: Long
    val durationMs: Long

    interface Listener {
        /** The source played to its end. */
        fun onEnded()

        /** The source cannot be played (no network, a broken file). */
        fun onError()

        /** The system paused the engine: audio focus was lost for good, or the headphones were unplugged. */
        fun onPausedBySystem()
    }
}

/**
 * What the player does besides sounding the reader session: the sample of a reciter and letting go of
 * the audio focus. Implemented by [ExoReaderAudioOutput], so there is one player for the whole app.
 */
internal interface OutputExtras {
    /** Plays [source] once (at normal speed); [onFinished] tells whether it failed. Cuts what was playing. */
    fun playSample(source: String, onFinished: (failed: Boolean) -> Unit)

    /** Stops a sample that is playing; its `onFinished` is not called. */
    fun stopSample()

    /** The ayah sequence is over: gives the audio focus back so that other apps can play again. */
    fun releaseFocus()
}

/**
 * [ReaderAudioOutput] on a [PlayerEngine] (Media3 ExoPlayer in production). The sequencing of repeats,
 * pauses and ranges stays in `ReaderAudioSession`; this class only plays one ayah at a time and adds:
 *
 * - retries: an ayah that fails to play is tried again after each of [retryDelaysMs] (1 s, then 2 s)
 *   before [ReaderAudioOutput.play]'s `onError` is called;
 * - system pauses: when the engine is paused by the system (permanent audio focus loss, becoming noisy)
 *   [pausedBySystem] is told so that the session can show the paused state;
 * - the reciter sample and the release of the audio focus ([OutputExtras]).
 *
 * A file that still cannot be played after the retries is deleted ([onSourceFailed]) so that a manual
 * retry downloads it again instead of failing on a broken cache file.
 */
internal class ExoReaderAudioOutput(
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    private val pausedBySystem: () -> Unit,
    private val retryDelaysMs: List<Long> = DefaultRetryDelaysMs,
    private val onSourceFailed: (String) -> Unit = ::deleteIfLocalFile,
) : ReaderAudioOutput, PlayerEngine.Listener, OutputExtras {
    private var sampleCallback: ((Boolean) -> Unit)? = null
    private var errorCallback: (() -> Unit)? = null
    private var completeCallback: (() -> Unit)? = null
    private var source: String? = null
    private var failures = 0
    private var retryJob: Job? = null

    init {
        engine.listener = this
    }

    override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
        cancelRetry()
        dropSample()
        errorCallback = onError
        completeCallback = onComplete
        source = url
        failures = 0
        engine.load(url)
    }

    override fun pause() = engine.pause()

    override fun resume() = engine.play()

    override fun setPlaybackSpeed(speed: Float) = engine.setSpeed(speed.coerceIn(MinSpeed, MaxSpeed))

    override fun getPositionMs(): Long = engine.positionMs.coerceAtLeast(0L)

    override fun getDurationMs(): Long = engine.durationMs.coerceAtLeast(0L)

    override fun stop() {
        cancelRetry()
        forget()
        dropSample()
        engine.stop()
    }

    override fun playSample(source: String, onFinished: (failed: Boolean) -> Unit) {
        cancelRetry()
        forget()
        dropSample()
        sampleCallback = onFinished
        engine.setSpeed(1f)
        engine.load(source)
    }

    override fun stopSample() {
        if (sampleCallback == null) return
        sampleCallback = null
        engine.stop()
    }

    override fun releaseFocus() {
        if (sampleCallback == null && source == null) engine.stop()
    }

    override fun onEnded() {
        if (endSample(failed = false)) return
        val complete = completeCallback ?: return
        forget()
        complete()
    }

    override fun onError() {
        if (endSample(failed = true)) return
        val failed = source ?: return
        val delayMs = retryDelaysMs.getOrNull(failures)
        if (delayMs == null) {
            val fail = errorCallback
            forget()
            onSourceFailed(failed)
            fail?.invoke()
            return
        }
        failures++
        cancelRetry()
        retryJob = scope.launch {
            delay(delayMs)
            if (source == failed) engine.prepare()
        }
    }

    override fun onPausedBySystem() {
        if (endSample(failed = false)) return
        pausedBySystem()
    }

    /** The sample is over (it ended, failed or was paused by the system): the player is idle again. */
    private fun endSample(failed: Boolean): Boolean {
        val callback = sampleCallback ?: return false
        sampleCallback = null
        engine.stop()
        callback(failed)
        return true
    }

    /** Another playback takes the player over: the sample is cut, which is not a failure. */
    private fun dropSample() {
        val callback = sampleCallback ?: return
        sampleCallback = null
        callback(false)
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun forget() {
        errorCallback = null
        completeCallback = null
        source = null
    }

    private companion object {
        const val MinSpeed = 0.5f
        const val MaxSpeed = 2f
    }
}

private fun deleteIfLocalFile(source: String) {
    if (source.startsWith("/")) File(source).delete()
}
