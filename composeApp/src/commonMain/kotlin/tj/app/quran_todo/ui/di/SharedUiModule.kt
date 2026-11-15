package tj.app.quran_todo.ui.di

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.dsl.module
import tj.app.quran_todo.ui.downloads.DownloadsViewModel
import tj.app.quran_todo.ui.drill.DrillViewModel
import tj.app.quran_todo.ui.hifz.HifzViewModel
import tj.app.quran_todo.ui.home.HomeUiViewModel
import tj.app.quran_todo.ui.khatm.KhatmViewModel
import tj.app.quran_todo.ui.lesson.LessonViewModel
import tj.app.quran_todo.ui.listen.ListenOldViewModel
import tj.app.quran_todo.ui.mushaf.MushafViewModel
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.notes.NotesViewModel
import tj.app.quran_todo.ui.onboarding.OnboardingViewModel
import tj.app.quran_todo.ui.plan.PlanViewModel
import tj.app.quran_todo.ui.progress.ProgressViewModel
import tj.app.quran_todo.ui.progress.analytics.AnalyticsViewModel
import tj.app.quran_todo.ui.quiz.QuizViewModel
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.similar.SimilarStore
import tj.app.quran_todo.ui.tafsir.TafsirViewModel
import tj.app.quran_todo.ui.translation.TranslationSearchViewModel
import tj.app.quran_todo.ui.whatsnew.WhatsNewViewModel

/**
 * Every view model of the UI layer as a Koin `factory`, with the same constructor arguments as `UiModules.kt` of the Android app. A
 * SwiftUI screen asks for its view model through `IosUi` and owns it until the screen goes away (no `koinViewModel`, no ViewModelStore
 * of an Android owner). The Android app keeps its own `viewModel { }` declarations in `UiModules.kt`.
 *
 * What a platform must bind before this module is used (the iOS ones are in `IosUiPlatformModule`):
 * `UiPrefs`, `PlaybackController`, `DownloadsService`, `TafsirChapters`, `MushafPageProvider`, `WordTimingsLoader`, `TransferFiles`,
 * `ReminderPlanner`, `StorageSpace`, `ListeningCache`, `HijriConverter`. The shared ones (`HomeViewModel`,
 * `SurahViewModel`, `HifzRepository`, `EpochDayTicker`, the DAOs) come from the common and iOS modules.
 */
val sharedUiModule = module {
    // The similar ayahs: one single, shared by the reader, the pages, the lesson and the quiz.
    single { SimilarStore(hifz = get(), home = get(), surahViewModel = get()) }
    single { ReminderController(planner = get(), prefs = get()) }

    factory { HomeUiViewModel(home = get(), uiPrefs = get(), playback = get(), hifz = get(), ticker = get()) }
    factory { TranslationSearchViewModel(repository = get(), downloads = get()) }
    factory { params ->
        TafsirViewModel(
            route = params.get<Dest.Tafsir>(),
            homeViewModel = get(),
            surahViewModel = get(),
            reference = get(),
            uiPrefs = get(),
            store = get(),
        )
    }
    factory { PlanViewModel(hifz = get(), ticker = get(), home = get()) }
    factory { params ->
        ReaderViewModel(
            route = params.get<Dest.Reader>(),
            homeViewModel = get(),
            surahViewModel = get(),
            ayahNoteDao = get(),
            uiPrefs = get(),
            playback = get(),
            hifz = get(),
            ticker = get(),
            wordTimings = get(),
        )
    }
    factory { params ->
        MushafViewModel(
            route = params.get<Dest.Mushaf>(),
            homeViewModel = get(),
            surahViewModel = get(),
            ayahNoteDao = get(),
            uiPrefs = get(),
            playback = get(),
            hifz = get(),
            ticker = get(),
            store = get(),
        )
    }
    factory { params ->
        HifzViewModel(route = params.get<Dest.Reader>(), hifz = get(), ticker = get(), uiPrefs = get(), playback = get())
    }
    factory {
        LessonViewModel(hifz = get(), ticker = get(), home = get(), surahViewModel = get(), uiPrefs = get(), playback = get())
    }
    factory {
        ListenOldViewModel(hifz = get(), ticker = get(), home = get(), surahViewModel = get(), uiPrefs = get(), playback = get())
    }
    factory { QuizViewModel(hifz = get(), home = get(), surahViewModel = get(), similar = get()) }
    factory {
        KhatmViewModel(
            debugDay = MutableStateFlow<Int?>(null),
            hijri = get(),
            hifz = get(),
            ticker = get(),
            uiPrefs = get(),
            playback = get(),
            home = get(),
        )
    }
    factory { ProgressViewModel(home = get(), hifz = get(), ticker = get()) }
    factory { AnalyticsViewModel(home = get(), hifz = get(), ticker = get()) }
    factory { NotesViewModel(home = get(), dao = get()) }
    factory {
        SettingsViewModel(uiPrefs = get(), hifz = get(), io = get(), reminder = get())
    }
    factory {
        DownloadsViewModel(
            manager = get(),
            hifz = get(),
            home = get(),
            uiPrefs = get(),
            quran = get(),
            hifzDao = get(),
            references = get(),
            cache = get(),
            space = get(),
        )
    }
    factory {
        DrillViewModel(hifz = get(), ticker = get(), home = get(), surahViewModel = get(), uiPrefs = get(), playback = get())
    }
    // The step and the choices of the onboarding live in a SavedStateHandle; SwiftUI keeps them in @SceneStorage if it wants to
    // survive a killed process, so a fresh handle is the default.
    factory { params ->
        OnboardingViewModel(
            handle = params.getOrNull<SavedStateHandle>() ?: SavedStateHandle(),
            home = get(),
            hifz = get(),
            ticker = get(),
            uiPrefs = get(),
        )
    }
    factory { WhatsNewViewModel(hifz = get(), uiPrefs = get()) }
}
