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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.Font
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.amiri_quran
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.analyticsCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.motion.StaggerState
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.progress.components.ActivitySection
import tj.app.quran_todo.ui.progress.components.BigRing
import tj.app.quran_todo.ui.progress.components.HardSpotsSection
import tj.app.quran_todo.ui.progress.components.PlanSection
import tj.app.quran_todo.ui.progress.components.QuranMapBlock
import tj.app.quran_todo.ui.progress.components.ReportSection
import tj.app.quran_todo.ui.progress.components.ReportFormat
import tj.app.quran_todo.ui.progress.components.ReportRequest
import tj.app.quran_todo.ui.progress.components.ReportSheet
import tj.app.quran_todo.ui.progress.components.RingHero
import tj.app.quran_todo.ui.progress.report.ReportCardStyle
import tj.app.quran_todo.ui.progress.report.ReportImage
import tj.app.quran_todo.ui.progress.report.ReportShare
import tj.app.quran_todo.ui.theme.QhFontFamily
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.qhColors

/**
 * The progress screen, on the review log and the coverage of `HifzRepository` (docs/redesign/PRODUCT_PLAN.md, 2.4): the big
 * ring (by the plan) that the home screen's small ring flies into and four blocks (UX task U25): the plan and the streak, the week
 * of sessions, the hard places with a "Train" button and the Quran map with the juz (collapsed); the report for the teacher is
 * a row under them. With nothing learned yet it invites to make a plan instead.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ProgressScreen(
    onBack: (() -> Unit)?,
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenDrill: () -> Unit,
    /** The room the tab scaffold needs under the last section (the floating mini player); only read when [onBack] is null. */
    tabBottomSpace: Dp = 0.dp,
) {
    val viewModel = koinViewModel<ProgressViewModel>()
    val language = LocalAppLanguage.current
    val copy = remember(language) { progressCopy(language) }

    LaunchedEffect(viewModel, language) { viewModel.setLanguage(language) }
    // A new day may have begun while the app slept: the ticker reads the clock again, like on the home screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val ui by viewModel.ui.collectAsStateWithLifecycle()

    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val share = rememberReportSharing(viewModel, copy)

    ProgressContent(
        ui = ui,
        copy = copy,
        onBack = onBack,
        tabBottomSpace = tabBottomSpace,
        onOpenReader = onOpenReader,
        onOpenPlan = onOpenPlan,
        onOpenDrill = onOpenDrill,
        onShareReport = { sheetOpen = true },
    )
    if (sheetOpen) {
        ReportSheet(
            copy = copy,
            analytics = analyticsCopy(language),
            today = ui.today,
            onShare = share,
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * Prepares the report of a period and hands it to the chooser of the system, as one `ACTION_SEND`: the text and, when asked
 * for, the picture of the card. Returns true when the chooser was opened. A failure shows a toast and nothing else happens.
 *
 * The card is drawn on the main thread (the [androidx.compose.ui.text.TextMeasurer] belongs to the composition; the drawing is a
 * few milliseconds), the file is written on the IO dispatcher.
 */
@Composable
private fun rememberReportSharing(
    viewModel: ProgressViewModel,
    copy: ProgressCopy,
): suspend (ReportRequest) -> Boolean {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val toaster = LocalToaster.current
    // The card is always the light theme of the app, whatever theme is on screen now.
    val calligraphy = Font(Res.font.amiri_quran, weight = FontWeight.Normal, style = FontStyle.Normal)
    val style = remember(calligraphy) {
        ReportCardStyle(
            colors = qhColors(ThemeMode.LIGHT, ThemePalette.FOREST),
            uiFont = QhFontFamily,
            calligraphyFont = FontFamily(calligraphy),
        )
    }
    return remember(viewModel, copy, context, measurer, toaster, style) {
        val action: suspend (ReportRequest) -> Boolean = { request ->
            val period = request.period
            try {
                val prepared = checkNotNull(viewModel.prepareReport(period, request.fromDay, request.toDay)) {
                    "the progress was not read yet"
                }
                val file = when (request.format) {
                    ReportFormat.Image ->
                        ReportShare.writePng(context, ReportImage.render(prepared.doc, style, measurer), prepared.toDay) to ReportShare.MIME_PNG
                    ReportFormat.Pdf ->
                        ReportShare.writePdf(context, prepared.doc, style.colors, prepared.toDay) to ReportShare.MIME_PDF
                    ReportFormat.Text -> {
                        ReportShare.clear(context)
                        null
                    }
                }
                context.startActivity(
                    ReportShare.intent(prepared.text, prepared.doc.title, file?.first, copy.chooserTitle, file?.second ?: ReportShare.MIME_PNG),
                )
                viewModel.onReportShared(period)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "progress_report_failed")
                toaster.show(copy.shareFailed, ToastKind.Error)
                false
            }
        }
        action
    }
}

@Composable
private fun ProgressContent(
    ui: ProgressUi,
    copy: ProgressCopy,
    onBack: (() -> Unit)?,
    tabBottomSpace: Dp,
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenDrill: () -> Unit,
    onShareReport: () -> Unit,
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
    // As a tab root the bar of the tabs owns the navigation-bar inset; only the floating player needs room.
    val navigationBottom = if (onBack == null) tabBottomSpace else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        when (ui.phase) {
            ProgressPhase.Loading -> LoadingBody(scrollState, topPadding, navigationBottom)
            ProgressPhase.Empty -> EmptyBody(ui, copy, onOpenPlan, scrollState, topPadding, navigationBottom)
            ProgressPhase.Content -> Body(
                ui = ui,
                copy = copy,
                onOpenReader = onOpenReader,
                onOpenPlan = onOpenPlan,
                onOpenDrill = onOpenDrill,
                onShareReport = onShareReport,
                scrollState = scrollState,
                stagger = stagger,
                topPadding = topPadding,
                navigationBottom = navigationBottom,
            )
        }
        QhLargeTopBar(
            title = copy.title,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size ->
                // Only an unscrolled bar is fully expanded.
                if (scrollState.value == 0) expandedBarPx = size.height
            },
            navigation = onBack?.let { back ->
                {
                    QhIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = copy.back,
                        onClick = back,
                        filled = false,
                    )
                }
            },
            actions = {
                if (ui.phase == ProgressPhase.Content) {
                    QhIconButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = copy.reportAction,
                        onClick = onShareReport,
                        filled = false,
                    )
                }
            },
        )
    }
}

@Composable
private fun Body(
    ui: ProgressUi,
    copy: ProgressCopy,
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenDrill: () -> Unit,
    onShareReport: () -> Unit,
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
        // The ring is what the home screen's ring flies to: it is there at once, the rest follows. Four blocks (U25).
        RingHero(hero = ui.hero, copy = copy)
        ProgressBuilder.blocks(ui).forEachIndexed { index, block ->
            val modifier = Modifier.staggeredEnter(index + 1, stagger)
            when (block) {
                // 1. The plan and the streak (the ring above is the plan's).
                ProgressBlock.PlanAndStreak -> PlanSection(
                    line = ui.plan,
                    streakDays = ui.tiles.streakDays,
                    today = ui.today,
                    copy = copy,
                    onOpenPlan = onOpenPlan,
                    modifier = modifier,
                )
                // 2. This week of sessions.
                ProgressBlock.Week -> ActivitySection(activity = ProgressBuilder.week(ui.activity), copy = copy, modifier = modifier)
                // 3. The hard places, with the way to train them.
                ProgressBlock.HardPlaces -> HardSpotsSection(
                    spots = ui.hard,
                    copy = copy,
                    onOpenAyah = onOpenReader,
                    onTrain = onOpenDrill,
                    modifier = modifier,
                )
                // 4. The Quran map with the juz inside it, collapsed.
                ProgressBlock.QuranMap -> QuranMapBlock(
                    cells = ui.cells,
                    juz = ui.juz,
                    copy = copy,
                    scrollState = scrollState,
                    onOpenSurah = { surah -> onOpenReader(Dest.Reader(surah = surah, origin = Origin.MAP)) },
                    onOpenJuz = { juz ->
                        // The reader opens at the first ayah of the juz (no highlight needed when it is the first ayah of the surah).
                        onOpenReader(Dest.Reader(surah = juz.startSurah, ayah = juz.startAyah.takeIf { it > 1 }, origin = Origin.MAP))
                    },
                    modifier = modifier,
                )
            }
        }
        // The report for the teacher: a row under the blocks (and the share action of the top bar).
        ReportSection(copy = copy, onShare = onShareReport, modifier = Modifier.staggeredEnter(5, stagger))
    }
}

/** Nothing learned yet: the empty ring is the picture, with an invitation to make a plan (or to open the one that exists). */
@Composable
private fun EmptyBody(
    ui: ProgressUi,
    copy: ProgressCopy,
    onOpenPlan: () -> Unit,
    scrollState: ScrollState,
    topPadding: Dp,
    navigationBottom: Dp,
) {
    val language = LocalAppLanguage.current
    val action = if (ui.plan.status == PlanStatus.NoPlan) copy.emptyAction else planCopy(language).title
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
            actionLabel = action,
            onAction = onOpenPlan,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            Skeleton(Modifier.weight(1f).height(124.dp), shape)
            Skeleton(Modifier.weight(1f).height(124.dp), shape)
        }
        Skeleton(Modifier.fillMaxWidth().height(180.dp), shape)
    }
}
