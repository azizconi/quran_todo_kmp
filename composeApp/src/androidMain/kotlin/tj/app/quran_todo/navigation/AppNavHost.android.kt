package tj.app.quran_todo.navigation

import androidx.compose.runtime.Composable
import tj.app.quran_todo.AppTab
import tj.app.quran_todo.presentation.home.HomeScreen
import tj.app.quran_todo.presentation.settings.SettingsScreen
import tj.app.quran_todo.presentation.stats.StatsScreen

@Composable
actual fun AppNavHost(current: AppTab) {
    when (current) {
        AppTab.Home -> HomeScreen()
        AppTab.Stats -> StatsScreen()
        AppTab.Settings -> SettingsScreen()
    }
}
