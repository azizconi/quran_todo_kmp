package tj.app.quran_todo.ui.di

import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tj.app.quran_todo.ui.hifz.HifzViewModel
import tj.app.quran_todo.ui.home.HomeUiViewModel
import tj.app.quran_todo.ui.lesson.LessonViewModel
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.onboarding.OnboardingViewModel
import tj.app.quran_todo.ui.plan.PlanViewModel
import tj.app.quran_todo.ui.playback.createPlaybackController
import tj.app.quran_todo.ui.progress.ProgressViewModel
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.settings.TransferIo
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.whatsnew.WhatsNewViewModel

/**
 * One Koin module per feature (docs/redesign/ANDROID_REDESIGN.md, 4.2). Phase 1/2 tasks register the
 * Android ViewModels of their feature in the module they own; the modules are wired in
 * `QuranTodoApplication.startKoin` through [uiModules].
 */
val homeUiModule = module {
    // The hifz repository and the day ticker are Koin singles of the common module: injected, never via koinViewModel.
    viewModel {
        HomeUiViewModel(
            home = get(),
            uiPrefs = get(),
            playback = get(),
            hifz = get(),
            ticker = get(),
        )
    }
}

val planUiModule = module {
    // "My plan": a draft of the plan over the shared HifzRepository; HomeViewModel only supplies the surah names.
    viewModel { PlanViewModel(hifz = get(), ticker = get(), home = get()) }
}

val readerUiModule = module {
    // One ReaderViewModel per Dest.Reader back stack entry; the shared view models come in through Koin.
    viewModel { params ->
        ReaderViewModel(
            route = params.get<Dest.Reader>(),
            homeViewModel = get(),
            surahViewModel = get(),
            ayahNoteDao = get(),
            uiPrefs = get(),
            playback = get(),
            hifz = get(),
            ticker = get(),
        )
    }
}

val hifzUiModule = module {
    // One HifzViewModel ("Check yourself") per Dest.Reader back stack entry, next to its ReaderViewModel; the
    // HifzRepository and the day ticker are Koin singletons that come in through the constructor.
    viewModel { params ->
        HifzViewModel(
            route = params.get<Dest.Reader>(),
            hifz = get(),
            ticker = get(),
            uiPrefs = get(),
            playback = get(),
        )
    }
}

val lessonUiModule = module {
    // The lesson of the day. The shared Home/Surah view models, the HifzRepository and the ticker are singletons that
    // come in through the constructor (never koinViewModel for them).
    viewModel {
        LessonViewModel(
            hifz = get(),
            ticker = get(),
            home = get(),
            surahViewModel = get(),
            uiPrefs = get(),
            playback = get(),
        )
    }
}

val progressUiModule = module {
    // The progress is read from the HifzRepository (a Koin single of the common module), the day comes from the EpochDayTicker; the
    // shared HomeViewModel only supplies the names of the surahs. None of them is ever put into a ViewModelStore.
    viewModel { ProgressViewModel(home = get(), hifz = get(), ticker = get()) }
}

val settingsUiModule = module {
    // The shared HomeViewModel and HifzRepository come in through Koin; the files of "Transfer and backup" through TransferIo.
    viewModel { SettingsViewModel(home = get(), uiPrefs = get(), hifz = get(), io = TransferIo(androidContext())) }
}

val onboardingUiModule = module {
    // The onboarding v2 keeps its step and its choices in the SavedStateHandle (a killed process continues from the same step). The
    // handle is the parameter (see OnboardingViewModelFactory); the shared HomeViewModel, HifzRepository and the day ticker are
    // singletons that come in through the constructor.
    viewModel { params ->
        OnboardingViewModel(
            handle = params.get(),
            home = get(),
            hifz = get(),
            ticker = get(),
            uiPrefs = get(),
            reminder = ReminderController(androidContext(), get()),
        )
    }
}

val whatsNewUiModule = module {
    // "What's new: the lesson of the day" for people who updated: only reads whether a plan exists.
    viewModel { WhatsNewViewModel(hifz = get(), uiPrefs = get()) }
}

/** App-wide singletons shared by all screens. */
val appUiModule = module {
    single { UiPrefs(androidContext()) }
    single { createPlaybackController(androidContext(), get<UiPrefs>()) }
}

val uiModules = listOf(
    appUiModule,
    homeUiModule,
    planUiModule,
    readerUiModule,
    hifzUiModule,
    lessonUiModule,
    progressUiModule,
    settingsUiModule,
    onboardingUiModule,
    whatsNewUiModule,
)
