package tj.app.quran_todo.common.audio

import android.media.MediaPlayer

actual class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var pendingSpeed = 1f
    private var isPrepared = false
    private var playWhenReady = false

    actual fun play(url: String, onComplete: () -> Unit) {
        stop()
        val player = MediaPlayer()
        var callbackDelivered = false
        fun completeOnce() {
            if (callbackDelivered) return
            callbackDelivered = true
            onComplete()
        }
        mediaPlayer = player
        playWhenReady = true
        player.setOnCompletionListener {
            if (mediaPlayer !== player) return@setOnCompletionListener
            playWhenReady = false
            completeOnce()
        }
        player.setOnErrorListener { failedPlayer, _, _ ->
            if (mediaPlayer === failedPlayer) {
                stop()
                completeOnce()
            }
            true
        }
        try {
            player.setDataSource(url)
            player.setOnPreparedListener {
                if (mediaPlayer !== it) return@setOnPreparedListener
                isPrepared = true
                applySpeed(it)
                if (playWhenReady) {
                    runCatching { it.start() }
                        .onFailure {
                            stop()
                            completeOnce()
                        }
                }
            }
            player.prepareAsync()
        } catch (_: Exception) {
            stop()
            completeOnce()
        }
    }

    actual fun pause() {
        playWhenReady = false
        if (isPrepared) {
            mediaPlayer?.let { player ->
                runCatching { player.pause() }
            }
        }
    }

    actual fun resume() {
        playWhenReady = true
        if (isPrepared) {
            mediaPlayer?.let { player ->
                runCatching { player.start() }
            }
        }
    }

    actual fun setPlaybackSpeed(speed: Float) {
        pendingSpeed = speed.coerceIn(0.5f, 2.0f)
        if (isPrepared) {
            mediaPlayer?.let { applySpeed(it) }
        }
    }

    actual fun getDurationMs(): Long =
        if (isPrepared) {
            mediaPlayer?.let { runCatching { it.duration.toLong() }.getOrDefault(0L) } ?: 0L
        } else {
            0L
        }

    actual fun getPositionMs(): Long =
        if (isPrepared) {
            mediaPlayer?.let {
                runCatching { it.currentPosition.toLong() }.getOrDefault(0L)
            } ?: 0L
        } else {
            0L
        }

    actual fun stop() {
        playWhenReady = false
        isPrepared = false
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
