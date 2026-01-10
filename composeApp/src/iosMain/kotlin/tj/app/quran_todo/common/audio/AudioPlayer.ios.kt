package tj.app.quran_todo.common.audio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.CoreMedia.CMTimeGetSeconds

@OptIn(ExperimentalForeignApi::class)
actual class AudioPlayer {
    private var player: AVPlayer? = null
    private var observer: Any? = null
    private var playbackRate: Float = 1f

    actual fun play(url: String, onComplete: () -> Unit) {
        stop()
        val nsUrl = if (url.startsWith("http")) {
            NSURL.URLWithString(url)
        } else {
            NSURL.fileURLWithPath(url)
        } ?: return
        val item = AVPlayerItem(uRL = nsUrl)
        val avPlayer = AVPlayer(playerItem = item)
        player = avPlayer
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = null
        ) { _ ->
            onComplete()
        }
        avPlayer.play()
        avPlayer.rate = playbackRate
    }

    actual fun pause() {
        player?.pause()
    }

    actual fun resume() {
        player?.play()
        player?.rate = playbackRate
    }

    actual fun setPlaybackSpeed(speed: Float) {
        playbackRate = speed.coerceIn(0.5f, 2.0f)
        player?.rate = playbackRate
    }

    actual fun getDurationMs(): Long {
        val seconds = player?.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
        return if (seconds.isFinite()) (seconds * 1000).toLong() else 0L
    }

    actual fun getPositionMs(): Long {
        val seconds = player?.currentTime()?.let { CMTimeGetSeconds(it) } ?: 0.0
        return if (seconds.isFinite()) (seconds * 1000).toLong() else 0L
    }

    actual fun stop() {
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
        observer?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        observer = null
    }
}
