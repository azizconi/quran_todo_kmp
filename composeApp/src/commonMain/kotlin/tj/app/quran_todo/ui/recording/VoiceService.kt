package tj.app.quran_todo.ui.recording

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tj.app.quran_todo.ui.downloads.RecordingsStorage

/** The passage a recording belongs to: the ayahs [startAyah]..[endAyah] (numbers in the surah) of [surah]. */
data class PassageKey(val surah: Int, val startAyah: Int, val endAyah: Int) {
    /** The folder name of the passage: `78_1_5`. */
    val folder: String get() = "${surah}_${startAyah}_$endAyah"
}

/** One recording of the person's own reading: the file ([path]), when it was started ([epochMillis]) and its size. */
data class Recording(val path: String, val key: PassageKey, val epochMillis: Long, val sizeBytes: Long)

/** What the recorder is doing. */
@Immutable
sealed interface RecorderState {
    data object Idle : RecorderState

    /** Recording the passage [key]: [elapsedMs] since the start, [level] (0..1) the loudness of the last moment. */
    data class Recording(val key: PassageKey, val elapsedMs: Long, val level: Float) : RecorderState
}

/** What the player of "My reading" is doing. [path] is the recording that is loaded (playing or paused), null when none. */
@Immutable
data class PlayerState(
    val path: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/** The answer to [VoiceService.start]. */
enum class StartResult { Started, AlreadyRecording, Failed }

/** A recording with its length, as the list of "My reading" shows it. */
@Immutable
data class VoiceNote(val recording: Recording, val durationMs: Long)

/**
 * The microphone of the app ("My reading"): records the person's own reading of a passage and plays it back. Everything is on the phone.
 * One recording and one playback at a time; each silences the reciter first and playing the reciter stops the person's own recording.
 * The microphone permission is the screen's business ([start] must be called with it granted).
 *
 * Android: `VoiceController` (MediaRecorder AAC `.m4a`, MediaPlayer, audio focus). iOS (S2): `AVAudioRecorder` (AAC `.m4a`, mono,
 * 64 kbit/s, 44.1 kHz, at most 10 minutes, `AVAudioSession` category `playAndRecord`) writing `{surah}_{start}_{end}/{epochMillis}.m4a`
 * under Application Support (no iCloud backup), metering for [RecorderState.Recording.level], an `AVAudioPlayer` for playback,
 * and a stop when an interruption (call) begins.
 */
interface VoiceService : RecordingsStorage {
    val recorder: StateFlow<RecorderState>
    val player: StateFlow<PlayerState>

    /** The recordings of [key] with their lengths, the newest first; read again whenever a file is added or removed. */
    fun notes(key: PassageKey): Flow<List<VoiceNote>>

    /** How many recordings [key] has. */
    fun count(key: PassageKey): Flow<Int>

    /** Removes one recording for good, at once (the sheet that held it is closing). */
    fun deleteNow(recording: Recording)

    /** Removes one recording for good. */
    suspend fun delete(recording: Recording)

    /** Starts recording [key]. */
    fun start(key: PassageKey): StartResult

    /** Stops the recording and keeps what was made (null when nothing worth keeping, or when none runs). */
    fun stop(): Recording?

    /** Plays the file at [path], or pauses / resumes it when it is the recording that is loaded. The reciter is silenced first. */
    fun toggle(path: String)

    fun stopPlayback()

    /** Lets go of the recorder and the player (the process is going away). */
    fun release()
}
