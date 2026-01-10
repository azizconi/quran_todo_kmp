package tj.app.quran_todo.common.audio

expect class AudioPlayer() {
    fun play(url: String, onComplete: () -> Unit)
    fun pause()
    fun resume()
    fun setPlaybackSpeed(speed: Float)
    fun getDurationMs(): Long
    fun getPositionMs(): Long
    fun stop()
}
