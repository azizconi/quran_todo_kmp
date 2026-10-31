package tj.app.quran_todo.ui.khatm

import org.koin.android.ext.koin.androidContext
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * The reading plan and Ramadan (task F5b). The repository, the day ticker, the shared home view model, the playback controller and the
 * preferences are Koin singles that come in through the constructor, never through `koinViewModel`.
 */
val khatmUiModule = module {
    viewModel {
        KhatmViewModel(
            context = androidContext(),
            hifz = get(),
            ticker = get(),
            uiPrefs = get(),
            playback = get(),
            home = get(),
        )
    }
}
