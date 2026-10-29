package tj.app.quran_todo.ui.hifz

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.ui.playback.PlaybackController

/** What the recall audio of one surah is doing right now. [ayah] is the number in the surah being heard. */
@Immutable
data class RecallAudioState(
    val ayah: Int? = null,
    /** Playing, or being prepared. */
    val playing: Boolean = false,
    /** Paused in the middle of the sound. */
    val paused: Boolean = false,
    val hasError: Boolean = false,
) {
    val active: Boolean get() = ayah != null && (playing || paused)

    companion object {
        val Idle = RecallAudioState()
    }
}

/**
 * The listening of the recall screens (the lesson and "Check yourself" in the reader): one ayah, or a sequence of
 * ayahs, played through the shared [PlaybackController], so the mini player and the notification behave as for any
 * listening. The person's own player settings are never changed: the sound is played as an explicit sequence
 * ([PlaybackController.playSequence]), and what the shared session was configured with before is put back as soon as
 * the sound is over, the screen is left or this object is cleared.
 *
 * The state is only about sound this object started; audio of the reader that was playing already is not reported.
 */
class RecallAudio(
    private val playback: PlaybackController,
    private val surahNumber: Int,
    scope: CoroutineScope,
) {
    private class Original(val prefs: SurahReaderPreferences, val practice: ReaderPracticeSettings)

    private val ours = MutableStateFlow<Original?>(null)
    private var seenPlaying = false

    /** The sound in progress is one ayah (a "Listen" button), not a sequence. */
    private var singleAyah = false

    val state: StateFlow<RecallAudioState> = combine(ours, playback.nowPlaying, playback.state) { original, now, audio ->
        if (original == null || now?.surahNumber != surahNumber) {
            RecallAudioState.Idle
        } else {
            RecallAudioState(
                ayah = now.ayahs.getOrNull(audio.index)?.numberInSurah,
                playing = audio.isPlaying || audio.isPreparing,
                paused = audio.canResume && !audio.isPlaying,
                hasError = audio.hasError,
            )
        }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, RecallAudioState.Idle)

    init {
        scope.launch {
            // The sound is over (or failed): the player settings that were there come back.
            playback.state.collect { audio ->
                if (ours.value == null) return@collect
                if (audio.isPlaying) {
                    seenPlaying = true
                } else if (!audio.isPreparing && !audio.canResume && (seenPlaying || audio.hasError)) {
                    restore()
                }
            }
        }
    }

    /** Plays the ayah, pauses it while it plays, resumes it when it is paused. [inSurah] is the number in the surah. */
    fun toggleAyah(surah: SurahWithAyahs, inSurah: Int) {
        val sound = state.value
        if (sound.ayah == inSurah && sound.active && singleAyah) {
            playback.toggle()
            return
        }
        begin(surah, listOf(inSurah))
    }

    /**
     * Plays the ayahs [inSurah] (numbers in the surah, repeats allowed) in this order; while it is on, calling this
     * again pauses and resumes it instead.
     */
    fun toggleSequence(surah: SurahWithAyahs, inSurah: List<Int>) {
        if (state.value.active) {
            playback.toggle()
            return
        }
        begin(surah, inSurah)
    }

    /** Starts the sequence over, whatever the audio is doing. */
    fun restartSequence(surah: SurahWithAyahs, inSurah: List<Int>) = begin(surah, inSurah)

    private fun begin(surah: SurahWithAyahs, inSurah: List<Int>) {
        val ayahs = surah.ayahs.sortedBy { it.numberInSurah }
        val indices = inSurah.mapNotNull { number -> ayahs.indexOfFirst { it.numberInSurah == number }.takeIf { it >= 0 } }
        if (indices.isEmpty()) return
        val saved = ours.value ?: Original(
            SurahReaderPreferences.load(),
            ReaderPracticeSettings.load(surahNumber, ayahs.size),
        )
        playback.playSequence(surah, indices, saved.prefs)
        // Set only now: while the sequence starts the audio state passes through "idle" and would look like the end.
        ours.value = saved
        seenPlaying = false
        singleAyah = indices.size == 1
    }

    /** Pauses what is playing (the person leaves the screen but stays in the app) and gives the settings back. */
    fun endListening() {
        if (ours.value == null) return
        val audio = playback.state.value
        if (playback.isActiveFor(surahNumber) && (audio.isPlaying || audio.isPreparing)) playback.toggle()
        restore()
    }

    /** Stops the sound for good and hides the mini player: the screen that owned it is gone. */
    fun stopAll() {
        if (ours.value == null) return
        ours.value = null
        seenPlaying = false
        if (playback.isActiveFor(surahNumber)) playback.stop()
    }

    private fun restore() {
        val saved = ours.value ?: return
        ours.value = null
        seenPlaying = false
        if (playback.isActiveFor(surahNumber)) playback.updateSettings(saved.prefs, saved.practice)
    }
}
