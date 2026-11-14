package tj.app.quran_todo.ui.recording

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.ios.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The microphone of the app on iOS ("My reading"), the counterpart of `VoiceController`: `AVAudioRecorder` (AAC `.m4a`, mono, 64 kbit/s,
 * 44.1 kHz, 10 minutes at most) into [RecordingStore] (Application Support, excluded from the iCloud backup), `AVAudioPlayer` for the
 * playback. One recording or one playback at a time; each silences the reciter first ([silenceReciter]) and the reciter that starts
 * stops the playback ([reciterPlaying]). A call or another app taking the audio session (`AVAudioSession` interruption) stops the
 * recording and keeps what was made. The microphone permission is the screen's business (Swift: `MicrophonePermission`).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalCoroutinesApi::class)
class IosVoiceService internal constructor(
    private val store: RecordingStore,
    private val silenceReciter: () -> Unit,
    reciterPlaying: Flow<Boolean>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : VoiceService {
    private val _recorder = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val recorder: StateFlow<RecorderState> = _recorder.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    override val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val revision = MutableStateFlow(0)

    private var audioRecorder: AVAudioRecorder? = null
    private var part: File? = null
    private var key: PassageKey? = null
    private var recordJob: Job? = null

    private var audioPlayer: AVAudioPlayer? = null
    private var playJob: Job? = null

    init {
        scope.launch {
            reciterPlaying.distinctUntilChanged().collect { plays -> if (plays && _player.value.playing) stopPlayback() }
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> onInterruption(notification) }
    }

    private fun onInterruption(notification: NSNotification?) {
        val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue ?: return
        if (type != AVAudioSessionInterruptionTypeBegan) return
        // A call: the recording stops and keeps what was made, the playback stops.
        if (_recorder.value is RecorderState.Recording) stop()
        if (_player.value.playing) stopPlayback()
    }

    // -- Lists ------------------------------------------------------------------------------------------

    override fun notes(key: PassageKey): Flow<List<VoiceNote>> = revision
        .mapLatest { store.list(key).map { VoiceNote(it, durationOf(it.path)) } }
        .flowOn(Dispatchers.Default)

    override fun count(key: PassageKey): Flow<Int> = revision.map { store.count(key) }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override suspend fun totalBytes(): Long = withContext(Dispatchers.Default) { store.totalBytes() }

    override suspend fun deleteAll() {
        stop()
        stopPlayback()
        withContext(Dispatchers.Default) { store.deleteAll() }
        revision.value++
    }

    override fun deleteNow(recording: Recording) {
        if (_player.value.path == recording.path) stopPlayback()
        store.delete(recording)
        revision.value++
    }

    override suspend fun delete(recording: Recording) {
        if (_player.value.path == recording.path) stopPlayback()
        withContext(Dispatchers.Default) { store.delete(recording) }
        revision.value++
    }

    private fun durationOf(path: String): Long = try {
        val player = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
        (player.duration * 1000).toLong()
    } catch (_: Throwable) {
        0L
    }

    // -- Recording --------------------------------------------------------------------------------------

    override fun start(key: PassageKey): StartResult {
        if (_recorder.value is RecorderState.Recording) return StartResult.AlreadyRecording
        stopPlayback()
        silenceReciter()
        val file = store.newPart(key)
        return try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth,
                error = null,
            )
            session.setActive(true, error = null)
            val settings = mapOf<Any?, Any?>(
                AVFormatIDKey to FORMAT_AAC,
                AVSampleRateKey to SAMPLE_RATE,
                AVNumberOfChannelsKey to 1,
                AVEncoderBitRateKey to BIT_RATE,
            )
            val recorder = AVAudioRecorder(uRL = NSURL.fileURLWithPath(file.path), settings = settings, error = null)
            recorder.meteringEnabled = true
            check(recorder.prepareToRecord()) { "prepare failed" }
            check(recorder.recordForDuration(MAX_MILLIS / 1000.0)) { "record failed" }
            audioRecorder = recorder
            part = file
            this.key = key
            _recorder.value = RecorderState.Recording(key, 0L, 0f)
            recordJob = scope.launch { watch(recorder) }
            StartResult.Started
        } catch (e: Throwable) {
            AppTelemetry.logError(Exception(e.message ?: "voice_record_start_failed"), context = "voice_record_start_failed")
            audioRecorder = null
            store.discard(file)
            restoreSession()
            StartResult.Failed
        }
    }

    private suspend fun watch(recorder: AVAudioRecorder) {
        while (true) {
            delay(POLL_MILLIS)
            val current = _recorder.value as? RecorderState.Recording ?: return
            recorder.updateMeters()
            val power = recorder.peakPowerForChannel(0u)
            val level = sqrt(10.0.pow(power / 20.0)).toFloat().coerceIn(0f, 1f)
            _recorder.value = current.copy(elapsedMs = (recorder.currentTime * 1000).toLong(), level = level)
            // The limit of the recorder ended the file by itself.
            if (!recorder.recording) {
                stop()
                return
            }
        }
    }

    override fun stop(): Recording? {
        val recorder = audioRecorder ?: return null
        val file = part
        val key = key
        recordJob?.cancel()
        recordJob = null
        audioRecorder = null
        part = null
        this.key = null
        recorder.stop()
        restoreSession()
        _recorder.value = RecorderState.Idle
        val saved = if (file != null && key != null) store.finish(file, key) else null
        if (saved == null) file?.let(store::discard)
        revision.value++
        return saved
    }

    /** The reader plays with the category `playback`: the recording leaves it as it found it. */
    private fun restoreSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
    }

    // -- Playing ----------------------------------------------------------------------------------------

    override fun toggle(path: String) {
        val state = _player.value
        val loaded = audioPlayer
        if (loaded != null && state.path == path) {
            if (state.playing) {
                loaded.pause()
                _player.value = state.copy(playing = false)
            } else {
                loaded.play()
                _player.value = state.copy(playing = true)
                playJob = scope.launch { follow(loaded) }
            }
            return
        }
        play(path)
    }

    private fun play(path: String) {
        stopPlayback()
        if (_recorder.value is RecorderState.Recording) stop()
        silenceReciter()
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
            val player = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
            check(player.prepareToPlay()) { "prepare failed" }
            check(player.play()) { "play failed" }
            audioPlayer = player
            _player.value = PlayerState(path = path, playing = true, positionMs = 0, durationMs = (player.duration * 1000).toInt())
            playJob = scope.launch { follow(player) }
        } catch (e: Throwable) {
            AppTelemetry.logError(Exception(e.message ?: "voice_play_failed"), context = "voice_play_failed")
            audioPlayer = null
            _player.value = PlayerState()
        }
    }

    private suspend fun follow(player: AVAudioPlayer) {
        while (true) {
            delay(FOLLOW_MILLIS)
            if (audioPlayer !== player) return
            if (!player.playing) {
                // Either paused by [toggle] (which then cancels this job) or the recording ended: it stays loaded at its start.
                if (_player.value.playing) {
                    player.currentTime = 0.0
                    _player.value = _player.value.copy(playing = false, positionMs = 0)
                }
                return
            }
            _player.value = _player.value.copy(positionMs = (player.currentTime * 1000).toInt())
        }
    }

    override fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        val player = audioPlayer
        audioPlayer = null
        player?.stop()
        _player.value = PlayerState()
    }

    override fun release() {
        stop()
        stopPlayback()
    }

    companion object {
        /** A recording ends by itself after 10 minutes. */
        const val MAX_MILLIS = 10 * 60 * 1000L
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100.0

        /** `kAudioFormatMPEG4AAC` ('aac '). */
        private const val FORMAT_AAC = 1633772320

        private const val POLL_MILLIS = 80L
        private const val FOLLOW_MILLIS = 200L
    }
}
