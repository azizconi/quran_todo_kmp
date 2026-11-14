package tj.app.quran_todo.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.dsl.module
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.common.audio.monotonicClock
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.downloads.ListeningCache
import tj.app.quran_todo.ui.haptics.Haptics
import tj.app.quran_todo.ui.playback.AudioCacheFiles
import tj.app.quran_todo.ui.playback.AvPlayerEngine
import tj.app.quran_todo.ui.playback.AyahAudioSource
import tj.app.quran_todo.ui.playback.IosHaptics
import tj.app.quran_todo.ui.playback.IosNowPlayingBridge
import tj.app.quran_todo.ui.playback.IosPlaybackServiceLink
import tj.app.quran_todo.ui.playback.IosReaderAudioOutput
import tj.app.quran_todo.ui.playback.IosWordTimingsStore
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.playback.ReciterPreview
import tj.app.quran_todo.ui.playback.currentAppLanguage
import tj.app.quran_todo.ui.progress.SurahNameSource
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.words.WordTimingsLoader

/**
 * The playback services of iOS (IOS-S2): the AVPlayer output with the audio session, Now Playing and the remote commands, the word
 * timings, the listening cache and the haptics. Included by `iosUiPlatformModule`.
 */
val iosPlaybackModule = module {
    single {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val prefs = get<UiPrefs>()
        lateinit var controller: PlaybackController

        val engine = AvPlayerEngine(scope)
        var names: List<String> = emptyList()
        val bridge = IosNowPlayingBridge(
            controllerProvider = { controller },
            scope = scope,
            surahTitle = { number -> names.getOrNull(number - 1) ?: "№ $number" },
            reciterName = { prefs.reciter.value.name(currentAppLanguage()) },
        )
        val output = IosReaderAudioOutput(
            engine = engine,
            scope = scope,
            pausedBySystem = {
                bridge.pausedByInterruption = true
                controller.pauseBySystem()
            },
            onReleaseFocus = engine::deactivateSession,
        )
        engine.onInterruptionEnded = bridge::interruptionEnded

        val source = AyahAudioSource(
            files = AudioCacheFiles(AudioCache()),
            reciter = { prefs.reciter.value },
            playlist = { controller.nowPlaying.value?.ayahs.orEmpty() },
            scope = scope,
        )
        val session = ReaderAudioSession(
            scope,
            output,
            { ayah, surah -> source.resolve(ayah, surah) },
            monotonicClock(),
            { surah, ayah -> UserSettingsStorage.saveLastPlaybackPosition(surah, ayah) },
        )
        controller = PlaybackController(
            session = session,
            timerScope = scope,
            reciter = prefs.reciter,
            serviceLink = IosPlaybackServiceLink(bridge),
            extras = output,
        )

        // The names of the surahs on the lock screen: the one naming rule of the app, in the language of the app.
        val home = getOrNull<HomeViewModel>()
        if (home != null) {
            scope.launch {
                SurahNameSource.names(home, controller.nowPlaying.map { currentAppLanguage() }.distinctUntilChanged())
                    .collect { names = it }
            }
        }
        controller
    }
    single<WordTimingsLoader> { IosWordTimingsStore.shared }
    single<ListeningCache> { AudioCacheStorage.shared }
    single<Haptics> { IosHaptics(get<UiPrefs>().hapticsEnabled) }
    single {
        val controller = get<PlaybackController>()
        ReciterPreview(
            play = controller::playSample,
            stopPlaying = controller::stopSample,
            files = AudioCacheFiles(AudioCache()),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
}
