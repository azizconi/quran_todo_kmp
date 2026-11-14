package tj.app.quran_todo.ui.playback

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.Foundation.NSNumber
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueCount
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueIndex
import platform.MediaPlayer.MPNowPlayingPlaybackState
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.Foundation.NSBundle
import tj.app.quran_todo.common.audio.ReaderAudioState
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.settings.SurahReaderPreferences

/** The service link of iOS: there is no service, the link only switches the remote commands (lock screen, headset, car) on and off. */
internal class IosPlaybackServiceLink(private val bridge: IosNowPlayingBridge) : PlaybackServiceLink {
    override fun ensureConnected() = bridge.connect()

    override fun release() = bridge.disconnect()
}

/**
 * Shows the [PlaybackController] to the system, the iOS twin of `PlaybackService` + `PlaybackSessionPlayer`:
 *  - `MPNowPlayingInfoCenter`: title "Surah · ayah", the reciter as artist, the surah as album, the elapsed time and duration of
 *    the ayah that sounds, the rate (so the lock screen moves the bar by itself) and the cover (the app icon);
 *  - `MPRemoteCommandCenter`: play, pause, toggle, next ayah, previous ayah and the seek bar, all sent back to the controller.
 *
 * Everything is on the main thread. [surahTitle] and [reciterName] are read at the moment an update is made.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosNowPlayingBridge(
    private val controllerProvider: () -> PlaybackController,
    private val scope: CoroutineScope,
    private val surahTitle: (Int) -> String,
    private val reciterName: () -> String,
    private val speed: () -> Float = { SurahReaderPreferences.load().playbackSpeed },
) {
    private val controller: PlaybackController get() = controllerProvider()
    private var connected = false
    private var job: Job? = null
    private var commandsInstalled = false
    private val artwork: MPMediaItemArtwork? by lazy { loadArtwork() }

    /** Called when the system pauses the audio (an interruption began); a later "should resume" plays again. */
    var pausedByInterruption = false

    fun connect() {
        if (connected) return
        connected = true
        installCommands()
        job = scope.launch { observe() }
    }

    fun disconnect() {
        connected = false
        job?.cancel()
        job = null
        clear()
    }

    /** The system said an interruption is over and playback may resume: plays again if the interruption is what paused it. */
    fun interruptionEnded() {
        if (!pausedByInterruption) return
        pausedByInterruption = false
        if (!controller.state.value.isPlaying && controller.nowPlaying.value != null) controller.toggle()
    }

    private suspend fun observe() {
        // Whatever changes what the lock screen shows (the ayah, play/pause, the duration) refreshes it; the position alone does not,
        // the system moves the bar by the rate. While audio sounds the elapsed time is corrected every 2 s.
        scope.launch {
            combine(
                controller.nowPlaying,
                controller.state.map { Key(it.index, it.isPlaying, it.isPreparing, it.durationMs, it.pauseRemainingSeconds > 0) }
                    .distinctUntilChanged(),
                controller.listenQueue,
            ) { _, _, _ -> }.collect { update() }
        }
        controller.state.map { it.isPlaying }.distinctUntilChanged().collectLatest { playing ->
            while (playing) {
                delay(ResyncMs)
                update()
            }
        }
    }

    private data class Key(val index: Int, val playing: Boolean, val preparing: Boolean, val durationMs: Long, val silence: Boolean)

    private fun update() {
        val now = controller.nowPlaying.value
        val state = controller.state.value
        val center = MPNowPlayingInfoCenter.defaultCenter()
        if (now == null || now.ayahs.isEmpty()) {
            clear()
            return
        }
        val index = state.index.coerceIn(now.ayahs.indices)
        val ayah = now.ayahs[index].numberInSurah
        val surahName = surahTitle(now.surahNumber)
        val moving = state.isPlaying && !state.isPreparing && state.pauseRemainingSeconds == 0
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to "$surahName · $ayah",
            MPMediaItemPropertyArtist to reciterName(),
            MPMediaItemPropertyAlbumTitle to surahName,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = state.positionMs / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double = if (moving) speed().toDouble() else 0.0),
            MPNowPlayingInfoPropertyDefaultPlaybackRate to NSNumber(double = speed().toDouble()),
            MPNowPlayingInfoPropertyPlaybackQueueIndex to NSNumber(int = index),
            MPNowPlayingInfoPropertyPlaybackQueueCount to NSNumber(int = now.ayahs.size),
        )
        if (state.durationMs > 0) info[MPMediaItemPropertyPlaybackDuration] = NSNumber(double = state.durationMs / 1000.0)
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        center.nowPlayingInfo = info
        center.playbackState = if (state.isPlaying) MPNowPlayingPlaybackStatePlaying else MPNowPlayingPlaybackStatePaused
    }

    private fun clear() {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        center.nowPlayingInfo = null
        center.playbackState = MPNowPlayingPlaybackStateStopped
    }

    private fun installCommands() {
        if (commandsInstalled) return
        commandsInstalled = true
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        fun MPRemoteCommand.on(block: () -> Boolean) {
            enabled = true
            addTargetWithHandler { _ -> if (block()) MPRemoteCommandHandlerStatusSuccess else MPRemoteCommandHandlerStatusCommandFailed }
        }
        commands.playCommand.on { play(); true }
        commands.pauseCommand.on {
            if (controller.state.value.isPlaying) controller.toggle()
            true
        }
        commands.togglePlayPauseCommand.on {
            if (controller.state.value.isPlaying) controller.toggle() else play()
            true
        }
        commands.nextTrackCommand.on { controller.next(); true }
        commands.previousTrackCommand.on { controller.previous(); true }
        commands.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            controller.seekInAyah((seconds * 1000).toLong())
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.changePlaybackPositionCommand.enabled = true
        // Not offered: the ayahs are the items, skipping by seconds has no meaning.
        commands.skipForwardCommand.enabled = false
        commands.skipBackwardCommand.enabled = false
        commands.seekForwardCommand.enabled = false
        commands.seekBackwardCommand.enabled = false
    }

    /** Play from the lock screen: like the notification of Android, a finished surah starts again from its first ayah. */
    private fun play() {
        val state = controller.state.value
        if (controller.nowPlaying.value == null || state.isPlaying) return
        val last = controller.nowPlaying.value?.ayahs?.lastIndex
        val finishedSurah = !state.canResume && last != null && state.index == last && state.durationMs > 0 && state.positionMs >= state.durationMs
        if (finishedSurah) controller.select(0)
        controller.toggle()
    }

    /** The app icon as the cover of the lock screen, when the bundle names one. */
    private fun loadArtwork(): MPMediaItemArtwork? {
        val icons = NSBundle.mainBundle.infoDictionary?.get("CFBundleIcons") as? Map<*, *>
        val primary = icons?.get("CFBundlePrimaryIcon") as? Map<*, *>
        val files = primary?.get("CFBundleIconFiles") as? List<*>
        val name = files?.lastOrNull() as? String ?: (primary?.get("CFBundleIconName") as? String) ?: return null
        val image = UIImage.imageNamed(name) ?: return null
        return MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
    }

    private companion object {
        const val ResyncMs = 2_000L
    }
}

/** The language of the app as the system shows it (the reciter and the surah names follow it). */
internal fun currentAppLanguage(): AppLanguage = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
