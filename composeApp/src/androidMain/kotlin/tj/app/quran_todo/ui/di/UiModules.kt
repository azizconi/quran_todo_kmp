package tj.app.quran_todo.ui.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tj.app.quran_todo.ui.onboarding.OnboardingViewModel
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * One Koin module per feature (docs/redesign/ANDROID_REDESIGN.md, 4.2). Phase 1/2 tasks register the
 * Android ViewModels of their feature in the module they own; the modules are wired in
 * `QuranTodoApplication.startKoin` through [uiModules].
 */
val homeUiModule = module { }

val readerUiModule = module { }

val hifzUiModule = module { }

val progressUiModule = module { }

val settingsUiModule = module {
    viewModel { SettingsViewModel(home = get(), uiPrefs = get()) }
}

val onboardingUiModule = module {
    viewModel { OnboardingViewModel(home = get(), uiPrefs = get()) }
}

/** App-wide singletons shared by all screens. */
val appUiModule = module {
    single { UiPrefs(androidContext()) }
    single { PlaybackController() }
}

val uiModules = listOf(
    appUiModule,
    homeUiModule,
    readerUiModule,
    hifzUiModule,
    progressUiModule,
    settingsUiModule,
    onboardingUiModule,
)
