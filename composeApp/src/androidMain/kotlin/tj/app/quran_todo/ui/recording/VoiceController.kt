package tj.app.quran_todo.ui.recording

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.ui.playback.PlaybackController

/** What the recorder is doing. */
@Immutable
sealed interface RecorderState {
    data object Idle : RecorderState

    /** Recording the passage [key]: [elapsedMs] since the start, [level] (0..1) the loudness of the last moment. */
    data class Recording(val key: PassageKey, val elapsedMs: Long, val level: Float) : RecorderState
}

/** What the player of "My reading" is doing. [file] is the recording that is loaded (playing or paused), null when none. */
@Immutable
data class PlayerState(
    val file: File? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/** The answer to [VoiceController.start]. */
enum class StartResult { Started, AlreadyRecording, Failed }

/** A recording with its length, as the list of "My reading" shows it. */
@Immutable
data class VoiceNote(val recording: Recording, val durationMs: Long)

/**
 * The microphone of the app (task F2a): records the person's own reading of a passage and plays it back. Everything is on the
 * phone - the files are in [RecordingStore], nothing is sent anywhere. One recording and one playback at a time; each takes the sound
 * of the reciter off first ([PlaybackController.stop]) and playing the reciter stops the person's own recording (the other way round).
 *
 * - **Recording:** [MediaRecorder], AAC in `.m4a`, mono, 64 kbit/s, at most [MAX_MILLIS] (10 minutes). Audio focus is held for as long as
 *   it runs; losing it (a call, another app that records or plays) stops the recording and keeps what was made.
 * - **Playing:** a plain [MediaPlayer] (no notification, no media session), with the speech attributes and its own focus.
 *
 * It is a Koin single and lives with the process; the screens only ask. The microphone permission is the screen's business
 * ([start] must be called with it granted).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceController internal constructor(
    context: Context,
    private val store: RecordingStore,
    private val playback: PlaybackController,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _recorder = MutableStateFlow<RecorderState>(RecorderState.Idle)

    /** The recorder: idle, or recording a passage. */
    val recorder: StateFlow<RecorderState> = _recorder.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())

    /** The player of the recordings. */
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    /** Bumped whenever the set of files changes: the lists read again. */
    private val revision = MutableStateFlow(0)

    private var mediaRecorder: MediaRecorder? = null
    private var part: File? = null
    private var key: PassageKey? = null
    private var startedAt = 0L
    private var recordJob: Job? = null
    private var focus: Focus? = null

    private var mediaPlayer: MediaPlayer? = null
    private var playJob: Job? = null
    private var playFocus: Focus? = null

    init {
        // The reciter starts to sound: the person's own recording stops playing (the other way round is done in [play]).
        scope.launch {
            playback.state.map { it.isPlaying }.distinctUntilChanged().collect { reciterPlays ->
                if (reciterPlays && _player.value.playing) stopPlayback()
            }
        }
    }

    // -- Lists ------------------------------------------------------------------------------------------

    /** The recordings of [key] with their lengths, the newest first; read again whenever a file is added or removed. */
    fun notes(key: PassageKey): Flow<List<VoiceNote>> = revision
        .mapLatest {
            store.list(key).map { VoiceNote(it, durationOf(it.file)) }
        }
        .flowOn(Dispatchers.IO)

    /** How many recordings [key] has. */
    fun count(key: PassageKey): Flow<Int> = revision
        .map { store.count(key) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /** The size of all the recordings on the device, in bytes. */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) { store.totalBytes() }

    /** "Delete all recordings" (the storage screen). */
    suspend fun deleteAll() {
        stop()
        stopPlayback()
        withContext(Dispatchers.IO) { store.deleteAll() }
        revision.value++
    }

    /** Removes one recording for good, at once (the sheet that held it is closing). */
    fun deleteNow(recording: Recording) {
        if (_player.value.file == recording.file) stopPlayback()
        store.delete(recording)
        revision.value++
    }

    /** Removes one recording for good. */
    suspend fun delete(recording: Recording) {
        if (_player.value.file == recording.file) stopPlayback()
        withContext(Dispatchers.IO) { store.delete(recording) }
        revision.value++
    }

    private fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    // -- Recording --------------------------------------------------------------------------------------

    /**
     * Starts recording the passage [key] (the microphone permission must be granted). The sound of the reciter and the playing of
     * a recording stop first. [StartResult.Failed] when the recorder could not start (no microphone, in use by a call).
     */
    fun start(key: PassageKey): StartResult {
        if (_recorder.value is RecorderState.Recording) return StartResult.AlreadyRecording
        stopPlayback()
        playback.stop()
        val file = store.newPart(key)
        val recorder = newRecorder()
        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(SAMPLE_RATE)
            recorder.setAudioEncodingBitRate(BIT_RATE)
            recorder.setMaxDuration(MAX_MILLIS.toInt())
            recorder.setOutputFile(file.absolutePath)
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) scope.launch { stop() }
            }
            recorder.setOnErrorListener { _, _, _ -> scope.launch { stop() } }
            val held = requestFocus { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) scope.launch { stop() }
            } ?: throw IllegalStateException("audio focus was refused")
            focus = held
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            part = file
            this.key = key
            startedAt = SystemClock.elapsedRealtime()
            _recorder.value = RecorderState.Recording(key, 0L, 0f)
            recordJob = scope.launch { watch(recorder) }
            StartResult.Started
        } catch (e: Exception) {
            AppTelemetry.logError(e, context = "voice_record_start_failed")
            runCatching { recorder.release() }
            abandon(focus)
            focus = null
            store.discard(file)
            StartResult.Failed
        }
    }

    /** Polls the loudness and the time while recording; the limit stops the recording here too. */
    private suspend fun watch(recorder: MediaRecorder) {
        while (true) {
            delay(POLL_MILLIS)
            val current = _recorder.value as? RecorderState.Recording ?: return
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val peak = try {
                recorder.maxAmplitude
            } catch (_: Exception) {
                0
            }
            _recorder.value = current.copy(elapsedMs = elapsed, level = sqrt((peak / MAX_AMPLITUDE).coerceIn(0f, 1f)))
            if (elapsed >= MAX_MILLIS) {
                stop()
                return
            }
        }
    }

    /** Stops the recording and keeps what was made (null when nothing worth keeping, or when none runs). */
    fun stop(): Recording? {
        val recorder = mediaRecorder ?: return null
        val file = part
        val key = key
        recordJob?.cancel()
        recordJob = null
        mediaRecorder = null
        part = null
        this.key = null
        var stopped = true
        try {
            recorder.stop()
        } catch (_: RuntimeException) {
            // Stopped before any sound reached the file: there is nothing to keep.
            stopped = false
        } finally {
            runCatching { recorder.release() }
        }
        abandon(focus)
        focus = null
        _recorder.value = RecorderState.Idle
        val saved = if (stopped && file != null && key != null) {
            store.finish(file, key)
        } else {
            file?.let(store::discard)
            null
        }
        revision.value++
        return saved
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    // -- Playing ----------------------------------------------------------------------------------------

    /** Plays [file], or pauses / resumes it when it is the recording that is loaded. The reciter is silenced first. */
    fun toggle(file: File) {
        val state = _player.value
        val loaded = mediaPlayer
        if (loaded != null && state.file == file) {
            if (state.playing) {
                loaded.pause()
                _player.value = state.copy(playing = false)
            } else {
                loaded.start()
                _player.value = state.copy(playing = true)
                playJob = scope.launch { follow(loaded) }
            }
            return
        }
        play(file)
    }

    private fun play(file: File) {
        stopPlayback()
        if (_recorder.value is RecorderState.Recording) stop()
        playback.stop()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            playFocus = requestFocus { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) scope.launch { stopPlayback() }
            }
            player.setOnCompletionListener {
                scope.launch {
                    // The recording ended: it stays loaded at its start, so ⏯ plays it again.
                    playJob?.cancel()
                    runCatching { it.seekTo(0) }
                    abandon(playFocus)
                    playFocus = null
                    _player.value = _player.value.copy(playing = false, positionMs = 0)
                }
            }
            player.setOnErrorListener { _, _, _ ->
                scope.launch { stopPlayback() }
                true
            }
            player.start()
            mediaPlayer = player
            _player.value = PlayerState(file = file, playing = true, positionMs = 0, durationMs = player.duration.coerceAtLeast(0))
            playJob = scope.launch { follow(player) }
        } catch (e: Exception) {
            AppTelemetry.logError(e, context = "voice_play_failed")
            runCatching { player.release() }
            abandon(playFocus)
            playFocus = null
            _player.value = PlayerState()
        }
    }

    private suspend fun follow(player: MediaPlayer) {
        while (true) {
            delay(FOLLOW_MILLIS)
            if (mediaPlayer !== player) return
            val position = try {
                player.currentPosition
            } catch (_: Exception) {
                return
            }
            if (_player.value.playing) _player.value = _player.value.copy(positionMs = position)
        }
    }

    /** Stops the playing for good and lets go of the file. */
    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        abandon(playFocus)
        playFocus = null
        _player.value = PlayerState()
    }

    /** The app leaves the screen (or the sheet closes): what is recorded is kept, what is playing stops. */
    fun release() {
        stop()
        stopPlayback()
    }

    // -- Audio focus ------------------------------------------------------------------------------------

    private class Focus(val request: AudioFocusRequest?, val listener: AudioManager.OnAudioFocusChangeListener)

    private fun requestFocus(onChange: (Int) -> Unit): Focus? {
        val listener = AudioManager.OnAudioFocusChangeListener { change -> onChange(change) }
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Focus(request, listener) else null
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Focus(null, listener) else null
        }
        return granted
    }

    private fun abandon(focus: Focus?) {
        focus ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focus.request != null) {
            audioManager.abandonAudioFocusRequest(focus.request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focus.listener)
        }
    }

    companion object {
        /** A recording ends by itself after 10 minutes. */
        const val MAX_MILLIS = 10 * 60 * 1000L

        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100

        private const val POLL_MILLIS = 80L
        private const val FOLLOW_MILLIS = 200L
        private const val MAX_AMPLITUDE = 32_767f
    }
}
