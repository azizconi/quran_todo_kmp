package tj.app.quran_todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastLastOrNull
import androidx.navigation3.runtime.DecoratedNavEntryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.stringsFor
import tj.app.quran_todo.presentation.HomeScreen
import tj.app.quran_todo.presentation.stats.StatsScreen

@Composable
@Preview
fun App() {
    val initialLanguage = remember {
        LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
    }
    var language by remember { mutableStateOf<AppLanguage>(initialLanguage) }

    val strings = remember(language) { stringsFor(language) }
    val backStack = remember { mutableStateListOf<AppTab>(AppTab.Home) }
    val current = backStack.lastOrNull() ?: AppTab.Home

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLanguage provides language,
        LocalAppLanguageSetter provides {
            language = it
            LanguageStorage.saveLanguage(it)
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                BottomNavigation(
                    backgroundColor = MaterialTheme.colors.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    BottomNavigationItem(
                        selected = current == AppTab.Home,
                        onClick = {
                            switchTab(backStack, AppTab.Home)
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(strings.statsSurahs) }
                    )
                    BottomNavigationItem(
                        selected = current == AppTab.Stats,
                        onClick = {
                            switchTab(backStack, AppTab.Stats)
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text(strings.statsTitle) }
                    )
                }
            }
        ) { paddingValues ->
            val entryProvider = remember {
                entryProvider<AppTab> {
                    entry<AppTab.Home> { HomeScreen() }
                    entry<AppTab.Stats> { StatsScreen() }
                }
            }

            Box(modifier = Modifier.padding(paddingValues)) {
                DecoratedNavEntryProvider(
                    backStack = backStack,
                    entryProvider = entryProvider,
                ) { entries ->
                    entries.last().Content()
                }
            }
        }
    }
}

@Serializable
sealed interface AppTab : NavKey {
    @Serializable
    data object Home : AppTab

    @Serializable
    data object Stats : AppTab
}

private fun switchTab(backStack: MutableList<AppTab>, tab: AppTab) {
    if (backStack.lastOrNull() == tab) return
    backStack.clear()
    backStack.add(tab)
}
