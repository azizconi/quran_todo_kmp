package tj.app.quran_todo.ui.playback

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import java.io.File

/**
 * The one ExoPlayer of the app (created on first use, on the main thread). It plays the recitation
 * as speech media, takes and gives up audio focus, pauses when the headphones are unplugged and keeps
 * the CPU and the Wi-Fi awake while it plays (`C.WAKE_MODE_NETWORK`).
 *
 * This is not the player the media session shows: that is `PlaybackSessionPlayer`, which mirrors the
 * playback controller and sends the commands of the notification and the headset back to it.
 */
internal class Media3Engine(private val context: Context) : PlayerEngine, Player.Listener {
    override var listener: PlayerEngine.Listener? = null

    private val player: ExoPlayer by lazy {
        val appContext = context.applicationContext
        ExoPlayer.Builder(appContext)
            .setLooper(Looper.getMainLooper())
            // ExoPlayer would retry a failed download three times by itself (0, 1 and 2 s apart) before it
            // reports an error. The retries (1 s, then 2 s) are ours, in ExoReaderAudioOutput: one layer only.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(appContext)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 0)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(this) }
    }

    // Set before the player exists, applied when it is created.
    private var speed = 1f
    private var created = false

    override fun load(source: String) {
        val exo = player
        created = true
        exo.setMediaItem(MediaItem.fromUri(uriOf(source)))
        exo.setPlaybackSpeed(speed)
        exo.prepare()
        exo.play()
    }

    override fun prepare() {
        if (created) player.prepare()
    }

    override fun play() {
        if (created) player.play()
    }

    override fun pause() {
        if (created) player.pause()
    }

    override fun stop() {
        if (!created) return
        player.stop()
        player.clearMediaItems()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        if (created) player.setPlaybackSpeed(speed)
    }

    override val positionMs: Long
        get() = if (created) player.currentPosition else 0L

    override val durationMs: Long
        get() = if (created) player.duration.takeIf { it != C.TIME_UNSET } ?: 0L else 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) listener?.onEnded()
    }

    override fun onPlayerError(error: PlaybackException) {
        listener?.onError()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady &&
            (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
        ) {
            listener?.onPausedBySystem()
        }
    }

    private fun uriOf(source: String): Uri =
        if (source.startsWith("/")) Uri.fromFile(File(source)) else Uri.parse(source)
}
