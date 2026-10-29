package tj.app.quran_todo.ui.di

import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tj.app.quran_todo.ui.hifz.HifzViewModel
import tj.app.quran_todo.ui.home.HomeUiViewModel
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.onboarding.OnboardingViewModel
import tj.app.quran_todo.ui.plan.PlanViewModel
import tj.app.quran_todo.ui.playback.createPlaybackController
import tj.app.quran_todo.ui.progress.ProgressViewModel
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.settings.TransferIo
import tj.app.quran_todo.ui.state.UiPrefs

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
            ayahTodoDao = get(),
            ayahNoteDao = get(),
            ayahReviewDao = get(),
            uiPrefs = get(),
            playback = get(),
        )
    }
}

val hifzUiModule = module {
    // One HifzViewModel per Dest.Reader back stack entry, next to its ReaderViewModel; the shared
    // SurahViewModel comes in through Koin like in the reader.
    viewModel { params ->
        HifzViewModel(
            route = params.get<Dest.Reader>(),
            surahViewModel = get(),
            homeViewModel = get(),
            ayahTodoDao = get(),
            uiPrefs = get(),
            playback = get(),
        )
    }
}

val progressUiModule = module {
    // The shared HomeViewModel comes in through Koin and is never put into a ViewModelStore.
    viewModel { ProgressViewModel(home = get(), reviewDao = get()) }
}

val settingsUiModule = module {
    // The shared HomeViewModel and HifzRepository come in through Koin; the files of "Transfer and backup" through TransferIo.
    viewModel { SettingsViewModel(home = get(), uiPrefs = get(), hifz = get(), io = TransferIo(androidContext())) }
}

val onboardingUiModule = module {
    viewModel { OnboardingViewModel(home = get(), uiPrefs = get()) }
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
    progressUiModule,
    settingsUiModule,
    onboardingUiModule,
)
