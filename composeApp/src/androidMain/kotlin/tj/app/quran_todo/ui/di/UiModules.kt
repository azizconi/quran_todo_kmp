package tj.app.quran_todo.ui.di

import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tj.app.quran_todo.ui.downloads.DownloadsManager
import tj.app.quran_todo.ui.downloads.DownloadsViewModel
import tj.app.quran_todo.ui.translation.TranslationSearchViewModel
import tj.app.quran_todo.ui.drill.DrillViewModel
import tj.app.quran_todo.ui.hifz.HifzViewModel
import tj.app.quran_todo.ui.home.HomeUiViewModel
import tj.app.quran_todo.ui.lesson.LessonViewModel
import tj.app.quran_todo.ui.khatm.khatmUiModule
import tj.app.quran_todo.ui.listen.ListenOldViewModel
import tj.app.quran_todo.ui.mushaf.MushafViewModel
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.onboarding.OnboardingViewModel
import tj.app.quran_todo.ui.plan.PlanViewModel
import tj.app.quran_todo.ui.playback.createPlaybackController
import tj.app.quran_todo.ui.progress.ProgressViewModel
import tj.app.quran_todo.ui.quiz.QuizViewModel
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.recording.RecordingStore
import tj.app.quran_todo.ui.recording.VoiceController
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.settings.TransferIo
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.tafsir.TafsirViewModel
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
    // One TafsirViewModel per Dest.Tafsir back stack entry (the commentary of an ayah, task F4b); the shared view models and the
    // reference repository come in through the constructor.
    viewModel { params ->
        TafsirViewModel(
            route = params.get<Dest.Tafsir>(),
            homeViewModel = get(),
            surahViewModel = get(),
            reference = get(),
            uiPrefs = get(),
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

val mushafUiModule = module {
    // One MushafViewModel per Dest.Mushaf back stack entry (the pages as a mushaf, task F3a); the shared Home and Surah view models, the
    // HifzRepository and the ticker are singletons that come in through the constructor, never through koinViewModel.
    viewModel { params ->
        MushafViewModel(
            route = params.get<Dest.Mushaf>(),
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

val listenUiModule = module {
    // "Listen to the old": the queue lives in the shared PlaybackController; this view model only reads the lesson and writes the
    // "listened" records through the HifzRepository (singletons come in through the constructor, never koinViewModel).
    viewModel {
        ListenOldViewModel(
            hifz = get(),
            ticker = get(),
            home = get(),
            surahViewModel = get(),
            uiPrefs = get(),
            playback = get(),
        )
    }
}

val quizUiModule = module {
    // The quiz "Check": the covered ayahs come from the HifzRepository, the text and the names from the shared view models (singletons,
    // injected through the constructor, never koinViewModel). It writes only the difficult marks (markHard), never the review log.
    viewModel { QuizViewModel(hifz = get(), home = get(), surahViewModel = get()) }
}

val progressUiModule = module {
    // The progress is read from the HifzRepository (a Koin single of the common module), the day comes from the EpochDayTicker; the
    // shared HomeViewModel only supplies the names of the surahs. None of them is ever put into a ViewModelStore.
    viewModel { ProgressViewModel(home = get(), hifz = get(), ticker = get()) }
}

val settingsUiModule = module {
    // The HifzRepository comes in through Koin; the files of "Transfer and backup" through TransferIo.
    viewModel { SettingsViewModel(uiPrefs = get(), hifz = get(), io = TransferIo(androidContext())) }
}

val downloadsUiModule = module {
    // The queue, the files and the work scheduler of "Downloads and storage" are one single, shared with the worker (which finds it
    // through Koin when WorkManager starts it). The shared Home view model (names of surahs), the HifzRepository (the plan), the
    // text of the Quran and its metrics come in through the constructor, never through koinViewModel.
    single { DownloadsManager.create(androidContext()) }
    viewModel {
        DownloadsViewModel(
            manager = get(),
            hifz = get(),
            home = get(),
            uiPrefs = get(),
            quran = get(),
            hifzDao = get(),
            voice = get(),
            references = get(),
        )
    }
    // The search "In translation" of the home screen (F4a): over the stored edition; its download is a request of the same queue.
    viewModel { TranslationSearchViewModel(repository = get(), downloads = get()) }
}

val drillUiModule = module {
    // The drill of the difficult ayahs: the shared Home/Surah view models, the HifzRepository and the ticker come in through the constructor.
    viewModel {
        DrillViewModel(
            hifz = get(),
            ticker = get(),
            home = get(),
            surahViewModel = get(),
            uiPrefs = get(),
            playback = get(),
        )
    }
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
    // The microphone of "My reading" (task F2a): the recordings never leave the phone.
    single { VoiceController(androidContext(), RecordingStore.shared, get()) }
}

val uiModules = listOf(
    appUiModule,
    homeUiModule,
    planUiModule,
    readerUiModule,
    mushafUiModule,
    hifzUiModule,
    lessonUiModule,
    listenUiModule,
    khatmUiModule,
    quizUiModule,
    progressUiModule,
    settingsUiModule,
    downloadsUiModule,
    drillUiModule,
    onboardingUiModule,
    whatsNewUiModule,
)
