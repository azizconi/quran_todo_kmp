package tj.app.quran_todo.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.ui.components.AyahWheel
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSlider
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular
import kotlin.math.roundToInt

/**
 * "My plan" (docs/redesign/PRODUCT_PLAN.md, 2.4 and 3.2): what is learned and in which order, the pace
 * (ayahs a day, or a date to finish by), the circle of the old, and a live forecast. The person edits a
 * draft; nothing changes until "Save" (an explicit button, because changing the queue restarts the plan's
 * clock and the forecast should not follow every accidental drag). Leaving with unsaved changes asks first.
 * "Delete the plan" (with a confirmation) keeps what is memorized.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlanScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<PlanViewModel>()
    val language = LocalAppLanguage.current
    val copy = remember(language) { planCopy(language) }
    val toaster = LocalToaster.current
    val latestOnBack by rememberUpdatedState(onBack)
    val latestCopy by rememberUpdatedState(copy)

    LaunchedEffect(language) { viewModel.setLanguage(language) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlanEvent.Saved -> {
                    toaster.show(latestCopy.savedToast, ToastKind.Success)
                    latestOnBack()
                }

                PlanEvent.Cleared -> {
                    toaster.show(latestCopy.deletedToast, ToastKind.Info)
                    latestOnBack()
                }

                PlanEvent.Failed -> toaster.show(latestCopy.saveFailed, ToastKind.Error)
            }
        }
    }

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var askUnsaved by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var addSurah by rememberSaveable { mutableStateOf(false) }

    // Back with unsaved changes asks first.
    BackHandler(enabled = ui.edited) { askUnsaved = true }

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val topBarState = rememberQhTopBarState(scrollState)
    val stagger = rememberStaggerState(ready = ui.loaded)
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) {
        (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx()
    }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val listTopPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(QhTheme.colors.bg)) {
        if (ui.loaded) {
            PlanContent(
                ui = ui,
                copy = copy,
                viewModel = viewModel,
                scrollState = scrollState,
                topPadding = listTopPadding,
                bottomPadding = navigationBottom + SaveBarHeight + QhSpacing.x6,
                stagger = stagger,
                onPickDate = { pickDate = true },
                onAddSurah = { addSurah = true },
                onDelete = { askDelete = true },
            )
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
                    onClick = { if (ui.edited) askUnsaved = true else onBack() },
                    filled = false,
                )
            },
        )
        SaveBar(
            copy = copy,
            enabled = ui.loaded && ui.canSave && ui.dirty,
            creating = !ui.hasSavedPlan,
            onSave = viewModel::save,
            navigationBottom = navigationBottom,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (pickDate) {
        TargetDateDialog(
            initialDay = ui.draft.targetDay ?: ui.suggestedTarget,
            today = ui.today,
            copy = copy,
            onPick = viewModel::setTarget,
            onDismiss = { pickDate = false },
        )
    }
    if (addSurah) {
        AddSurahSheet(
            catalog = ui.catalog,
            inQueue = ui.draft.queue.toSet(),
            copy = copy,
            onAdd = viewModel::add,
            onDismiss = { addSurah = false },
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = QhTheme.colors.surface,
            title = { Text(copy.deleteTitle, style = QhTheme.type.title1, color = QhTheme.colors.ink) },
            text = { Text(copy.deleteBody, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDelete = false
                        viewModel.clear()
                    },
                ) { Text(copy.deleteConfirm, style = QhTheme.type.title2, color = QhTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text(copy.cancel, style = QhTheme.type.title2, color = QhTheme.colors.primary) }
            },
        )
    }
    if (askUnsaved) {
        // "Save" and "Don't save" are the two buttons; tapping outside or back keeps editing.
        AlertDialog(
            onDismissRequest = { askUnsaved = false },
            containerColor = QhTheme.colors.surface,
            title = { Text(copy.unsavedTitle, style = QhTheme.type.title1, color = QhTheme.colors.ink) },
            text = { Text(copy.unsavedBody, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askUnsaved = false
                        if (ui.canSave) viewModel.save()
                    },
                    enabled = ui.canSave,
                ) { Text(copy.save, style = QhTheme.type.title2, color = QhTheme.colors.primary) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        askUnsaved = false
                        onBack()
                    },
                ) { Text(copy.discard, style = QhTheme.type.title2, color = QhTheme.colors.danger) }
            },
        )
    }
}

private val SaveBarHeight = 56.dp + QhSpacing.x3 * 2

@Composable
private fun PlanContent(
    ui: PlanUi,
    copy: PlanCopy,
    viewModel: PlanViewModel,
    scrollState: androidx.compose.foundation.ScrollState,
    topPadding: Dp,
    bottomPadding: Dp,
    stagger: tj.app.quran_todo.ui.motion.StaggerState,
    onPickDate: () -> Unit,
    onAddSurah: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = bottomPadding)
            .padding(horizontal = QhSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
    ) {
        PlanSection(copy.sectionWhat, Modifier.staggeredEnter(0, stagger)) {
            WhatSection(ui, copy, viewModel, onAddSurah)
        }
        PlanSection(copy.sectionPace, Modifier.staggeredEnter(1, stagger)) {
            PaceSection(ui, copy, viewModel, onPickDate)
        }
        PlanSection(copy.sectionOld, Modifier.staggeredEnter(2, stagger)) {
            OldSection(ui, copy, viewModel)
        }
        PlanSection(copy.sectionForecast, Modifier.staggeredEnter(3, stagger)) {
            ForecastSection(ui, copy)
        }
        if (ui.hasSavedPlan) {
            QhButton(
                text = copy.delete,
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().staggeredEnter(4, stagger),
                variant = QhButtonVariant.Danger,
                size = QhButtonSize.M,
            )
        }
    }
}

/** A titled card: an overline header, then the card with its content. */
@Composable
private fun PlanSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title, Modifier.padding(horizontal = QhSpacing.x1))
        Spacer(Modifier.height(QhSpacing.x2))
        QhCard(contentPadding = PaddingValues(QhSpacing.card)) {
            Column(
                Modifier.fillMaxWidth().animateContentSize(QhMotion.gentle<androidx.compose.ui.unit.IntSize>().orSnap()),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) { content() }
        }
    }
}

// -- What I learn ---------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatSection(ui: PlanUi, copy: PlanCopy, viewModel: PlanViewModel, onAddSurah: () -> Unit) {
    val colors = QhTheme.colors
    val draft = ui.draft
    var expanded by rememberSaveable { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
    ) {
        listOf(
            HifzScope.JUZ_30 to copy.scopeJuz30,
            HifzScope.JUZ_29 to copy.scopeJuz29,
            HifzScope.FROM_START to copy.scopeFromStart,
            HifzScope.CUSTOM to copy.scopeCustom,
        ).forEach { (scope, label) ->
            QhChip(label = label, selected = draft.scope == scope, onClick = { viewModel.selectScope(scope) })
        }
    }
    val description = when (draft.scope) {
        HifzScope.JUZ_30 -> copy.describeJuz30
        HifzScope.JUZ_29 -> copy.describeJuz29
        HifzScope.FROM_START -> copy.describeFromStart
        HifzScope.CUSTOM -> copy.describeCustom
    }
    Column {
        Text(description, style = QhTheme.type.callout, color = colors.inkSecondary)
        if (ui.queue.isNotEmpty()) {
            Text(
                text = copy.scopeInfo(draft.queue.distinct().size, PlanMath.total(draft.queue)),
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
            )
        }
    }
    if (ui.queue.isEmpty()) {
        Text(copy.queueEmpty, style = QhTheme.type.body, color = colors.inkSecondary)
    } else {
        SurahQueue(
            rows = ui.queue,
            editable = draft.isCustom,
            copy = copy,
            expanded = expanded,
            onMove = viewModel::move,
            onRemove = viewModel::remove,
        )
        if (!draft.isCustom && ui.queue.size > FoldLimit) {
            // A preset is long (up to 114 rows): fold it, one tap shows all of it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = { expanded = !expanded })
                    .padding(vertical = QhSpacing.x2),
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(QhSpacing.iconBar),
                )
                Text(
                    text = if (expanded) copy.showLess else copy.showAll(ui.queue.size),
                    style = QhTheme.type.title2,
                    color = colors.primary,
                )
            }
        }
    }
    if (draft.isCustom) {
        QhButton(
            text = copy.addSurah,
            onClick = onAddSurah,
            modifier = Modifier.fillMaxWidth(),
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.M,
            icon = Icons.Rounded.Add,
        )
    } else {
        QhButton(
            text = copy.editOrder,
            onClick = viewModel::editOrder,
            modifier = Modifier.fillMaxWidth(),
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.M,
            icon = Icons.Rounded.Edit,
        )
    }
}

/** A queue of up to this many rows is shown whole. */
private const val FoldLimit = 6

// -- Pace -----------------------------------------------------------------------------------------

@Composable
private fun PaceSection(ui: PlanUi, copy: PlanCopy, viewModel: PlanViewModel, onPickDate: () -> Unit) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val draft = ui.draft
    QhSegmentedControl(
        options = listOf(copy.modePerDay, copy.modeByDate),
        selectedIndex = if (draft.mode == PaceMode.PerDay) 0 else 1,
        onSelect = { viewModel.setMode(if (it == 0) PaceMode.PerDay else PaceMode.ByDate) },
    )
    when (draft.mode) {
        PaceMode.PerDay -> AyahWheel(
            label = copy.perDayLabel,
            minimum = HifzConstants.DAILY_NEW_MIN,
            maximum = HifzConstants.DAILY_NEW_MAX,
            value = draft.perDay,
            onValueChange = viewModel::setPerDay,
            modifier = Modifier.fillMaxWidth(),
        )

        PaceMode.ByDate -> {
            val target = draft.targetDay ?: ui.suggestedTarget
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = onPickDate, shape = QhTheme.shapes.md)
                    .background(colors.surfaceMuted, QhTheme.shapes.md)
                    .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${copy.finishBy}: ${copy.dateLabel(target, ui.today)}"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.primary, modifier = Modifier.size(QhSpacing.iconBar))
                Text(copy.finishBy, style = type.callout, color = colors.inkSecondary, modifier = Modifier.weight(1f))
                Text(copy.dateLabel(target, ui.today), style = type.title2.tabular(), color = colors.primary)
            }
            val summary = ui.summary
            summary?.requiredPerDay?.let { required ->
                Text(
                    text = copy.needPerDay(required.coerceAtLeast(1)),
                    style = type.title2,
                    color = if (summary.tooFast) colors.due else colors.ink,
                )
            }
            if (summary != null && summary.tooFast) {
                Warning(copy.tooFast(summary.requiredPerDay ?: HifzConstants.DAILY_NEW_MAX))
            }
        }
    }
    // The time of one day's new passage, in the minutes the lesson counts.
    ui.estimate?.let { estimate ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(QhSpacing.iconInline))
            Text(copy.minutesPerDay(estimate.minutes), style = type.callout.tabular(), color = colors.inkSecondary)
        }
        if (estimate.overBudget) Warning(copy.longLesson(estimate.minutes))
    }
}

/** A soft warning: a warning icon and a line in the "due" color. Not a red error: the plan can still be saved. */
@Composable
private fun Warning(text: String) {
    val colors = QhTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.dueContainer, QhTheme.shapes.md)
            .padding(QhSpacing.x3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.due, modifier = Modifier.size(QhSpacing.iconInline))
        Text(text, style = QhTheme.type.callout, color = colors.ink)
    }
}

// -- The circle of the old ------------------------------------------------------------------------

@Composable
private fun OldSection(ui: PlanUi, copy: PlanCopy, viewModel: PlanViewModel) {
    val colors = QhTheme.colors
    val days = ui.draft.manzilDays
    Text(copy.oldTitle(days), style = QhTheme.type.title2, color = colors.ink)
    QhSlider(
        value = days.toFloat(),
        onValueChange = { viewModel.setManzilDays(it.roundToInt()) },
        valueRange = HifzConstants.MANZIL_DAYS_MIN.toFloat()..HifzConstants.MANZIL_DAYS_MAX.toFloat(),
        steps = HifzConstants.MANZIL_DAYS_MAX - HifzConstants.MANZIL_DAYS_MIN - 1,
        modifier = Modifier.semantics { contentDescription = copy.oldTitle(days) },
    )
    val pages = ui.oldPagesPerDay
    Text(
        text = if (pages != null) copy.oldPages(pages) else copy.oldNone,
        style = QhTheme.type.callout.tabular(),
        color = QhTheme.colors.inkSecondary,
    )
}

// -- Forecast -------------------------------------------------------------------------------------

@Composable
private fun ForecastSection(ui: PlanUi, copy: PlanCopy) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val summary = ui.summary
    if (summary == null) {
        Skeleton(Modifier.fillMaxWidth(0.6f).height(28.dp))
        Skeleton(Modifier.fillMaxWidth(0.4f).height(18.dp))
        return
    }
    val reduced = LocalReducedMotion.current
    val headline = when (val h = summary.headline) {
        ForecastHeadline.Empty -> copy.forecastEmpty
        ForecastHeadline.Finished -> copy.finished
        is ForecastHeadline.FinishOn -> copy.finishOn(h.epochDay, ui.today)
    }
    AnimatedContent(
        targetState = headline,
        transitionSpec = {
            if (reduced) {
                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            } else {
                fadeIn(androidx.compose.animation.core.tween(QhMotion.Short)) togetherWith fadeOut(androidx.compose.animation.core.tween(QhMotion.Instant))
            }
        },
        label = "forecast-headline",
    ) { text ->
        Text(text, style = type.title1, color = colors.ink)
    }
    if (summary.headline !is ForecastHeadline.Empty) {
        if (summary.totalAyahs > 0) {
            QhLinearProgress(
                progress = (summary.totalAyahs - summary.remainingAyahs).toFloat() / summary.totalAyahs,
                modifier = Modifier.semantics {
                    contentDescription = copy.remaining(summary.remainingAyahs, summary.totalAyahs)
                },
            )
            Text(copy.remaining(summary.remainingAyahs, summary.totalAyahs), style = type.callout.tabular(), color = colors.inkSecondary)
        }
        summary.requiredPerDay?.let { required ->
            if (summary.remainingAyahs > 0) {
                Text(copy.needPerDay(required.coerceAtLeast(1)), style = type.title2, color = colors.ink)
            }
        }
        if (summary.behindAyahs > 0) {
            Text(copy.behind(summary.behindAyahs), style = type.title2, color = colors.due)
        }
    }
}

// -- Save bar -------------------------------------------------------------------------------------

/** The button that saves the plan, pinned to the bottom over a soft fade of the page color. */
@Composable
private fun SaveBar(
    copy: PlanCopy,
    enabled: Boolean,
    creating: Boolean,
    onSave: () -> Unit,
    navigationBottom: Dp,
    modifier: Modifier = Modifier,
) {
    val bg = QhTheme.colors.bg
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.96f), bg),
                        startY = 0f,
                        endY = size.height,
                    ),
                    topLeft = Offset.Zero,
                )
            }
            .padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x6, bottom = navigationBottom + QhSpacing.x3),
    ) {
        QhButton(
            text = if (creating) copy.create else copy.save,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}
