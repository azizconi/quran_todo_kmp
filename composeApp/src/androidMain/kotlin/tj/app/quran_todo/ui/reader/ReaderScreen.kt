package tj.app.quran_todo.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.nativeReaderThemeColors
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahScreen
import tj.app.quran_todo.ui.di.ProvideProcessViewModelStore
import tj.app.quran_todo.ui.navigation.Dest

/**
 * Placeholder of the reader: phase 1B replaces the body. Until then it shows the old reader, which owns
 * its own audio session (sound stops when the screen is left).
 */
@Composable
fun ReaderScreen(
    route: Dest.Reader,
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    // TODO(1B): the redesigned reader injects the shared ViewModels through Koin instead.
    ProvideProcessViewModelStore {
        LegacyReader(route, onBack)
    }
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun LegacyReader(route: Dest.Reader, onBack: () -> Unit) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val state by homeViewModel.uiState.collectAsState()
    val surah = state.completeQuran.firstOrNull { it.surah.number == route.surah }
    val colors = nativeReaderThemeColors(LocalThemeMode.current, LocalThemePalette.current)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.readingBackground),
    ) {
        if (surah != null) {
            SurahScreen(surahWithAyahs = surah, onDismiss = onBack)
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = colors.primary,
            )
        }
    }
}
