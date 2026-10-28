package tj.app.quran_todo.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.presentation.hifz.QuranLibraryRoute
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.di.ProvideProcessViewModelStore
import tj.app.quran_todo.ui.navigation.Dest

/**
 * Placeholder of the home screen: phase 1A replaces the body. Until then it hosts the old library
 * screen so the app keeps working end to end.
 *
 * [onOpenCatalog] is non-null in debug builds only; the placeholder opens it with a long press on the
 * title area of the old library (the real screen will use a long press on its large title).
 */
@Composable
fun HomeScreen(
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null,
) {
    // TODO(1A): the redesigned screen injects the shared ViewModels through Koin instead.
    ProvideProcessViewModelStore {
        LegacyHome(onOpenReader, onOpenSettings, onOpenCatalog)
    }
}

@OptIn(KoinExperimentalAPI::class, ExperimentalFoundationApi::class)
@Composable
private fun LegacyHome(
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
) {
    val homeViewModel = koinViewModel<HomeViewModel>()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        homeViewModel.refreshDueReviewsNow()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        QuranLibraryRoute(
            viewModel = homeViewModel,
            onOpenSurah = { surah -> onOpenReader(Dest.Reader(surah = surah)) },
            onOpenSettings = onOpenSettings,
        )
        if (onOpenCatalog != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 16.dp)
                    .size(width = 140.dp, height = 56.dp)
                    .combinedClickable(onClick = {}, onLongClick = onOpenCatalog),
            )
        }
    }
}
