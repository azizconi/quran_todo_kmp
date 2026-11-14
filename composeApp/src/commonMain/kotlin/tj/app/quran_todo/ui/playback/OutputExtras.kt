package tj.app.quran_todo.ui.playback

/**
 * What the player does besides sounding the reader session: the sample of a reciter and letting go of
 * the audio focus. Implemented by [ExoReaderAudioOutput] on Android, so there is one player for the whole app.
 *
 * iOS (S2): implemented by the AVPlayer-based output next to `ReaderAudioOutput` (`play`, `pause`, `resume`, `setPlaybackSpeed`,
 * positions, `stop`): the sample plays on the same player, `releaseFocus` deactivates the `AVAudioSession`, `seekTo` and `startNextAt`
 * seek the player.
 */
interface OutputExtras {
    /** Plays [source] once (at normal speed); [onFinished] tells whether it failed. Cuts what was playing. */
    fun playSample(source: String, onFinished: (failed: Boolean) -> Unit)

    /** Stops a sample that is playing; its `onFinished` is not called. */
    fun stopSample()

    /** The ayah sequence is over: gives the audio focus back so that other apps can play again. */
    fun releaseFocus()

    /** Where the file that sounds now is, in milliseconds, read from the player at this very moment; 0 when nothing is loaded. */
    fun currentPositionMs(): Long = 0L

    /** Moves the file that sounds (or is paused) now to [positionMs]. Does nothing when no ayah is loaded or a sample plays. */
    fun seekTo(positionMs: Long) {}

    /**
     * The ayah that starts next begins at [positionMs] instead of its start (a tap on a word of an ayah that is not playing). It is
     * applied once, and dropped when the player is stopped before the ayah has started.
     */
    fun startNextAt(positionMs: Long) {}
}
