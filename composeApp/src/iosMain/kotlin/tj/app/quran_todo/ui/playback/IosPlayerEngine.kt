package tj.app.quran_todo.ui.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

/** Pauses before the first and the second retry of an ayah that failed to play; then it is an error. */
internal val DefaultRetryDelaysMs: List<Long> = listOf(1_000L, 2_000L)

/**
 * The thin layer under [IosReaderAudioOutput]: one playable file or URL at a time (the same contract as the Android `PlayerEngine`).
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

    /** Stops and forgets the loaded source. */
    fun stop()
    fun setSpeed(speed: Float)
    val positionMs: Long
    val durationMs: Long

    /** Moves the loaded source to [positionMs]; before the source is ready it becomes the place where it starts. */
    fun seekTo(positionMs: Long) {}

    interface Listener {
        /** The source played to its end. */
        fun onEnded()

        /** The source cannot be played (no network, a broken file). */
        fun onError()

        /** The system paused the engine: an interruption began (a call, Siri, another app) or the headphones were unplugged. */
        fun onPausedBySystem()
    }
}

/**
 * The one AVPlayer of the app, in an `AVAudioSession` of category playback (speech), so that audio goes on with the screen locked
 * (the `audio` background mode of Info.plist). It watches the interruptions of the session and the route changes: an interruption
 * that began and unplugged headphones pause and tell the listener ([PlayerEngine.Listener.onPausedBySystem]); when an interruption
 * ends with "should resume", [onInterruptionEnded] is called (the wiring decides whether to play again).
 */
@OptIn(ExperimentalForeignApi::class)
internal class AvPlayerEngine(private val scope: CoroutineScope) : PlayerEngine {
    override var listener: PlayerEngine.Listener? = null

    /** An interruption of the audio session ended and the system says playback may resume. */
    var onInterruptionEnded: () -> Unit = {}

    private var player: AVPlayer? = null
    private var item: AVPlayerItem? = null
    private var source: String? = null
    private var speed = 1f
    private var pendingSeekMs = -1L
    private val itemObservers = mutableListOf<NSObjectProtocol>()
    private val sessionObservers = mutableListOf<NSObjectProtocol>()
    private var watchJob: Job? = null
    private var failureReported = false

    init {
        val center = NSNotificationCenter.defaultCenter
        sessionObservers += center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { note ->
            onInterruption(note)
        }
        sessionObservers += center.addObserverForName(AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { note ->
            onRouteChange(note)
        }
    }

    override fun load(source: String) {
        activateSession()
        detachItem()
        this.source = source
        failureReported = false
        attach(source)
        applyPending()
        startPlayer()
    }

    override fun prepare() {
        val src = source ?: return
        // A failed item cannot be revived: a new item of the same source.
        val resume = pendingSeekMs
        detachItem()
        failureReported = false
        attach(src)
        pendingSeekMs = resume
        applyPending()
        startPlayer()
    }

    override fun play() {
        if (item == null) return
        activateSession()
        startPlayer()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        player?.pause()
        detachItem()
        source = null
        pendingSeekMs = -1L
        player?.replaceCurrentItemWithPlayerItem(null)
    }

    /** Lets other apps play again (after the last ayah). */
    fun deactivateSession() {
        runCatching {
            AVAudioSession.sharedInstance().setActive(
                false,
                withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                error = null,
            )
        }
    }

    override fun seekTo(positionMs: Long) {
        pendingSeekMs = positionMs
        applyPending()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        val p = player ?: return
        if (p.rate != 0f) p.rate = speed
    }

    override val positionMs: Long
        get() {
            val p = player
            if (p == null || item == null) return 0L
            val seconds = CMTimeGetSeconds(p.currentTime())
            return if (seconds.isFinite()) (seconds * 1000).toLong() else 0L
        }

    override val durationMs: Long
        get() {
            val current = item ?: return 0L
            val seconds = CMTimeGetSeconds(current.duration)
            return if (seconds.isFinite()) (seconds * 1000).toLong() else 0L
        }

    private fun ensurePlayer(): AVPlayer = player ?: AVPlayer().also { player = it }

    private fun attach(src: String) {
        val url = if (src.startsWith("/")) NSURL.fileURLWithPath(src) else NSURL.URLWithString(src)
        if (url == null) {
            scope.launch { fail(null) }
            return
        }
        val created = AVPlayerItem(uRL = url)
        item = created
        val center = NSNotificationCenter.defaultCenter
        itemObservers += center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, created, NSOperationQueue.mainQueue) { _ ->
            if (item === created) listener?.onEnded()
        }
        itemObservers += center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, created, NSOperationQueue.mainQueue) { _ ->
            fail(created)
        }
        ensurePlayer().replaceCurrentItemWithPlayerItem(created)
        // The status of a loading item is read every 150 ms (no KVO needed for "it failed to load").
        watchJob = scope.launch {
            while (isActive) {
                delay(WatchMs)
                if (created.status == AVPlayerItemStatusFailed) {
                    fail(created)
                    return@launch
                }
            }
        }
    }

    private fun fail(failed: AVPlayerItem?) {
        if (failed != null && item !== failed) return
        if (failureReported) return
        failureReported = true
        watchJob?.cancel()
        listener?.onError()
    }

    private fun detachItem() {
        watchJob?.cancel()
        watchJob = null
        itemObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        itemObservers.clear()
        item = null
    }

    private fun startPlayer() {
        val p = ensurePlayer()
        p.play()
        p.rate = speed
    }

    private fun applyPending() {
        val at = pendingSeekMs
        val p = player ?: return
        if (at < 0L || item == null) return
        p.seekToTime(
            CMTimeMakeWithSeconds(at / 1000.0, 1000),
            toleranceBefore = CMTimeMakeWithSeconds(0.0, 1000),
            toleranceAfter = CMTimeMakeWithSeconds(0.0, 1000),
        )
        pendingSeekMs = -1L
    }

    private fun activateSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeSpokenAudio, 0uL, null)
            session.setActive(true, error = null)
        }
    }

    private fun onInterruption(note: NSNotification?) {
        val info = note?.userInfo ?: return
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return
        if (type == AVAudioSessionInterruptionTypeBegan) {
            if (item != null && (player?.rate ?: 0f) != 0f) {
                player?.pause()
                listener?.onPausedBySystem()
            }
        } else {
            val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
            if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) onInterruptionEnded()
        }
    }

    private fun onRouteChange(note: NSNotification?) {
        val info = note?.userInfo ?: return
        val reason = (info[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.unsignedLongValue ?: return
        if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable && item != null && (player?.rate ?: 0f) != 0f) {
            player?.pause()
            listener?.onPausedBySystem()
        }
    }

    private companion object {
        const val WatchMs = 150L
    }
}
