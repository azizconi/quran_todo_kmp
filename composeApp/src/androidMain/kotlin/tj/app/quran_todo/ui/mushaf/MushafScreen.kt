package tj.app.quran_todo.ui.mushaf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.utils.getQuranFontFamily
import org.koin.compose.koinInject
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.CheckSaved
import tj.app.quran_todo.ui.hifz.HifzPhase
import tj.app.quran_todo.ui.home.components.MiniPlayerHost
import tj.app.quran_todo.ui.home.components.MiniPlayerMargin
import tj.app.quran_todo.ui.home.components.miniPlayerHeight
import tj.app.quran_todo.ui.i18n.hifzCopy
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.i18n.mushafCopy
import tj.app.quran_todo.ui.i18n.pagesCopy
import tj.app.quran_todo.ui.i18n.readerCopy
import tj.app.quran_todo.ui.mushaf.data.LoadedPage
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.i18n.goToCopy
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.reader.components.GoToSheet
import tj.app.quran_todo.ui.reader.components.GoToSurah
import tj.app.quran_todo.ui.navigation.ReaderRouting
import tj.app.quran_todo.ui.reader.MushafPlace
import tj.app.quran_todo.ui.reader.ayahShareText
import tj.app.quran_todo.ui.reader.components.AyahSheet
import tj.app.quran_todo.ui.reader.components.KeepScreenOnEffect
import tj.app.quran_todo.ui.reader.cutBismillah
import tj.app.quran_todo.ui.state.ReaderLayout
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.rememberArabicSize

/** The page follows the sound only when the person has not turned a page for this long. */
private const val FollowIdleMillis = 5_000L

/** How long the ayah that "continue", a search result or the mini player pointed at stays marked. */
private const val FlashMillis = 1_500L

/**
 * The mushaf as pages (task F3a): the 604 pages of the Madani mushaf in the layout of quran.com, drawn with the fonts of the pages, in
 * a pager that turns as a printed mushaf does (the next page comes in from the left). The top margin names the surah of the first line
 * with its juz and hizb, the bottom margin has the page number; a tap on the page hides and shows them. A tap on an ayah marks it, a
 * long press opens its sheet (the sheet of the reader), "Check yourself" hides the ayahs of the page, the app's mini player plays
 * at the bottom and the page follows the sound.
 *
 * [onOpenText] opens the text reader (the switch of the top bar, "Open as text" of a page that is not on the phone); the navigation
 * replaces this screen with it.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun MushafScreen(
    route: Dest.Mushaf,
    onBack: () -> Unit,
    onOpenText: (Dest.Reader) -> Unit,
) {
    val viewModel = koinViewModel<MushafViewModel>(parameters = { parametersOf(route) })
    val uiPrefs = koinInject<UiPrefs>()
    val language = LocalAppLanguage.current
    val density = LocalDensity.current
    val toaster = LocalToaster.current
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pages = remember(language) { pagesCopy(language) }
    val reader = remember(language) { readerCopy(language) }
    val hifzText = remember(language) { hifzCopy(language) }
    val mushafText = remember(language) { mushafCopy(language) }
    val goToText = remember(language) { goToCopy(language) }
    val homeText = remember(language) { homeCopy(language) }

    LaunchedEffect(language) { viewModel.onLanguage(language) }
    DisposableEffect(viewModel) { onDispose { viewModel.flushReadingPosition() } }

    val loads by viewModel.loads.collectAsState()
    val names by viewModel.names.collectAsState()
    val arabicNames by viewModel.arabicNames.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val miniPlayer by viewModel.miniPlayer.collectAsState()
    val progress = viewModel.playbackProgress.collectAsState()
    val anythingPlaying by viewModel.anythingPlaying.collectAsState()
    val check by viewModel.check.collectAsState()
    val askingToLeave by viewModel.exitPrompt.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val sheetAyah by viewModel.sheetAyah.collectAsState()
    val sheetModel by viewModel.sheet.collectAsState()
    val keepScreenOnPref by uiPrefs.keepScreenOn.collectAsState()
    KeepScreenOnEffect(enabled = keepScreenOnPref || anythingPlaying || check != null)

    // -- The pager -----------------------------------------------------------------------------------------------
    val pagerState = rememberPagerState(initialPage = viewModel.initialPage - 1) { MushafPages.COUNT }
    val currentPage = pagerState.currentPage + 1
    LaunchedEffect(pagerState, viewModel) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.onPageSettled(it + 1) }
    }

    // The tap on the page hides and shows the margins; a tap on an ayah marks it, another tap on the same ayah lets it go.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var selectedText by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = AyahKey.parse(selectedText)
    var flash by remember { mutableStateOf(viewModel.initialFlash) }
    var goToOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (flash != null) {
            delay(FlashMillis)
            flash = null
        }
    }

    // The page follows the sound unless the person has turned a page in the last few seconds.
    var lastTurnAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) lastTurnAt = SystemClock.uptimeMillis()
        }
    }
    val playingPage = playing?.let { MushafPages.pageOf(it.surah, it.ayah) }
    LaunchedEffect(playingPage) {
        val target = playingPage ?: return@LaunchedEffect
        val idle = SystemClock.uptimeMillis() - lastTurnAt
        if (check != null || pagerState.settledPage == target - 1) return@LaunchedEffect
        if (lastTurnAt != 0L && idle < FollowIdleMillis) return@LaunchedEffect
        if (reduced) pagerState.scrollToPage(target - 1) else pagerState.animateScrollToPage(target - 1)
    }

    BackHandler(enabled = check != null) { viewModel.requestExit() }
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { saved ->
            toaster.show(if (saved == CheckSaved.Review) hifzText.savedReview else hifzText.savedCheck, ToastKind.Success)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MushafEvent.StatusFailed -> {
                    haptics.perform(HapticKind.Reject)
                    toaster.show(reader.statusError, ToastKind.Error)
                }
            }
        }
    }

    // -- Layout ---------------------------------------------------------------------------------------------------
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val playerShown = miniPlayer != null
    val checkShown = check != null
    val fontScale = density.fontScale
    // The body of a page ends above the margin, the mini player and the check panel; it gives way to them with a spring, not a jump.
    val reserved = when {
        checkShown -> CheckPanelReserve
        playerShown -> miniPlayerHeight(fontScale) + MiniPlayerGap
        else -> 0.dp
    }
    val bottomReserve by animateDpAsState(reserved, label = "mushaf-bottom-reserve")
    val fonts = remember { PageFonts() }
    val layouts = remember { PageLayouts() }
    val hafs = getQuranFontFamily(ReadingFontStyle.MADINAH_MUSHAF)
    // The reading font of the settings: the Madinah one is the design of the page fonts, so its pages keep their glyphs.
    val readingStyle = LocalReadingFontStyle.current
    val textFont = if (readingStyle == ReadingFontStyle.MADINAH_MUSHAF) null else getQuranFontFamily(readingStyle)
    val resolver = LocalFontFamilyResolver.current

    val colors = QhTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.readerBg),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val blockWidth = PageGeometry.blockWidth(widthPx, density.density)
        val glyphSize = PageGeometry.glyphSize(blockWidth)
        val bodyTop = topInset + MushafBarRowHeight
        val bodyBottom = navInset + MushafFootHeight + bottomReserve
        val bodyHeightPx = with(density) { (maxHeight - bodyTop - bodyBottom).coerceAtLeast(0.dp).toPx() }
        val slot = PageGeometry.slotHeight(bodyHeightPx, glyphSize)
        val slotState = rememberUpdatedState(slot)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // A printed mushaf is turned from the left: the next page comes in from the left.
            reverseLayout = true,
            userScrollEnabled = !checkShown,
            beyondViewportPageCount = 1,
            key = { it },
        ) { index ->
            val number = index + 1
            LaunchedEffect(number) { viewModel.ensure(number) }
            val load = loads[number]
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = bodyTop, bottom = bodyBottom),
            ) {
                when (load) {
                    is PageLoad.Ready -> ReadyPage(
                        loaded = load.loaded,
                        fonts = fonts,
                        layouts = layouts,
                        hafs = hafs,
                        textFont = textFont,
                        textFontName = readingStyle.storageValue,
                        wordTexts = viewModel::wordTexts,
                        resolver = resolver,
                        arabicNames = arabicNames,
                        blockWidth = blockWidth,
                        glyphSize = glyphSize,
                        slot = slotState,
                        bodyHeightPx = bodyHeightPx,
                        marks = remember(selected, flash, playing, check, load.loaded.page) {
                            marksFor(load.loaded.page, selected ?: flash, playing, check)
                        },
                        interactive = !checkShown,
                        copy = pages,
                        onTapAyah = { key ->
                            if (selected == key) {
                                // The picked ayah once more: it lets go and the margins come and go, as a tap on the page does.
                                selectedText = null
                                chromeVisible = !chromeVisible
                            } else {
                                // Another ayah: it is picked, and the margins are there for what can be done with it.
                                selectedText = key.text
                                chromeVisible = true
                            }
                        },
                        onLongPressAyah = { key ->
                            selectedText = key.text
                            viewModel.openSheet(key)
                        },
                        onTapBlank = {
                            selectedText = null
                            chromeVisible = !chromeVisible
                        },
                    )
                    PageLoad.Missing -> PageMissing(
                        copy = pages,
                        onRetry = { viewModel.retry(number) },
                        onOpenText = { onOpenText(ReaderRouting.textFor(number)) },
                    )
                    else -> PageSkeleton()
                }
            }
        }

        // -- The margins ------------------------------------------------------------------------------------------
        val firstAyah = MushafPages.firstAyahOf(currentPage)
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
        ) {
            val playingNow = playing != null && miniPlayer?.isPlaying == true
            MushafTopBar(
                surahName = names[firstAyah.surah].orEmpty(),
                place = mushafText.placeText(
                    MushafPlace(MushafPages.juzOf(currentPage), MushafPages.hizbOf(currentPage), currentPage),
                ),
                playing = playingNow,
                checking = checkShown,
                backLabel = reader.back,
                listenLabel = hifzText.listen,
                pauseLabel = hifzText.listenPause,
                checkLabel = hifzText.eyeAction,
                textLabel = pages.openAsText,
                goTo = goToText,
                onGoTo = { goToOpen = true },
                onBack = onBack,
                onPlay = { viewModel.playPage((loads[currentPage] as? PageLoad.Ready)?.loaded?.page, selected) },
                onCheck = {
                    (loads[currentPage] as? PageLoad.Ready)?.loaded?.page?.let { page ->
                        selectedText = null
                        viewModel.startCheck(page, selected)
                    }
                },
                onText = {
                    uiPrefs.setReaderLayout(ReaderLayout.LIST)
                    onOpenText(ReaderRouting.textFor(currentPage))
                },
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navInset),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // The foot sits at the very bottom, under the player and the check panel.
            MushafFoot(page = currentPage)
        }

        // The mini player of the app, as on the home screen; a tap on it brings the page of the sound.
        MiniPlayerHost(
            player = if (checkShown) null else miniPlayer,
            progress = progress,
            copy = homeText,
            onOpen = {
                val target = playingPage ?: return@MiniPlayerHost
                scope.launch { if (reduced) pagerState.scrollToPage(target - 1) else pagerState.animateScrollToPage(target - 1) }
            },
            onToggle = viewModel::togglePlayback,
            onStop = viewModel::stopPlayback,
            // The pill stands on the page number strip, not over it.
            bottomInset = navInset + MushafFootHeight - MiniPlayerMargin,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // -- "Check yourself" on the page -------------------------------------------------------------------------
        AnimatedVisibility(
            visible = check != null && check?.finished == false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navInset + 8.dp),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            val shown = check
            if (shown != null) {
                CheckPanel(
                    check = shown,
                    copy = hifzText,
                    onReveal = viewModel::reveal,
                    onMark = viewModel::mark,
                    onClose = viewModel::requestExit,
                )
            }
        }
    }

    // -- Sheets ----------------------------------------------------------------------------------------------------
    // "Go to" from the surah name: the pager turns to the page that holds the ayah and the ayah is marked for a moment.
    if (goToOpen) {
        GoToSheet(
            surahs = remember(names, arabicNames) {
                (1..114).map { GoToSurah(number = it, name = names[it].orEmpty().ifBlank { "№ $it" }, arabic = arabicNames[it].orEmpty()) }
            },
            currentSurah = MushafPages.firstAyahOf(currentPage).surah,
            copy = goToText,
            onGoTo = { target ->
                val page = MushafPages.pageOf(target.surah, target.ayah)
                val mark = AyahKey(target.surah, target.ayah)
                selectedText = null
                flash = mark
                scope.launch {
                    if (reduced) pagerState.scrollToPage(page - 1) else pagerState.animateScrollToPage(page - 1)
                    delay(FlashMillis)
                    if (flash == mark) flash = null
                }
            },
            onDismiss = { goToOpen = false },
        )
    }
    val key = sheetAyah
    val model = sheetModel
    if (key != null && model != null) {
        val quranFamily = getQuranFontFamily()
        val type = QhTheme.type
        val shareText = ayahShareText(
            cutBismillah(key.surah, key.ayah, model.ayah.text),
            null,
            reader.quranRef,
            key.surah,
            key.ayah,
            surahName = model.surahName,
        )
        val sheetArabicSize = rememberArabicSize(24f)
        AyahSheet(
            model = model,
            arabicStyle = remember(type, quranFamily, sheetArabicSize) { type.quranBody(quranFamily, sheetArabicSize) },
            copy = reader,
            onListenFromHere = { viewModel.listenFrom(key) },
            onRepeat = { viewModel.repeatAyah(key) },
            onCheckFromHere = {
                (loads[currentPage] as? PageLoad.Ready)?.loaded?.page?.let { page ->
                    selectedText = null
                    viewModel.startCheck(page, key)
                }
            },
            onMarkKnown = { viewModel.markAyahKnown(key) },
            onForget = { viewModel.forgetAyah(key) },
            onSaveNote = { text ->
                viewModel.saveNote(key, text)
                toaster.show(if (text.isBlank()) reader.noteRemoved else reader.noteSaved, ToastKind.Success)
            },
            onCopy = {
                context.copyToClipboard(shareText)
                toaster.show(reader.copied, ToastKind.Success)
            },
            onShare = { context.shareText(shareText) },
            onDismiss = viewModel::closeSheet,
        )
    }

    val running = check
    if (running != null && askingToLeave && !running.finished) {
        CheckLeaveSheet(running, hifzText, onKeepGoing = viewModel::dismissExitPrompt, onLeave = viewModel::exitCheck)
    }
    // The grade sheet comes up a moment after the last answer, once the last ayah has been shown again.
    var gradeReady by remember { mutableStateOf(false) }
    val finished = running?.finished == true
    LaunchedEffect(finished) {
        if (finished) {
            if (!reduced) delay(GradeDelayMillis)
            gradeReady = true
        } else {
            gradeReady = false
        }
    }
    if (running != null && finished && gradeReady) {
        val first = running.items.first().key
        val title = "${names[first.surah].orEmpty()} · ${mushafText.pageLabel(running.page)}"
        PageGradeSheet(
            check = running,
            title = title,
            copy = hifzText,
            busy = saving,
            onConfirm = viewModel::finishCheck,
            onDismiss = viewModel::exitCheck,
        )
    }
}

/** The grade sheet waits this long after the last answer. */
private const val GradeDelayMillis = 480L

/** Room the check panel takes at the foot of the body. */
private val CheckPanelReserve = 132.dp

/** The gap between the mini player and the margin under it. */
private val MiniPlayerGap = 16.dp

/**
 * What marks the ayahs of [page]: the picked ayah, the ayah that sounds, and the looks of the check when it runs on this page (the
 * ayahs it did not take are dimmed, the ones it did are hidden until their turn).
 */
private fun marksFor(page: MushafPage, picked: AyahKey?, playing: AyahKey?, check: PageCheck?): PageMarks {
    val running = check?.takeIf { it.page == page.number } ?: return PageMarks(selected = picked, playing = playing)
    val looks = HashMap<AyahKey, AyahLook>()
    for (key in page.ayahs) {
        looks[key] = when (running.phaseOf(key)) {
            HifzPhase.Current -> if (running.revealed) AyahLook.Normal else AyahLook.Hidden
            HifzPhase.Upcoming -> AyahLook.Waiting
            HifzPhase.Done -> if (running.markOf(key) == AyahMark.HARD) AyahLook.Hard else AyahLook.Normal
            HifzPhase.Outside -> AyahLook.Dimmed
        }
    }
    return PageMarks(selected = null, playing = playing, current = running.current?.key, looks = looks)
}

/**
 * A page that is loaded: its layout is measured off the main thread (the glyph of every word, [PageLayout]) and drawn as soon as it is
 * ready; the pages next to the one on screen are measured in advance by the pager, so a turn finds them done. A page that does not fit
 * the body (a phone held sideways) scrolls.
 */
@Composable
private fun ReadyPage(
    loaded: LoadedPage,
    fonts: PageFonts,
    layouts: PageLayouts,
    hafs: FontFamily,
    textFont: FontFamily?,
    textFontName: String,
    wordTexts: (MushafPage) -> Map<AyahKey, List<String>>,
    resolver: FontFamily.Resolver,
    arabicNames: Map<Int, String>,
    blockWidth: Float,
    glyphSize: Float,
    slot: State<Float>,
    bodyHeightPx: Float,
    marks: PageMarks,
    interactive: Boolean,
    copy: tj.app.quran_todo.ui.i18n.PagesCopy,
    onTapAyah: (AyahKey) -> Unit,
    onLongPressAyah: (AyahKey) -> Unit,
    onTapBlank: () -> Unit,
) {
    val density = LocalDensity.current
    val family = remember(loaded.page.number, loaded.font) { fonts.familyFor(loaded.page.number, loaded.font) }
    // A page that comes back (the pager lets go of the pages away from the one on screen) finds its measured layout again.
    // The words of the bundled text, for a page in the reading font (empty for the Madinah font, or while the text is loading).
    val texts = remember(loaded.page, textFont, arabicNames) { if (textFont == null) emptyMap() else wordTexts(loaded.page) }
    val layoutKey = PageLayouts.Key(
        loaded.page.number, blockWidth, glyphSize, System.identityHashCode(arabicNames), loaded.font.path,
        textFont = if (textFont == null || texts.isEmpty()) null else textFontName,
    )
    val layout by produceState<PageLayout?>(layouts.get(layoutKey), layoutKey, family) {
        val known = layouts.get(layoutKey)
        if (known != null) {
            value = known
            return@produceState
        }
        // Another width (the phone was turned) cannot be drawn with the old measures; new names only change the headers, so the page
        // stays on screen until they are laid out.
        if (value?.let { it.blockWidth != blockWidth || it.glyphSize != glyphSize } == true) value = null
        value = try {
            withContext(Dispatchers.Default) {
                buildPageLayout(
                    loaded.page, family, hafs, glyphSize, blockWidth, { arabicNames[it].orEmpty() }, density, resolver,
                    textFont = textFont.takeIf { texts.isNotEmpty() },
                    wordText = { word -> texts[word.key]?.getOrNull(word.position - 1) },
                )
            }.also { layouts.put(layoutKey, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            // A font that cannot be read: the page stays quiet (the switch to the text is in the top bar) and the cause is reported.
            AppTelemetry.logError(e, context = "mushaf_page_layout_failed")
            null
        }
    }
    val ready = layout
    if (ready == null) {
        PageSkeleton()
        return
    }
    val scroll = rememberScrollState()
    val lineCount = ready.page.lineCount
    // A body shorter than the lines need (a phone held sideways) scrolls.
    val contentHeight = maxOf(bodyHeightPx, slot.value * lineCount)
    val description = copy.pageOf(loaded.page.number)
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        PageBody(
            layout = ready,
            marks = marks,
            slot = slot,
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { contentHeight.toDp() })
                .semantics { contentDescription = description },
            interactive = interactive,
            onTapAyah = onTapAyah,
            onLongPressAyah = onLongPressAyah,
            onTapBlank = onTapBlank,
        )
    }
}

/** The last few fonts of pages as `FontFamily` objects, so turning back to a page does not make its family again. */
internal class PageFonts(private val limit: Int = 6) {
    private val families = LinkedHashMap<Int, Pair<String, FontFamily>>()

    @Synchronized
    fun familyFor(page: Int, file: File): FontFamily {
        val known = families.remove(page)
        val family = if (known != null && known.first == file.path) known.second else FontFamily(androidx.compose.ui.text.font.Font(file))
        families[page] = file.path to family
        while (families.size > limit) families.remove(families.keys.first())
        return family
    }
}

/** The last few measured pages ([PageLayout]), so a page that the pager had let go of is not measured again when it comes back. */
internal class PageLayouts(private val limit: Int = 5) {
    internal data class Key(
        val page: Int,
        val blockWidth: Float,
        val glyphSize: Float,
        val names: Int,
        val font: String,
        /** The reading font the words are drawn in, or null for the glyphs of the page font. */
        val textFont: String? = null,
    )

    private val map = LinkedHashMap<Key, PageLayout>()

    @Synchronized
    fun get(key: Key): PageLayout? {
        val found = map.remove(key) ?: return null
        map[key] = found
        return found
    }

    @Synchronized
    fun put(key: Key, layout: PageLayout) {
        map.remove(key)
        map[key] = layout
        while (map.size > limit) map.remove(map.keys.first())
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
