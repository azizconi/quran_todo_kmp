package tj.app.quran_todo.ui.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import tj.app.quran_todo.ui.recording.PassageKey
import tj.app.quran_todo.ui.recording.PlayerState
import tj.app.quran_todo.ui.recording.RecorderState
import tj.app.quran_todo.ui.recording.Recording
import tj.app.quran_todo.ui.recording.StartResult
import tj.app.quran_todo.ui.recording.VoiceNote
import tj.app.quran_todo.ui.recording.VoiceService

/** TODO(IOS-S2): AVAudioRecorder / AVAudioPlayer (see [VoiceService]). Until then nothing is recorded and the list is empty. */
object IosVoiceService : VoiceService {
    override val recorder: StateFlow<RecorderState> = MutableStateFlow(RecorderState.Idle)
    override val player: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
    override fun notes(key: PassageKey): Flow<List<VoiceNote>> = flowOf(emptyList())
    override fun count(key: PassageKey): Flow<Int> = flowOf(0)
    override fun deleteNow(recording: Recording) = Unit
    override suspend fun delete(recording: Recording) = Unit
    override fun start(key: PassageKey): StartResult = StartResult.Failed
    override fun stop(): Recording? = null
    override fun toggle(path: String) = Unit
    override fun stopPlayback() = Unit
    override fun release() = Unit
    override suspend fun totalBytes(): Long = 0L
    override suspend fun deleteAll() = Unit
}
