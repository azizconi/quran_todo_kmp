package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.ReaderAudioOutput
import tj.app.quran_todo.ios.File

/**
 * [ReaderAudioOutput] on a [PlayerEngine] (an AVPlayer in production): the iOS twin of the Android `ExoReaderAudioOutput`, with the
 * same behaviour. The sequencing of repeats, pauses and ranges stays in `ReaderAudioSession`; this class only plays one ayah at a
 * time and adds:
 *
 * - retries: an ayah that fails to play is tried again after each of [retryDelaysMs] (1 s, then 2 s) before [ReaderAudioOutput.play]'s
 *   `onError` is called;
 * - system pauses: when the engine is paused by the system (an interruption, headphones unplugged) [pausedBySystem] is told so that
 *   the session can show the paused state;
 * - the reciter sample and the release of the audio session ([OutputExtras]).
 *
 * A file that still cannot be played after the retries is deleted ([onSourceFailed]) so that a manual retry downloads it again.
 */
internal class IosReaderAudioOutput(
    private val engine: PlayerEngine,
    private val scope: CoroutineScope,
    private val pausedBySystem: () -> Unit,
    private val onReleaseFocus: () -> Unit = {},
    private val retryDelaysMs: List<Long> = DefaultRetryDelaysMs,
    private val onSourceFailed: (String) -> Unit = ::deleteIfLocalFile,
) : ReaderAudioOutput, PlayerEngine.Listener, OutputExtras {
    private var sampleCallback: ((Boolean) -> Unit)? = null
    private var errorCallback: (() -> Unit)? = null
    private var completeCallback: (() -> Unit)? = null
    private var source: String? = null
    private var failures = 0
    private var retryJob: Job? = null
    private var pendingStartMs = NoStart

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
        if (pendingStartMs > 0L) engine.seekTo(pendingStartMs)
        pendingStartMs = NoStart
    }

    override fun pause() = engine.pause()

    override fun resume() = engine.play()

    override fun setPlaybackSpeed(speed: Float) = engine.setSpeed(speed.coerceIn(MinSpeed, MaxSpeed))

    override fun getPositionMs(): Long = engine.positionMs.coerceAtLeast(0L)

    override fun getDurationMs(): Long = engine.durationMs.coerceAtLeast(0L)

    override fun currentPositionMs(): Long = if (source != null) engine.positionMs.coerceAtLeast(0L) else 0L

    override fun seekTo(positionMs: Long) {
        // No source: nothing is loaded yet, or the ayah has ended.
        if (source == null || sampleCallback != null) return
        engine.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun startNextAt(positionMs: Long) {
        pendingStartMs = positionMs
    }

    override fun stop() {
        pendingStartMs = NoStart
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
        if (sampleCallback == null && source == null) {
            engine.stop()
            onReleaseFocus()
        }
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
        const val NoStart = -1L
    }
}

private fun deleteIfLocalFile(source: String) {
    if (source.startsWith("/")) File(source).delete()
}
