package tj.app.quran_todo.presentation.hifz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.navigation.HifzRootComponent
import tj.app.quran_todo.presentation.surah.SurahScreen

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HifzAppShell(
    root: HifzRootComponent,
    onNativeSurahRequested: ((Int) -> Unit)? = null,
    onNativeSettingsRequested: (() -> Unit)? = null,
    onNativeBack: () -> Unit = {},
) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val stateHolder = rememberSaveableStateHolder()
    val stack by root.childStack.subscribeAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        homeViewModel.refreshDueReviewsNow()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Children(stack = root.childStack) { child ->
            stateHolder.SaveableStateProvider(child.configuration.saveableStateKey()) {
                when (val instance = child.instance) {
                    HifzRootComponent.Child.Quran -> QuranLibraryRoute(
                        viewModel = homeViewModel,
                        onOpenSurah = onNativeSurahRequested ?: root::openSurah,
                        onOpenSettings = onNativeSettingsRequested ?: root::openSettings,
                    )
                    HifzRootComponent.Child.Settings -> HifzSettingsRoute(homeViewModel = homeViewModel)
                    is HifzRootComponent.Child.Surah -> SurahDestinationRoute(
                        surahNumber = instance.number,
                        homeViewModel = homeViewModel,
                        onBack = {
                            if (stack.backStack.isNotEmpty()) root.goBack() else onNativeBack()
                        },
                    )
                }
            }
        }
    }
}

private fun HifzRootComponent.Config.saveableStateKey(): String = when (this) {
    HifzRootComponent.Config.Quran -> "quran"
    HifzRootComponent.Config.Settings -> "settings"
    is HifzRootComponent.Config.Surah -> "surah-$number"
}

@Composable
private fun SurahDestinationRoute(
    surahNumber: Int,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by homeViewModel.uiState.collectAsState()
    val surah = state.completeQuran.firstOrNull { it.surah.number == surahNumber }

    if (surah != null) {
        SurahScreen(
            surahWithAyahs = surah,
            onDismiss = onBack,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Загрузка суры…")
        }
    }
}
