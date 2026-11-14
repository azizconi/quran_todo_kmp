package tj.app.quran_todo.ui.playback

import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.presentation.home.HomeViewModel

/**
 * What the debug screen of the playback (`Features/Debug/PlaybackDebugView.swift`) calls: plays a surah from the data of the app with
 * the given repeat and speed, and says in one line what the player is doing. Not used by the real screens.
 */
object IosPlaybackDebug {
    private val controller: PlaybackController get() = KoinPlatform.getKoin().get()

    /** Plays [surah] from its first ayah; [repeatCount] times each ayah, at [speed]. False when the surah text is not loaded yet. */
    fun play(surah: Int, repeatCount: Int, speed: Float, echo: Boolean): Boolean {
        val home = KoinPlatform.getKoin().get<HomeViewModel>()
        val data = home.uiState.value.completeQuran.firstOrNull { it.surah.number == surah } ?: return false
        val prefs = SurahReaderPreferences(autoAdvanceAyahs = true, playbackSpeed = speed, repeatCount = repeatCount)
        controller.start(data, prefs, ReaderPracticeSettings(echo = echo), fromIndex = 0, autoplay = true)
        return true
    }

    fun toggle() = controller.toggle()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun stop() = controller.stop()

    fun setSpeed(speed: Float, repeatCount: Int) {
        controller.updateSettings(
            SurahReaderPreferences(autoAdvanceAyahs = true, playbackSpeed = speed, repeatCount = repeatCount),
            ReaderPracticeSettings(),
        )
    }

    /** "Surah 1 · ayah 3 of 7 · playing · 1240/3100 ms". */
    fun status(): String {
        val now = controller.nowPlaying.value ?: return "idle"
        val state = controller.state.value
        val ayah = now.ayahs.getOrNull(state.index)?.numberInSurah ?: 0
        val mode = when {
            state.hasError -> "error"
            state.isPreparing -> "preparing"
            state.pauseRemainingSeconds > 0 -> "silence ${state.pauseRemainingSeconds}s"
            state.isPlaying -> "playing"
            else -> "paused"
        }
        return "surah ${now.surahNumber} · ayah $ayah of ${now.ayahs.size} · $mode · ${controller.positionMs()}/${state.durationMs} ms"
    }
}
