package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.Reciter

/** What the sample button of a reciter shows (the iOS twin of the Android type of the same name, without Compose). */
sealed interface PreviewState {
    data object Idle : PreviewState

    /** The sample of [reciterId] is being fetched. */
    data class Loading(val reciterId: String) : PreviewState

    /** The sample of [reciterId] is playing. */
    data class Playing(val reciterId: String) : PreviewState
}

/**
 * Plays the sample of a reciter (Surah Al-Ikhlas, ayah 1) for the reciter sheet, on the app's one player: [play] is
 * `PlaybackController.playSample`, so audio that is playing in the reader stops where it is and stays stopped afterwards.
 *
 * A Koin single on iOS (see `IosPlaybackModule`); call [stop] when the sheet goes away.
 */
class ReciterPreview(
    private val play: (source: String, onFinished: (failed: Boolean) -> Unit) -> Unit,
    private val stopPlaying: () -> Unit,
    private val files: AyahFiles,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a sample could not be fetched or played. */
    val failures: SharedFlow<Unit> = _failures.asSharedFlow()

    private var job: Job? = null
    private var request = 0

    /** Starts the sample of [reciter]; stops it when that sample is already loading or playing. */
    fun toggle(reciter: Reciter) {
        val current = _state.value
        val currentId = when (current) {
            is PreviewState.Loading -> current.reciterId
            is PreviewState.Playing -> current.reciterId
            PreviewState.Idle -> null
        }
        stop()
        if (currentId == reciter.id) return
        val mine = ++request
        _state.value = PreviewState.Loading(reciter.id)
        job = scope.launch {
            val url = reciter.audioUrl(SampleSurah, SampleAyahInSurah)
            val local = files.getOrFetch(url, reciter.cacheKey(SampleSurah, SampleAyahInSurah))
            if (!isActive || mine != request) return@launch
            _state.value = PreviewState.Playing(reciter.id)
            // Not downloadable (no space, no connection): let the player try to stream it.
            play(local ?: url) { failed ->
                if (mine != request) return@play
                _state.value = PreviewState.Idle
                if (failed) _failures.tryEmit(Unit)
            }
        }
    }

    fun stop() {
        request++
        job?.cancel()
        job = null
        if (_state.value != PreviewState.Idle) stopPlaying()
        _state.value = PreviewState.Idle
    }

    companion object {
        /** Surah 112, ayah 1: "Say: He is Allah, One". */
        const val SampleSurah = 112
        const val SampleAyahInSurah = 1
    }
}
