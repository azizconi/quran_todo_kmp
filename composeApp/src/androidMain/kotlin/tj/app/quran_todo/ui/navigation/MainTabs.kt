package tj.app.quran_todo.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.home.HomeUiViewModel
import tj.app.quran_todo.ui.home.LessonTab
import tj.app.quran_todo.ui.home.QuranTab
import tj.app.quran_todo.ui.home.components.MiniPlayerHost
import tj.app.quran_todo.ui.home.components.listBottomPadding
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.i18n.tabsCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.progress.ProgressScreen
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * How much of the bottom of the screen the tab bar takes (the bar with the navigation-bar inset), 0 when it is not on screen. The
 * scaffold writes it; things that float over the bottom of every screen (the update banner) stand above it.
 */
@Stable
class BottomChrome {
    var height: Dp by mutableStateOf(0.dp)
        internal set
}

val LocalBottomChrome = compositionLocalOf { BottomChrome() }

/** Everything the three tabs can ask the app to open. */
class MainTabActions(
    val openReader: (Dest.Reader) -> Unit,
    val openSettings: () -> Unit,
    val openLesson: () -> Unit,
    val openPlan: () -> Unit,
    val openQuiz: () -> Unit,
    val openKhatm: () -> Unit,
    val openKhatmPicker: () -> Unit,
    val openMushaf: (page: Int) -> Unit,
    val openDrill: () -> Unit,
    val openDetails: () -> Unit,
    val openNotes: () -> Unit,
    /** Non-null in debug builds only. */
    val openCatalog: (() -> Unit)?,
)

/**
 * The body of [Dest.Home] (UX task U14): the three tabs and their bar. The chosen tab lives above, in the app navigation
 * ([selected], [onSelect]), so a deep entry can change it and a rotation or a process death keeps it. Only the chosen tab is
 * composed; the others keep their scroll position and their fields through a [androidx.compose.runtime.saveable.SaveableStateHolder].
 *
 * The bar is hidden while the keyboard is up. System back on a tab other than [default] goes to [default]; on [default] it is not
 * taken here, so the app is left. The floating mini player sits above the bar on all three tabs.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun MainTabs(
    selected: MainTab,
    default: MainTab,
    onSelect: (MainTab) -> Unit,
    actions: MainTabActions,
) {
    // Declared before the tabs, so a back handler inside a tab (the search of the Quran tab) is asked first.
    val backTarget = MainTab.backTarget(selected, default)
    BackHandler(enabled = backTarget != null) { backTarget?.let(onSelect) }

    val language = LocalAppLanguage.current
    val copy = remember(language) { tabsCopy(language) }
    val homeCopy = remember(language) { homeCopy(language) }
    val colors = QhTheme.colors
    val density = LocalDensity.current
    val reduced = LocalReducedMotion.current
    val holder = rememberSaveableStateHolder()

    val viewModel = koinViewModel<HomeUiViewModel>()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val playbackProgress = viewModel.playbackProgress.collectAsState()

    val barVisible = WindowInsets.ime.getBottom(density) == 0
    val chrome = LocalBottomChrome.current
    DisposableEffect(chrome) { onDispose { chrome.height = 0.dp } }
    LaunchedEffect(barVisible) { if (!barVisible) chrome.height = 0.dp }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider(selected.name) {
                when (selected) {
                    MainTab.Lesson -> LessonTab(
                        onOpenSettings = actions.openSettings,
                        onOpenLesson = actions.openLesson,
                        onOpenPlan = actions.openPlan,
                        onOpenQuiz = actions.openQuiz,
                        onOpenKhatm = actions.openKhatm,
                        onOpenKhatmPicker = actions.openKhatmPicker,
                        onOpenMushaf = actions.openMushaf,
                    )

                    MainTab.Quran -> QuranTab(
                        onOpenReader = actions.openReader,
                        onOpenSettings = actions.openSettings,
                        onOpenCatalog = actions.openCatalog,
                    )

                    MainTab.Progress -> ProgressScreen(
                        onBack = null,
                        onOpenReader = actions.openReader,
                        onOpenPlan = actions.openPlan,
                        onOpenDrill = actions.openDrill,
                        onOpenDetails = actions.openDetails,
                        onOpenNotes = actions.openNotes,
                        tabBottomSpace = listBottomPadding(0.dp, playerVisible = player != null, fontScale = density.fontScale),
                    )
                }
            }

            MiniPlayerHost(
                player = player,
                progress = playbackProgress,
                copy = homeCopy,
                onOpen = { current ->
                    actions.openReader(Dest.Reader(surah = current.surah, ayah = current.ayah, origin = Origin.PLAYER))
                },
                onToggle = viewModel::togglePlayback,
                onStop = viewModel::stopPlayback,
                // The bar below owns the navigation-bar inset; with the keyboard up the player rides above it.
                bottomInset = 0.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding(),
            )
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = if (reduced) EnterTransition.None else expandVertically(QhMotion.snappy()) + fadeIn(QhMotion.snappy()),
            exit = if (reduced) ExitTransition.None else shrinkVertically(QhMotion.snappy()) + fadeOut(QhMotion.snappy()),
        ) {
            MainTabBar(
                selected = selected,
                onSelect = onSelect,
                copy = copy,
                modifier = Modifier.onSizeChanged { size -> chrome.height = with(density) { size.height.toDp() } },
            )
        }
    }
}
