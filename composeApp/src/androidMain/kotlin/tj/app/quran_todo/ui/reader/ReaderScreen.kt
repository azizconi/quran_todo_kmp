package tj.app.quran_todo.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.hifz.HifzEyeButton
import tj.app.quran_todo.ui.hifz.HifzMode
import tj.app.quran_todo.ui.hifz.HifzOverlays
import tj.app.quran_todo.ui.hifz.HifzTopBar
import tj.app.quran_todo.ui.hifz.rememberHifzMode
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.i18n.readerCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.reader.components.AppearanceSheet
import tj.app.quran_todo.ui.reader.components.AyahRow
import tj.app.quran_todo.ui.reader.components.AyahRowModel
import tj.app.quran_todo.ui.reader.components.AyahSheet
import tj.app.quran_todo.ui.reader.components.AyahSheetModel
import tj.app.quran_todo.ui.reader.components.DockHeaderHeight
import tj.app.quran_todo.ui.reader.components.JumpToCurrentPill
import tj.app.quran_todo.ui.reader.components.KeepScreenOnEffect
import tj.app.quran_todo.ui.reader.components.NextSurahCard
import tj.app.quran_todo.ui.reader.components.PlayerDock
import tj.app.quran_todo.ui.reader.components.PlayerDockState
import tj.app.quran_todo.ui.reader.components.PlayingElsewhereChip
import tj.app.quran_todo.ui.reader.components.QuranFinishedCard
import tj.app.quran_todo.ui.reader.components.ReaderEnterState
import tj.app.quran_todo.ui.reader.components.ReaderHeader
import tj.app.quran_todo.ui.reader.components.ReaderHeaderData
import tj.app.quran_todo.ui.reader.components.ReaderTopBar
import tj.app.quran_todo.ui.reader.components.ReaderTopBarRowHeight
import tj.app.quran_todo.ui.reader.components.SurahSheet
import tj.app.quran_todo.ui.reader.components.SurahSheetPage
import tj.app.quran_todo.ui.reader.components.label
import tj.app.quran_todo.ui.reader.components.rangePositionOf
import tj.app.quran_todo.ui.reader.components.readerEnter
import tj.app.quran_todo.ui.reader.components.rememberPlayerDockState
import tj.app.quran_todo.ui.reader.components.rememberReaderEnter
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Items before the first ayah in the list: the header. */
private const val HeaderItems = 1

/** The playing ayah is kept at this fraction of the visible list height. */
private const val FollowFraction = 0.3f

/**
 * The reader: the surah as a list (header with the cartouche, Bismillah, ayahs, the next surah), a compact
 * top bar that takes over when the header scrolls away and the player dock. Audio belongs to the shared
 * [tj.app.quran_todo.ui.playback.PlaybackController], so leaving the screen never interrupts it.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ReaderScreen(
    route: Dest.Reader,
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    val viewModel = koinViewModel<ReaderViewModel>(parameters = { parametersOf(route) })
    val language = LocalAppLanguage.current
    val copy = remember(language) { readerCopy(language) }
    val uiPrefs = koinInject<UiPrefs>()
    val surah by viewModel.surah.collectAsState()

    LaunchedEffect(language, viewModel) { viewModel.onLanguage(language) }
    // Settings may have changed the player preferences, and another reader may have replaced the shared
    // translations while this one was behind it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.reloadPrefs()
        viewModel.ensureTranslations()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.flushReadingPosition() }
    }

    val keepScreenOnPref by uiPrefs.keepScreenOn.collectAsState()
    val anythingPlaying by viewModel.anythingPlaying.collectAsState()
    KeepScreenOnEffect(enabled = keepScreenOnPref || anythingPlaying)

    val loaded = surah
    if (loaded == null) {
        val failed by viewModel.loadFailed.collectAsState()
        ReaderLoading(copy = copy, failed = failed, onRetry = viewModel::retryLoading, onBack = onBack)
    } else {
        ReaderContent(
            viewModel = viewModel,
            surah = loaded,
            route = route,
            copy = copy,
            onBack = onBack,
            onOpenReader = onOpenReader,
        )
    }
}

/** What is open on top of the reader. */
private sealed interface ReaderSheet {
    data class Surah(val page: SurahSheetPage) : ReaderSheet
    data class Ayah(val index: Int) : ReaderSheet
    data object Appearance : ReaderSheet
}

@Composable
private fun ReaderLoading(copy: ReaderCopy, failed: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.readerBg)
            .statusBarsPadding(),
    ) {
        QhIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = copy.back,
            onClick = onBack,
            filled = false,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (failed) {
            EmptyState(
                title = copy.notLoaded,
                modifier = Modifier.padding(top = QhSpacing.x12),
                actionLabel = copy.retry,
                onAction = onRetry,
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x6),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
            ) {
                Skeleton(Modifier.fillMaxWidth().height(80.dp), QhTheme.shapes.lg)
                Skeleton(Modifier.fillMaxWidth(0.5f).height(20.dp))
                Skeleton(Modifier.fillMaxWidth().height(60.dp))
                Skeleton(Modifier.fillMaxWidth().height(60.dp))
                Skeleton(Modifier.fillMaxWidth(0.8f).height(60.dp))
            }
        }
    }
}

@Composable
private fun ReaderContent(
    viewModel: ReaderViewModel,
    surah: SurahWithAyahs,
    route: Dest.Reader,
    copy: ReaderCopy,
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    val colors = QhTheme.colors
    val density = LocalDensity.current
    val settings = LocalAppSettings.current
    val toaster = LocalToaster.current
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ayahs = surah.ayahs
    val total = ayahs.size
    val surahNumber = surah.surah.number

    val prefs by viewModel.prefs.collectAsState()
    val practice by viewModel.practice.collectAsState()
    val player by viewModel.player.collectAsState()
    val status by viewModel.surahStatus.collectAsState()
    val chapterNames by viewModel.chapterNames.collectAsState()
    val russianNames by viewModel.russianNames.collectAsState()
    val language = LocalAppLanguage.current
    val statuses by viewModel.ayahStatuses.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val weak by viewModel.weakAyahs.collectAsState()
    val translations by viewModel.translations.collectAsState()

    // -- Text ------------------------------------------------------------------------------------------
    val quranFamily = getQuranFontFamily()
    val fontSize = settings.readingFontSize
    val type = QhTheme.type
    val arabicStyle = remember(type, quranFamily, fontSize) {
        type.quranBody(quranFamily, fontSize.sp).copy(
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    val headerStyle = remember(type, quranFamily) { type.quranHeader(quranFamily, 34.sp) }
    val nextNameStyle = remember(type, quranFamily) { type.quranInline(quranFamily, 22.sp) }
    val translationStyle = type.body

    val names = chapterNames[surahNumber]
    val title = SurahNames.displayName(
        language = language,
        russianName = russianNames[surahNumber],
        transliteration = names?.transliteration,
        fallback = surah.surah.englishName,
    )
    val meaning = names?.translated ?: surah.surah.englishNameTranslation
    val arabicName = names?.arabic ?: surah.surah.name
    val headerData = remember(surahNumber, route.origin, arabicName, title, meaning, copy, total) {
        ReaderHeaderData(
            surahNumber = surahNumber,
            origin = route.origin,
            arabicName = arabicName,
            title = title,
            meaning = meaning,
            meta = "${copy.revelationPlace(surah.surah.revelationType)} · ${copy.ayahCount(total)}",
            showBismillah = hasBismillahLine(surahNumber),
        )
    }

    // -- List and scrolling ------------------------------------------------------------------------------
    val initialIndex = viewModel.initialAyahIndex
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (initialIndex > 0) initialIndex + HeaderItems else 0,
    )
    val enter = rememberReaderEnter()
    val dockState = rememberPlayerDockState()

    val collapsedStart = with(density) { 72.dp.toPx() }
    val collapsedEnd = with(density) { 132.dp.toPx() }
    val collapsed = remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                ((listState.firstVisibleItemScrollOffset - collapsedStart) / (collapsedEnd - collapsedStart)).coerceIn(0f, 1f)
            }
        }
    }
    // The index of the first ayah on screen; see ReaderViewModel.NotMeasured and HeaderOnTop for the other values.
    val firstVisibleAyah = remember(listState, total) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > 0 }
            when {
                first == null -> ReaderViewModel.NotMeasured
                first.index == 0 -> ReaderViewModel.HeaderOnTop
                first.index <= total -> first.index - 1
                else -> total - 1
            }
        }
    }
    val ayahNumber = remember(firstVisibleAyah) { derivedStateOf { firstVisibleAyah.value.coerceAtLeast(0) + 1 } }
    val readingProgress = remember(firstVisibleAyah, total) {
        derivedStateOf {
            val index = firstVisibleAyah.value
            if (index < 0) 0f else (index + 1f) / total
        }
    }
    LaunchedEffect(listState, viewModel) {
        snapshotFlow { firstVisibleAyah.value }
            .distinctUntilChanged()
            .collect { index -> viewModel.onVisibleAyah(index) }
    }
    LaunchedEffect(viewModel) { viewModel.onOpened() }

    // Following the playing ayah: paused as soon as the person drags the list themselves.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = false
        }
    }
    val playingHere = player.active && (player.isPlaying || player.isPreparing)
    val followTarget = if (following && playingHere) player.index else null
    LaunchedEffect(followTarget) {
        val target = followTarget ?: return@LaunchedEffect
        listState.bringAyahTo(target + HeaderItems, fraction = FollowFraction, animate = !reduced, onlyIfHidden = false)
    }
    // Previous/next while nothing plays moves the picked ayah: make sure it can be seen.
    LaunchedEffect(player.index, player.selected, player.active) {
        if (!player.active && player.selected) {
            listState.bringAyahTo(player.index + HeaderItems, fraction = FollowFraction, animate = !reduced, onlyIfHidden = true)
        }
    }
    val currentItem by rememberUpdatedState(player.index + HeaderItems)
    val trackingPlayback by rememberUpdatedState(playingHere)
    val followingNow by rememberUpdatedState(following)
    val showJumpPill by remember(listState) {
        derivedStateOf {
            !followingNow && trackingPlayback &&
                listState.layoutInfo.visibleItemsInfo.none { it.index == currentItem }
        }
    }
    val pillPointsUp by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > currentItem } }

    // The ayah a search result or "continue" pointed at is marked for a moment.
    var flashIndex by rememberSaveable { mutableStateOf(viewModel.initialFlashIndex) }
    LaunchedEffect(Unit) {
        if (flashIndex != null) {
            delay(1_500)
            flashIndex = null
        }
    }

    // -- Sheets, events ------------------------------------------------------------------------------------
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var celebration by remember { mutableIntStateOf(0) }
    val latestTitle by rememberUpdatedState(title)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ReaderEvent.SurahLearned -> {
                    celebration++
                    toaster.show(copy.learnedToast(latestTitle), ToastKind.Success)
                }
                ReaderEvent.StatusFailed -> {
                    haptics.perform(HapticKind.Reject)
                    toaster.show(copy.statusError, ToastKind.Error)
                }
            }
        }
    }
    LaunchedEffect(player.hasError) {
        if (player.hasError) haptics.perform(HapticKind.Reject)
    }

    // -- Layout ----------------------------------------------------------------------------------------------
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val dockVisibleHeight = maxOf(DockHeaderHeight + navInset, with(density) { dockState.collapsedHeightPx.toDp() })
    val learnedFraction = remember(statuses, total) {
        statuses.values.count { it == AyahTodoStatus.LEARNED }.toFloat() / total.coerceAtLeast(1)
    }

    fun scrollToAyah(index: Int) {
        scope.launch { listState.bringAyahTo(index + HeaderItems, fraction = FollowFraction, animate = !reduced, onlyIfHidden = false) }
    }

    // The memorize mode (ui/hifz) owns all of its logic; the reader hands it the list and gives it its hooks.
    val hifz = rememberHifzMode(
        route = route,
        surah = surah,
        surahName = title,
        listState = listState,
        headerItems = HeaderItems,
        bringToFocus = { ayahIndex ->
            listState.bringAyahTo(ayahIndex + HeaderItems, fraction = FollowFraction, animate = !reduced, onlyIfHidden = false)
        },
        startIndex = { player.highlightIndex(prefs.highlightPlayingAyah) ?: firstVisibleAyah.value.coerceAtLeast(0) },
        rangeEndIndex = { if (practice.rangeEnabled) practice.endAyah - 1 else null },
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.readerBg),
    ) {
        ReaderList(
            listState = listState,
            surah = surah,
            contentPadding = PaddingValues(
                top = topInset + ReaderTopBarRowHeight + 2.dp,
                bottom = dockVisibleHeight + QhSpacing.x8,
            ),
            headerData = headerData,
            status = status,
            statusLabel = status.label(copy),
            learnedFraction = learnedFraction,
            celebration = celebration,
            headerStyle = headerStyle,
            arabicStyle = arabicStyle,
            translationStyle = translationStyle,
            nextNameStyle = nextNameStyle,
            copy = copy,
            enter = enter,
            statuses = statuses,
            notes = notes.keys,
            weak = weak,
            translations = if (prefs.showTranslation) translations else emptyMap(),
            markedIndex = if (hifz.active) hifz.currentIndex else player.highlightIndex(prefs.highlightPlayingAyah),
            hifz = hifz,
            flashIndex = flashIndex,
            passage = if (practice.rangeEnabled) (practice.startAyah - 1)..(practice.endAyah - 1) else null,
            nextSurah = if (surahNumber < 114) viewModel.nextSurahDisplay(surahNumber + 1, language, chapterNames, russianNames) else null,
            onSelect = { index ->
                following = true
                viewModel.selectAyah(index)
            },
            onMore = { index -> sheet = ReaderSheet.Ayah(index) },
            onOpenStatus = { sheet = ReaderSheet.Surah(SurahSheetPage.Status) },
            onOpenNext = { onOpenReader(Dest.Reader(surah = surahNumber + 1, origin = Origin.NEXT)) },
            onStartOver = { onOpenReader(Dest.Reader(surah = 1, origin = Origin.NEXT)) },
        )

        ReaderTopBar(
            surahName = title,
            totalAyahs = total,
            collapsed = collapsed,
            ayahNumber = ayahNumber,
            progress = readingProgress,
            copy = copy,
            onBack = onBack,
            onTextSettings = { sheet = ReaderSheet.Appearance },
            onMore = { sheet = ReaderSheet.Surah(SurahSheetPage.Menu) },
            modifier = Modifier.align(Alignment.TopCenter),
            extraAction = { HifzEyeButton(hifz) },
        )
        HifzTopBar(hifz, Modifier.align(Alignment.TopCenter))

        // Chips above the dock; they fade as the dock is pulled up.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dockVisibleHeight + QhSpacing.x3)
                .graphicsLayer { alpha = 1f - (dockState.progress * 3f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            AnimatedVisibility(
                visible = player.elsewhereSurah != null,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
            ) {
                val elsewhere = player.elsewhereSurah
                if (elsewhere != null) {
                    val name = viewModel.surahDisplayName(elsewhere, language, chapterNames, russianNames)
                    PlayingElsewhereChip(
                        label = copy.playingElsewhere(name),
                        onClick = { viewModel.routeToPlayingSurah()?.let(onOpenReader) },
                    )
                }
            }
            AnimatedVisibility(
                visible = showJumpPill,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
            ) {
                JumpToCurrentPill(
                    pointsUp = pillPointsUp,
                    label = copy.jumpToCurrent,
                    onClick = {
                        following = true
                        scope.launch {
                            listState.bringAyahTo(currentItem, fraction = FollowFraction, animate = !reduced, onlyIfHidden = false)
                        }
                    },
                )
            }
        }

        DockScrim(dockState = dockState, onClick = { scope.launch { dockState.collapse() } })

        PlayerDock(
            state = dockState,
            viewModel = viewModel,
            ui = player,
            prefs = prefs,
            practice = practice,
            surah = surah,
            showTranslation = prefs.showTranslation,
            copy = copy,
            origin = route.origin,
            screenHeight = screenHeight,
            onPlay = {
                following = true
                viewModel.togglePlayback()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            expandable = !hifz.active,
        )

        when (val open = sheet) {
            null -> Unit
            is ReaderSheet.Surah -> SurahSheet(
                initialPage = open.page,
                surahName = title,
                status = status,
                totalAyahs = total,
                currentAyah = (firstVisibleAyah.value.coerceAtLeast(0)) + 1,
                copy = copy,
                onStatus = viewModel::setSurahStatus,
                onGoTo = { number ->
                    val index = (number - 1).coerceIn(0, total - 1)
                    flashIndex = index
                    scrollToAyah(index)
                    scope.launch {
                        delay(1_500)
                        if (flashIndex == index) flashIndex = null
                    }
                },
                onDismiss = { sheet = null },
            )
            is ReaderSheet.Ayah -> {
                val ayah = ayahs.getOrNull(open.index)
                if (ayah != null) {
                    val reviews by viewModel.reviews.collectAsState()
                    val arabic = splitBismillah(surahNumber, ayah.numberInSurah, ayah.text)
                    val translation = translations[ayah.number]
                    val sheetModel = AyahSheetModel(
                        ayah = ayah,
                        index = open.index,
                        surahName = title,
                        arabic = arabic,
                        translation = translation,
                        status = statuses[ayah.number],
                        weak = ayah.number in weak,
                        note = notes[ayah.number]?.note.orEmpty(),
                        review = reviews[ayah.number],
                    )
                    val shareText = ayahShareText(arabic, translation, copy.quranRef, surahNumber, ayah.numberInSurah, surahName = title)
                    AyahSheet(
                        model = sheetModel,
                        arabicStyle = remember(type, quranFamily) { type.quranBody(quranFamily, 24.sp) },
                        copy = copy,
                        onListenFromHere = {
                            following = true
                            viewModel.listenFrom(open.index)
                        },
                        onRepeat = {
                            following = true
                            viewModel.repeatAyah(open.index)
                        },
                        onStatus = { viewModel.setAyahStatus(ayah, it) },
                        onSaveNote = { text ->
                            viewModel.saveNote(ayah, text)
                            toaster.show(if (text.isBlank()) copy.noteRemoved else copy.noteSaved, ToastKind.Success)
                        },
                        onCopy = {
                            context.copyToClipboard(shareText)
                            toaster.show(copy.copied, ToastKind.Success)
                        },
                        onShare = { context.shareText(shareText) },
                        onDismiss = { sheet = null },
                    )
                } else {
                    sheet = null
                }
            }
            ReaderSheet.Appearance -> AppearanceSheet(
                previewArabic = ayahs.firstOrNull { it.numberInSurah == (firstVisibleAyah.value.coerceAtLeast(0) + 1) }
                    ?.let { splitBismillah(surahNumber, it.numberInSurah, it.text) }
                    ?: ayahs.firstOrNull()?.text.orEmpty(),
                prefs = prefs,
                copy = copy,
                onTranslation = viewModel::setShowTranslation,
                onDismiss = { sheet = null },
            )
        }

        HifzOverlays(hifz)
    }
}

/** The dimmed layer behind the expanded dock; a tap folds the dock back down. */
@Composable
private fun DockScrim(
    dockState: PlayerDockState,
    onClick: () -> Unit,
) {
    val scrim = QhTheme.colors.scrim
    val active by remember { derivedStateOf { dockState.progress > 0.02f } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = dockState.progress }
            .background(scrim)
            .then(
                if (active) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun ReaderList(
    listState: LazyListState,
    surah: SurahWithAyahs,
    contentPadding: PaddingValues,
    headerData: ReaderHeaderData,
    status: SurahTodoStatus?,
    statusLabel: String,
    learnedFraction: Float,
    celebration: Int,
    headerStyle: TextStyle,
    arabicStyle: TextStyle,
    translationStyle: TextStyle,
    nextNameStyle: TextStyle,
    copy: ReaderCopy,
    enter: ReaderEnterState,
    statuses: Map<Int, AyahTodoStatus>,
    notes: Set<Int>,
    weak: Set<Int>,
    translations: Map<Int, String>,
    markedIndex: Int?,
    hifz: HifzMode,
    flashIndex: Int?,
    passage: IntRange?,
    nextSurah: NextSurahDisplay?,
    onSelect: (Int) -> Unit,
    onMore: (Int) -> Unit,
    onOpenStatus: () -> Unit,
    onOpenNext: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ayahs = surah.ayahs
    val surahNumber = surah.surah.number
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "header", contentType = "header") {
            ReaderHeader(
                data = headerData,
                status = status,
                statusLabel = statusLabel,
                learnedFraction = learnedFraction,
                celebration = celebration,
                headerStyle = headerStyle,
                bismillahStyle = arabicStyle,
                enter = enter,
                onStatusClick = onOpenStatus,
            )
        }
        itemsIndexed(ayahs, key = { _, ayah -> ayah.number }, contentType = { _, _ -> "ayah" }) { index, ayah ->
            val hifzRow = hifz.rowState(index)
            val model = AyahRowModel(
                ayah = ayah,
                index = index,
                arabic = remember(ayah) { splitBismillah(surahNumber, ayah.numberInSurah, ayah.text) },
                translation = translations[ayah.number],
                status = statuses[ayah.number],
                weak = ayah.number in weak,
                hasNote = ayah.number in notes,
                current = markedIndex == index,
                flash = flashIndex == index,
                range = rangePositionOf(index, passage),
            )
            AyahRow(
                model = model,
                arabicStyle = arabicStyle,
                translationStyle = translationStyle,
                copy = copy,
                showDivider = index != ayahs.lastIndex,
                enter = enter,
                onSelect = onSelect,
                onMore = onMore,
                hifz = hifzRow,
                hifzControls = if (hifzRow != null) hifz.controlsFor(index) else null,
            )
        }
        item(key = "end", contentType = "end") {
            if (nextSurah != null) {
                NextSurahCard(
                    title = nextSurah.title,
                    meaning = nextSurah.meaning,
                    arabicName = nextSurah.arabicName,
                    arabicStyle = nextNameStyle,
                    copy = copy,
                    enter = enter,
                    onClick = onOpenNext,
                )
            } else {
                QuranFinishedCard(copy = copy, enter = enter, onStartOver = onStartOver)
            }
            Spacer(Modifier.height(QhSpacing.x4))
        }
    }
}

/**
 * Scrolls so that the list item [itemIndex] sits [fraction] of the visible height below the top bar.
 * With [onlyIfHidden] nothing happens while the item is already fully on screen. Items already within a
 * few dp of their place are left alone, so following the audio does not twitch.
 */
private suspend fun LazyListState.bringAyahTo(
    itemIndex: Int,
    fraction: Float,
    animate: Boolean,
    onlyIfHidden: Boolean,
) {
    // At the very first frame nothing has been measured yet.
    if (layoutInfo.viewportSize.height <= 0) snapshotFlow { layoutInfo.viewportSize.height }.first { it > 0 }
    val info = layoutInfo
    val viewport = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
    if (viewport <= 0) return
    val visible = info.visibleItemsInfo.firstOrNull { it.index == itemIndex }
    val wanted = viewport * fraction
    if (visible != null) {
        val fullyVisible = visible.offset >= 0 && visible.offset + visible.size <= viewport
        if (onlyIfHidden && fullyVisible) return
        if (!onlyIfHidden && abs(visible.offset - wanted) < 16f) return
    }
    val offset = -wanted.roundToInt()
    try {
        if (animate) animateScrollToItem(itemIndex, offset) else scrollToItem(itemIndex, offset)
    } catch (e: IllegalArgumentException) {
        // A scroll offset the list does not accept: bring the item to the top instead.
        if (animate) animateScrollToItem(itemIndex) else scrollToItem(itemIndex)
    }
}

private fun Context.copyToClipboard(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("ayah", text))
}

private fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null)
    if (findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooser)
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
