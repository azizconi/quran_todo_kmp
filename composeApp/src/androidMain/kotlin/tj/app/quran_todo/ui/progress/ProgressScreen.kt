package tj.app.quran_todo.ui.progress

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.motion.StaggerState
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.progress.components.ActivitySection
import tj.app.quran_todo.ui.progress.components.BigRing
import tj.app.quran_todo.ui.progress.components.QuranMapSection
import tj.app.quran_todo.ui.progress.components.RingHero
import tj.app.quran_todo.ui.progress.components.StatTiles
import tj.app.quran_todo.ui.progress.components.WeakSpotsSection
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The progress screen (docs/redesign/ANDROID_REDESIGN.md, 6.7): the big ring that the home screen's
 * small ring flies into, the Quran map, four tiles, two weeks of activity and the weak spots. With
 * nothing started yet it invites to the first surah instead.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    val viewModel = koinViewModel<ProgressViewModel>()
    val language = LocalAppLanguage.current
    val copy = remember(language) { progressCopy(language) }

    LaunchedEffect(viewModel, language) { viewModel.setLanguage(language) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ProgressContent(ui = ui, copy = copy, onBack = onBack, onOpenReader = onOpenReader)
}

@Composable
private fun ProgressContent(
    ui: ProgressUi,
    copy: ProgressCopy,
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val topBarState = rememberQhTopBarState(scrollState)
    val stagger = rememberStaggerState(ready = ui.phase != ProgressPhase.Loading)

    // The bar floats over the content: reserve its expanded height (the estimate is exact for a
    // one-line title, so nothing jumps when the first measurement arrives).
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) {
        (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx()
    }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val topPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        when (ui.phase) {
            ProgressPhase.Loading -> LoadingBody(scrollState, topPadding, navigationBottom)
            ProgressPhase.Empty -> EmptyBody(ui, copy, onBack, scrollState, topPadding, navigationBottom)
            ProgressPhase.Content -> Body(ui, copy, onOpenReader, scrollState, stagger, topPadding, navigationBottom)
        }
        QhLargeTopBar(
            title = copy.title,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size ->
                // Only an unscrolled bar is fully expanded.
                if (scrollState.value == 0) expandedBarPx = size.height
            },
            navigation = {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = copy.back,
                    onClick = onBack,
                    filled = false,
                )
            },
        )
    }
}

@Composable
private fun Body(
    ui: ProgressUi,
    copy: ProgressCopy,
    onOpenReader: (Dest.Reader) -> Unit,
    scrollState: ScrollState,
    stagger: StaggerState,
    topPadding: Dp,
    navigationBottom: Dp,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = navigationBottom + QhSpacing.x8)
            .padding(horizontal = QhSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
    ) {
        // The ring is what the home screen's ring flies to: it is there at once, the rest follows.
        RingHero(hero = ui.hero, copy = copy)
        QuranMapSection(
            cells = ui.cells,
            copy = copy,
            scrollState = scrollState,
            onOpen = { surah -> onOpenReader(Dest.Reader(surah = surah, origin = Origin.MAP)) },
            modifier = Modifier.staggeredEnter(1, stagger),
        )
        StatTiles(tiles = ui.tiles, copy = copy, modifier = Modifier.staggeredEnter(2, stagger))
        ActivitySection(activity = ui.activity, copy = copy, modifier = Modifier.staggeredEnter(3, stagger))
        WeakSpotsSection(
            spots = ui.weak,
            copy = copy,
            onReview = onOpenReader,
            modifier = Modifier.staggeredEnter(4, stagger),
        )
    }
}

/** Nothing started: the empty ring is the picture, with an invitation and a way back to the surahs. */
@Composable
private fun EmptyBody(
    ui: ProgressUi,
    copy: ProgressCopy,
    onBack: () -> Unit,
    scrollState: ScrollState,
    topPadding: Dp,
    navigationBottom: Dp,
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = navigationBottom + QhSpacing.x8),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = copy.emptyTitle,
            message = copy.emptyBody,
            actionLabel = copy.emptyAction,
            onAction = onBack,
            illustration = { BigRing(hero = ui.hero, copy = copy) },
        )
    }
}

@Composable
private fun LoadingBody(scrollState: ScrollState, topPadding: Dp, navigationBottom: Dp) {
    val shape = QhTheme.shapes.lg
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState, enabled = false)
            .padding(top = topPadding, bottom = navigationBottom + QhSpacing.x8)
            .padding(horizontal = QhSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
    ) {
        Skeleton(Modifier.fillMaxWidth().height(268.dp), shape)
        Skeleton(Modifier.fillMaxWidth().height(380.dp), shape)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            Skeleton(Modifier.weight(1f).height(124.dp), shape)
            Skeleton(Modifier.weight(1f).height(124.dp), shape)
        }
    }
}
