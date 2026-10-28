package tj.app.quran_todo.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.home.components.HeroCard
import tj.app.quran_todo.ui.home.components.HeroListenState
import tj.app.quran_todo.ui.home.components.HomeSkeletonRow
import tj.app.quran_todo.ui.home.components.HomeSkeletonTop
import tj.app.quran_todo.ui.home.components.ListEmptyState
import tj.app.quran_todo.ui.home.components.MiniPlayerHost
import tj.app.quran_todo.ui.home.components.MiniPlayerMargin
import tj.app.quran_todo.ui.home.components.ProgressAction
import tj.app.quran_todo.ui.home.components.progressPercent
import tj.app.quran_todo.ui.home.components.RecentSection
import tj.app.quran_todo.ui.home.components.RowTexts
import tj.app.quran_todo.ui.home.components.SearchHeader
import tj.app.quran_todo.ui.home.components.SettingsAction
import tj.app.quran_todo.ui.home.components.SmartResultCard
import tj.app.quran_todo.ui.home.components.SurahListRow
import tj.app.quran_todo.ui.home.components.SurahSheet
import tj.app.quran_todo.ui.home.components.TodaySection
import tj.app.quran_todo.ui.home.components.listBottomPadding
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * The home screen (docs/redesign/ANDROID_REDESIGN.md, 6.3): a large "Quran" title with the progress
 * ring and the settings gear, the "Continue" hero, Today, Recent, a sticky search field with the
 * status segments, the 114 surahs, and the floating mini player.
 *
 * [onOpenCatalog] is non-null in debug builds only: a long press on the title area opens the
 * component catalog.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null,
) {
    val viewModel = koinViewModel<HomeUiViewModel>()
    val language = LocalAppLanguage.current
    val settings = LocalAppSettings.current
    val copy = remember(language) { homeCopy(language) }
    val toaster = LocalToaster.current

    LaunchedEffect(language, settings.dailyGoal, settings.focusMinutes) {
        viewModel.setInputs(language, settings.dailyGoal, settings.focusMinutes)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LaunchedEffect(viewModel, copy) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.StatusUpdateFailed -> toaster.show(copy.statusUpdateFailed, ToastKind.Error)
            }
        }
    }

    // Frozen while another screen is on top or a transition is running, so the cards do not reorder
    // under the shared element animation; fresh as soon as the screen is resumed again.
    val ui by viewModel.ui.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val player by viewModel.player.collectAsStateWithLifecycle()
    // Not read here: only the mini player ring reads it.
    val playbackProgress = viewModel.playbackProgress.collectAsState()

    val actions = remember(viewModel, onOpenReader, onOpenSettings, onOpenProgress) {
        HomeActions(
            openReader = onOpenReader,
            openSettings = onOpenSettings,
            openProgress = onOpenProgress,
            setQuery = viewModel::setQuery,
            setFilter = viewModel::setFilter,
            retry = viewModel::retry,
            setStatus = viewModel::setSurahStatus,
            togglePin = viewModel::togglePin,
            listen = viewModel::listen,
            togglePlayback = viewModel::togglePlayback,
            stopPlayback = viewModel::stopPlayback,
            dismissInvite = viewModel::dismissHifzInvite,
            consumeGoalCelebration = viewModel::consumeGoalCelebration,
        )
    }
    HomeContent(
        ui = ui,
        player = player,
        playbackProgress = playbackProgress,
        copy = copy,
        actions = actions,
        onOpenCatalog = onOpenCatalog,
    )
}

/** Everything the screen can ask for, in one stable object so the list items do not rebuild lambdas. */
@Immutable
internal class HomeActions(
    val openReader: (Dest.Reader) -> Unit,
    val openSettings: () -> Unit,
    val openProgress: () -> Unit,
    val setQuery: (String) -> Unit,
    val setFilter: (SurahFilter) -> Unit,
    val retry: () -> Unit,
    val setStatus: (surah: Int, status: SurahTodoStatus?) -> Unit,
    val togglePin: (surah: Int) -> Unit,
    val listen: (surah: Int, readAyah: Int) -> Unit,
    val togglePlayback: () -> Unit,
    val stopPlayback: () -> Unit,
    val dismissInvite: () -> Unit,
    val consumeGoalCelebration: (done: Int, goal: Int) -> Boolean,
)

/** Which row just became memorized; [token] changes every time so the burst fires again. */
private data class Celebration(val surah: Int = 0, val token: Int = 0)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    ui: HomeUi,
    player: MiniPlayerUi?,
    playbackProgress: State<Float>,
    copy: HomeCopy,
    actions: HomeActions,
    onOpenCatalog: (() -> Unit)?,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHaptics.current
    val toaster = LocalToaster.current

    // -- Screen state -----------------------------------------------------------------------------
    var query by rememberSaveable { mutableStateOf("") }
    var filterOrdinal by rememberSaveable { mutableStateOf(0) }
    var sheetSurah by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf(Celebration()) }
    val filter = SurahFilter.entries.getOrElse(filterOrdinal) { SurahFilter.All }
    // The search text lives here (an immediate echo for the field); the ViewModel filters off the UI thread.
    LaunchedEffect(Unit) {
        actions.setQuery(query)
        actions.setFilter(filter)
    }
    // While the person searches, the hero / today / recent fold away and the field rises to the top.
    val searchMode = searchFocused || query.isNotBlank()

    val listState = rememberLazyListState()
    val stagger = rememberStaggerState(ready = ui.phase == HomePhase.Content)
    val focusRequester = remember { FocusRequester() }

    // The font loads asynchronously, so the family must be re-read on every composition (it is the
    // same instance once loaded, which keeps these styles equal and the rows skippable).
    val quranFont = getQuranFontFamily()
    val rowArabicStyle = type.quranInline(quranFont)
    val heroArabicStyle = type.quranHeader(quranFont, 30.sp)
    val rowTexts = remember(copy) { RowTexts.of(copy) }

    // -- Geometry of the top bar ------------------------------------------------------------------
    // The bar overlays the list. The list starts under the collapsed bar (status bar + 56dp), so the
    // sticky search header pins right below it; the first list item reserves the room of the big
    // title, which the bar collapses over the first scroll distance.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedBar = statusTop + TopBarRowHeight
    val estimatedTitleBlock = with(density) { type.display.lineHeight.toDp() } + QhSpacing.x2
    var expandedBarPx by remember { mutableIntStateOf(0) }
    val measuredTitleBlock = with(density) { expandedBarPx.toDp() } - collapsedBar
    val titleBlock = maxOf(estimatedTitleBlock, measuredTitleBlock)
    val topBarState = rememberQhTopBarState(listState, collapseDistance = titleBlock)

    // -- Bottom: navigation bar and the floating mini player ----------------------------------------
    val imeInsets = WindowInsets.ime
    val navigationInset = WindowInsets.navigationBars.exclude(imeInsets).asPaddingValues().calculateBottomPadding()
    val bottomPadding by animateDpAsState(
        targetValue = listBottomPadding(navigationInset, playerVisible = player != null, fontScale = density.fontScale),
        animationSpec = QhMotion.gentle<Dp>().orSnap(),
        label = "list-bottom-padding",
    )

    // Scrolling the list puts the keyboard away (the search text and the results stay).
    val dismissKeyboardOnScroll = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }

    fun open(dest: Dest.Reader) {
        focusManager.clearFocus()
        actions.openReader(dest)
    }

    val hero = ui.continueItem
    val heroListen = if (player != null && hero != null && player.surah == hero.surah && player.isPlaying) {
        HeroListenState.Playing
    } else {
        HeroListenState.Idle
    }
    // Index of the sticky header: the title space and whatever sections exist above it.
    val searchHeaderIndex = 1 + (if (hero != null) 1 else 0) + (if (ui.today != null) 1 else 0) +
        (if (ui.recent.isNotEmpty()) 1 else 0)
    val searchStuck = remember(listState, searchHeaderIndex) { { listState.firstVisibleItemIndex >= searchHeaderIndex } }

    // Focusing the field lifts it under the top bar. Only when the sections above it are still on
    // screen: from the middle of the list the field is already pinned and the list must not jump.
    LaunchedEffect(searchFocused) {
        if (searchFocused && listState.firstVisibleItemIndex < searchHeaderIndex) {
            listState.animateScrollToItem(searchHeaderIndex)
        }
    }
    // Once the keyboard is gone the field is not "in use" any more: let go of its focus, so the hero,
    // Today and the status segments come back when the field is empty.
    LaunchedEffect(imeInsets, density) {
        var wasVisible = false
        snapshotFlow { imeInsets.getBottom(density) > 0 }.collect { visible ->
            if (wasVisible && !visible) focusManager.clearFocus()
            wasVisible = visible
        }
    }
    // Back leaves search mode first (clears the field and the focus); only then does it leave the app.
    BackHandler(enabled = searchMode) {
        query = ""
        actions.setQuery("")
        keyboard?.hide()
        focusManager.clearFocus()
    }
    // When search ends and the list is still near the top, bring the hero back into view.
    val wasSearching = remember { booleanArrayOf(false) }
    LaunchedEffect(searchMode) {
        if (!searchMode && wasSearching[0] && listState.firstVisibleItemIndex <= searchHeaderIndex) {
            listState.animateScrollToItem(0)
        }
        wasSearching[0] = searchMode
    }
    // The burst is over after a second; forget it so a row scrolled back into view does not replay it.
    LaunchedEffect(celebration) {
        if (celebration.token != 0) {
            delay(CelebrationMillis)
            celebration = Celebration()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = collapsedBar)
                .nestedScroll(dismissKeyboardOnScroll),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item(key = "title-space", contentType = "space") { Spacer(Modifier.height(titleBlock)) }

            when (ui.phase) {
                HomePhase.Loading -> {
                    item(key = "skeleton-top", contentType = "skeleton") { HomeSkeletonTop() }
                    items(SkeletonRowCount, key = { "skeleton-row-$it" }) { HomeSkeletonRow() }
                }

                HomePhase.Error -> item(key = "error", contentType = "error") {
                    EmptyState(
                        title = copy.loadErrorTitle,
                        message = copy.loadErrorBody,
                        actionLabel = copy.retry,
                        onAction = actions.retry,
                        modifier = Modifier.fillParentMaxHeight(0.7f),
                    )
                }

                HomePhase.Content -> {
                    if (hero != null) {
                        item(key = "hero", contentType = "hero") {
                            FoldableSection(visible = !searchMode) {
                                HeroCard(
                                    item = hero,
                                    copy = copy,
                                    arabicStyle = heroArabicStyle,
                                    listenState = heroListen,
                                    onRead = {
                                        open(
                                            Dest.Reader(
                                                surah = hero.surah,
                                                ayah = if (hero.isFresh) null else hero.ayah,
                                                origin = Origin.HERO,
                                            ),
                                        )
                                    },
                                    onListen = { actions.listen(hero.surah, hero.ayah) },
                                    modifier = Modifier
                                        .padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x3)
                                        .staggeredEnter(0, stagger),
                                )
                            }
                        }
                    }
                    ui.today?.let { today ->
                        item(key = "today", contentType = "today") {
                            FoldableSection(visible = !searchMode) {
                                TodaySection(
                                    today = today,
                                    copy = copy,
                                    onOpenCard = { card ->
                                        open(
                                            Dest.Reader(
                                                surah = card.surah,
                                                ayah = card.startAyah,
                                                origin = Origin.TODAY,
                                                hifz = true,
                                                rangeStart = card.startAyah,
                                                rangeEnd = card.endAyah,
                                            ),
                                        )
                                    },
                                    onDismissInvite = actions.dismissInvite,
                                    consumeGoalCelebration = actions.consumeGoalCelebration,
                                    modifier = Modifier.staggeredEnter(1, stagger),
                                )
                            }
                        }
                    }
                    if (ui.recent.isNotEmpty()) {
                        item(key = "recent", contentType = "recent") {
                            FoldableSection(visible = !searchMode) {
                                RecentSection(
                                    recent = ui.recent,
                                    copy = copy,
                                    onOpen = { open(Dest.Reader(surah = it, origin = Origin.RECENT)) },
                                    onLongPress = { sheetSurah = it },
                                    modifier = Modifier.staggeredEnter(2, stagger),
                                )
                            }
                        }
                    }

                    stickyHeader(key = "search", contentType = "search") {
                        SearchHeader(
                            query = query,
                            onQueryChange = {
                                query = it
                                actions.setQuery(it)
                            },
                            filter = filter,
                            onFilterChange = {
                                filterOrdinal = it.ordinal
                                actions.setFilter(it)
                            },
                            showFilters = !searchMode,
                            copy = copy,
                            focusRequester = focusRequester,
                            onFocusChange = { searchFocused = it },
                            stuck = searchStuck,
                            modifier = Modifier.staggeredEnter(3, stagger),
                        )
                    }

                    val listing = ui.listing
                    listing.smartResult?.let { smart ->
                        item(key = "smart", contentType = "smart") {
                            SmartResultCard(
                                result = smart,
                                copy = copy,
                                onOpen = {
                                    open(Dest.Reader(surah = smart.row.number, ayah = smart.ayah, origin = Origin.SEARCH))
                                },
                                modifier = Modifier
                                    .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x1)
                                    .then(rowAnimation()),
                            )
                        }
                    }
                    if (listing.rows.isEmpty() && listing.smartResult == null) {
                        item(key = "empty", contentType = "empty") {
                            ListEmptyState(
                                isSearching = listing.isSearching,
                                filter = listing.filter,
                                copy = copy,
                                modifier = rowAnimation(),
                            )
                        }
                    }
                    val rowOrigin = if (listing.isSearching) Origin.SEARCH else Origin.LIST
                    itemsIndexed(listing.rows, key = { _, row -> row.number }, contentType = { _, _ -> "row" }) { position, row ->
                        SurahListRow(
                            row = row,
                            origin = rowOrigin,
                            texts = rowTexts,
                            arabicStyle = rowArabicStyle,
                            celebration = if (celebration.surah == row.number) celebration.token else 0,
                            onOpen = { open(Dest.Reader(surah = it, origin = rowOrigin)) },
                            onLongPress = { sheetSurah = it },
                            modifier = Modifier
                                // The burst reaches into the neighbouring rows: draw this one on top of them.
                                .zIndex(if (celebration.surah == row.number && celebration.token != 0) 1f else 0f)
                                .then(rowAnimation())
                                .staggeredEnter(FirstRowStaggerIndex + position, stagger),
                        )
                    }
                }
            }
        }

        QhLargeTopBar(
            title = copy.title,
            overline = copy.greeting,
            state = topBarState,
            actions = {
                ProgressAction(
                    progress = ui.overallProgress,
                    description = copy.progressDescription(progressPercent(ui.overallProgress)),
                    onClick = actions.openProgress,
                )
                SettingsAction(label = copy.settings, onClick = actions.openSettings)
            },
            modifier = Modifier.onSizeChanged { expandedBarPx = maxOf(expandedBarPx, it.height) },
        )

        if (onOpenCatalog != null) {
            // Debug builds: a long press on the big title opens the component catalog.
            Box(
                Modifier
                    .padding(top = collapsedBar)
                    .size(width = 160.dp, height = titleBlock)
                    .combinedClickable(onClick = {}, onLongClick = onOpenCatalog),
            )
        }

        MiniPlayerHost(
            player = player,
            progress = playbackProgress,
            copy = copy,
            onOpen = { current ->
                open(Dest.Reader(surah = current.surah, ayah = current.ayah, origin = Origin.PLAYER))
            },
            onToggle = actions.togglePlayback,
            onStop = actions.stopPlayback,
            bottomInset = navigationInset,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val sheetRow = sheetSurah?.let { number -> ui.rows.firstOrNull { it.number == number } }
    if (sheetRow != null) {
        SurahSheet(
            row = sheetRow,
            copy = copy,
            arabicStyle = rowArabicStyle,
            onPicked = { status ->
                // At the tap, while the sheet slides away: the toast stays up for its full 3 seconds and
                // the haptic does not depend on the row being on screen.
                if (status == SurahTodoStatus.LEARNED) {
                    haptics.perform(HapticKind.Success)
                    toaster.show(copy.learnedToast(sheetRow.name), ToastKind.Success)
                }
            },
            onSetStatus = { status ->
                // Once the sheet is gone: the badge fills and the burst plays on the visible row.
                actions.setStatus(sheetRow.number, status)
                if (status == SurahTodoStatus.LEARNED) {
                    celebration = Celebration(sheetRow.number, celebration.token + 1)
                }
            },
            onTogglePin = { actions.togglePin(sheetRow.number) },
            onListen = {
                actions.listen(sheetRow.number, if (hero?.surah == sheetRow.number) hero.ayah else 1)
            },
            onDismiss = { sheetSurah = null },
        )
    }
}

/**
 * Wraps a section above the search field. It folds to nothing (and back) when the person starts
 * searching, which lifts the search field to the top of the screen without any manual scrolling.
 */
@Composable
private fun FoldableSection(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) {
            EnterTransition.None
        } else {
            expandVertically(QhMotion.gentle()) + fadeIn(QhMotion.snappy())
        },
        exit = if (reduced) {
            ExitTransition.None
        } else {
            shrinkVertically(QhMotion.gentle()) + fadeOut(tween(QhMotion.Short))
        },
    ) {
        Column {
            content()
            Spacer(Modifier.height(QhSpacing.section))
        }
    }
}

/** Fade and re-placement of list items when the filter or the query changes; none under reduced motion. */
@Composable
private fun LazyItemScope.rowAnimation(): Modifier =
    if (LocalReducedMotion.current) {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        Modifier.animateItem()
    }

private val TopBarRowHeight = 56.dp
private const val SkeletonRowCount = 9
private const val FirstRowStaggerIndex = 4
private const val CelebrationMillis = 1_100L
