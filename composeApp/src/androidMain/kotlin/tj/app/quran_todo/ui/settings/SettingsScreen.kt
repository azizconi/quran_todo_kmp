package tj.app.quran_todo.ui.settings

import androidx.compose.runtime.Composable
import tj.app.quran_todo.presentation.settings.NativeSettingsScreen
import tj.app.quran_todo.ui.di.ProvideProcessViewModelStore

/** Placeholder of the settings screen: phase 1C replaces the body with the redesigned screen. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // TODO(1C): the redesigned screen injects the shared ViewModels through Koin instead.
    ProvideProcessViewModelStore {
        NativeSettingsScreen(onBack = onBack)
    }
}
