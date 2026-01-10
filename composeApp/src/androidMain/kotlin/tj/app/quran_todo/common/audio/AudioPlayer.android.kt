package tj.app.quran_todo.common.audio

import android.media.MediaPlayer

actual class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var pendingSpeed = 1f

    actual fun play(url: String, onComplete: () -> Unit) {
        stop()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setOnCompletionListener {
            onComplete()
        }
        try {
            player.setDataSource(url)
            player.prepareAsync()
            player.setOnPreparedListener {
                applySpeed(it)
                it.start()
            }
        } catch (_: Exception) {
            stop()
            onComplete()
        }
    }

    actual fun pause() {
        mediaPlayer?.pause()
    }

    actual fun resume() {
        mediaPlayer?.start()
    }

    actual fun setPlaybackSpeed(speed: Float) {
        pendingSpeed = speed.coerceIn(0.5f, 2.0f)
        mediaPlayer?.let { applySpeed(it) }
    }

    actual fun getDurationMs(): Long = mediaPlayer?.duration?.toLong() ?: 0L

    actual fun getPositionMs(): Long = mediaPlayer?.currentPosition?.toLong() ?: 0L

    actual fun stop() {
        mediaPlayer?.let { player ->
            try {
                player.stop()
            } catch (_: Exception) {
                // Ignore stop errors for already released players.
            }
            player.release()
        }
        mediaPlayer = null
    }

    private fun applySpeed(player: MediaPlayer) {
        try {
            player.playbackParams = player.playbackParams.setSpeed(pendingSpeed)
        } catch (_: Exception) {
            // Ignore playback param errors on some devices.
        }
    }
}
