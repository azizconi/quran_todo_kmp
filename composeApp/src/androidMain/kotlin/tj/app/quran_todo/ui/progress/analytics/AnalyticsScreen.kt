package tj.app.quran_todo.ui.progress.analytics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.common.JuzIndex
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.i18n.rememberA11yCopy
import tj.app.quran_todo.ui.i18n.AnalyticsCopy
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.analyticsCopy
import tj.app.quran_todo.ui.i18n.lessonCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhColors
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Rows of a list before "Show more". */
private const val CollapsedRows = 6

/**
 * The detailed analytics (`Dest.ProgressDetails`, task F5a), opened from the progress screen: retention by surah and juz, the year heat
 * map, the forecast by surah of the plan and the hardest places with a way into the drill.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenDrill: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val viewModel = koinViewModel<AnalyticsViewModel>()
    val language = LocalAppLanguage.current
    val copy = remember(language) { analyticsCopy(language) }
    val progress = remember(language) { progressCopy(language) }
    LaunchedEffect(viewModel, language) { viewModel.setLanguage(language) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val topBarState = rememberQhTopBarState(scrollState)
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) { (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx() }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val topPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(QhTheme.colors.bg)) {
        if (!ui.ready) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .padding(horizontal = QhSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
            ) {
                Skeleton(Modifier.fillMaxWidth().height(200.dp), QhTheme.shapes.lg)
                Skeleton(Modifier.fillMaxWidth().height(160.dp), QhTheme.shapes.lg)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = topPadding, bottom = navigationBottom + QhSpacing.x8)
                    .padding(horizontal = QhSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
            ) {
                RetentionSection(ui, copy, progress, onOpenReader)
                HeatSection(ui, copy, progress, viewModel::selectYear)
                ForecastSection(ui, copy, progress, onOpenReader, onOpenPlan)
                HardPlacesSection(ui, copy, onOpenReader, onOpenDrill)
            }
        }
        QhLargeTopBar(
            title = copy.screenTitle,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size -> if (scrollState.value == 0) expandedBarPx = size.height },
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

// -- Retention ------------------------------------------------------------------------------------

@Composable
private fun RetentionSection(
    ui: AnalyticsUi,
    copy: AnalyticsCopy,
    progress: ProgressCopy,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    val colors = QhTheme.colors
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rows = if (mode == 0) ui.bySurah else ui.byJuz
    val language = copy.language
    Column(Modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.retentionTitle)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.retentionHint, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        QhSegmentedControl(
            options = listOf(copy.toggleSurahs, copy.toggleJuz),
            selectedIndex = mode,
            onSelect = {
                mode = it
                expanded = false
            },
        )
        Spacer(Modifier.height(QhSpacing.x3))
        if (rows.isEmpty()) {
            QhCard { Text(text = copy.retentionEmpty, style = QhTheme.type.callout, color = colors.inkSecondary) }
            return@Column
        }
        val visible = if (expanded) rows else rows.take(CollapsedRows)
        QhCard(
            contentPadding = PaddingValues(vertical = QhSpacing.x1),
            modifier = if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize(QhMotion.gentle()),
        ) {
            visible.forEachIndexed { index, row ->
                if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
                val title = if (mode == 0) ui.nameOf(row.id) else progress.juzName(row.id)
                val open = {
                    if (mode == 0) {
                        onOpenReader(Dest.Reader(surah = row.id, origin = Origin.MAP))
                    } else {
                        val range = JuzIndex.range(row.id)
                        onOpenReader(Dest.Reader(surah = range.startSurah, ayah = range.startAyah.takeIf { it > 1 }, origin = Origin.MAP))
                    }
                }
                RetentionRowView(
                    title = title,
                    subtitle = lessonCopy(language).repetitions(row.repetitions),
                    row = row,
                    percentText = progress.percentText(row.percent),
                    onClick = open,
                )
            }
            ExpandRow(show = rows.size > CollapsedRows, expanded = expanded, hiddenCount = rows.size - CollapsedRows, progress = progress) {
                expanded = !expanded
            }
        }
    }
}

@Composable
private fun RetentionRowView(
    title: String,
    subtitle: String,
    row: RetentionRow,
    percentText: String,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val bar = when {
        row.percent >= 85 -> colors.learned
        row.percent >= 60 -> colors.primary
        else -> colors.due
    }
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, shape = QhTheme.shapes.sm, role = Role.Button)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $percentText, $subtitle" },
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = percentText, style = type.label, color = bar, modifier = Modifier.padding(start = QhSpacing.x2))
        }
        QhLinearProgress(progress = row.fraction, height = 6.dp, color = bar, trackColor = colors.surfaceMuted)
        Text(text = subtitle, style = type.label, color = colors.inkSecondary)
    }
}

@Composable
private fun ExpandRow(show: Boolean, expanded: Boolean, hiddenCount: Int, progress: ProgressCopy, onToggle: () -> Unit) {
    if (!show) return
    QhRowDivider(startIndent = QhSpacing.card)
    Box(Modifier.fillMaxWidth().padding(vertical = QhSpacing.x1), contentAlignment = Alignment.Center) {
        QhButton(
            text = if (expanded) progress.hardLess else progress.hardMore(hiddenCount),
            onClick = onToggle,
            variant = QhButtonVariant.Ghost,
            size = QhButtonSize.S,
            icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
        )
    }
}

// -- Heat map -------------------------------------------------------------------------------------

private val CellGap = 2.dp
private val MonthRowHeight = 16.dp

@Composable
private fun HeatSection(ui: AnalyticsUi, copy: AnalyticsCopy, progress: ProgressCopy, onSelectYear: (Int) -> Unit) {
    val colors = QhTheme.colors
    val heat = ui.heat ?: return
    val index = ui.years.indexOf(heat.year)
    var selected by remember(heat.year) { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.heatTitle)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.heatHint, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        QhCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = copy.previousYear,
                    onClick = { ui.years.getOrNull(index - 1)?.let(onSelectYear) },
                    enabled = index > 0,
                    filled = false,
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${heat.year}", style = QhTheme.type.title2, color = colors.ink)
                    Text(text = copy.activeDays(heat.activeDays), style = QhTheme.type.label, color = colors.inkSecondary)
                }
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = copy.nextYear,
                    onClick = { ui.years.getOrNull(index + 1)?.let(onSelectYear) },
                    enabled = index < ui.years.lastIndex,
                    filled = false,
                )
            }
            Spacer(Modifier.height(QhSpacing.x2))
            val day = selected?.let { epochDay -> heat.weeks.asSequence().flatten().firstOrNull { it?.epochDay == epochDay } }
            val text = day?.let {
                val date = LocalDate.fromEpochDays(it.epochDay)
                copy.bubble(progress.date(date.dayOfMonth, date.monthNumber), it.newCount, it.reviewedCount, it.listened, it.paused)
            }
            HeatGrid(heat = heat, progress = progress, selected = selected, selectedText = text, onSelect = { selected = it }, summary = copy.activeDays(heat.activeDays))
            Spacer(Modifier.height(QhSpacing.x3))
            if (text != null) {
                Text(
                    text = text,
                    style = QhTheme.type.callout,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(QhSpacing.x3))
                        .background(colors.primaryContainer)
                        .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2)
                        .semantics {
                            contentDescription = text
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                Spacer(Modifier.height(QhSpacing.x3))
            }
            HeatLegend(copy = copy, showPause = heat.weeks.any { week -> week.any { it?.paused == true } })
        }
    }
}

/** The color of a cell of the heat map: the four steps of `primary` over the muted track. */
private fun cellColor(level: Int, colors: QhColors): Color = when (level) {
    0 -> colors.surfaceMuted
    1 -> colors.primary.copy(alpha = 0.28f)
    2 -> colors.primary.copy(alpha = 0.5f)
    3 -> colors.primary.copy(alpha = 0.75f)
    else -> colors.primary
}

@Composable
private fun HeatGrid(
    heat: HeatYear,
    progress: ProgressCopy,
    selected: Int?,
    selectedText: String?,
    onSelect: (Int?) -> Unit,
    summary: String,
) {
    val a11y = rememberA11yCopy()
    val colors = QhTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = QhTheme.type.label.copy(color = colors.inkSecondary, fontSize = 10.sp)
    val weeks = heat.weeks.size
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = with(density) { CellGap.toPx() }
        val monthRow = with(density) { MonthRowHeight.toPx() }
        val cell = (with(density) { maxWidth.toPx() } - gap * (weeks - 1)) / weeks
        val height = with(density) { (monthRow + 7 * cell + 6 * gap).toDp() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = "${heat.year}. $summary"
                    // The day itself is reached by steps (no pixel-precise taps): the chosen day is read as the state.
                    if (selectedText != null) stateDescription = selectedText
                    customActions = listOf(
                        CustomAccessibilityAction(a11y.heatPreviousDay) { onSelect(HeatGridHit.step(heat, selected, -1)); true },
                        CustomAccessibilityAction(a11y.heatNextDay) { onSelect(HeatGridHit.step(heat, selected, 1)); true },
                        CustomAccessibilityAction(a11y.heatPreviousWeek) { onSelect(HeatGridHit.step(heat, selected, -7)); true },
                        CustomAccessibilityAction(a11y.heatNextWeek) { onSelect(HeatGridHit.step(heat, selected, 7)); true },
                    )
                }
                .pointerInput(heat, cell) {
                    // A finger is wider than a 5 dp cell: the nearest day within about 12 dp is the one meant.
                    val tolerance = 12.dp.toPx()
                    detectTapGestures { offset ->
                        val day = HeatGridHit.nearestDay(heat, offset.x, offset.y, cell, gap, monthRow, tolerance)
                        onSelect(if (day == null || selected == day) null else day)
                    }
                },
        ) {
            var lastMonth = 0
            var lastLabelEnd = -1f
            heat.weeks.forEachIndexed { week, days ->
                val x = week * (cell + gap)
                // The month label sits over the first week whose first day opens a new month.
                val first = days.firstOrNull { it != null }
                if (first != null) {
                    val date = LocalDate.fromEpochDays(first.epochDay)
                    if (date.monthNumber != lastMonth && (date.dayOfMonth <= 7)) {
                        lastMonth = date.monthNumber
                        val layout = measurer.measure(progress.months[date.monthNumber - 1], labelStyle, maxLines = 1)
                        if (x >= lastLabelEnd) {
                            drawText(layout, topLeft = Offset(x, 0f))
                            lastLabelEnd = x + layout.size.width + gap * 2
                        }
                    }
                }
                days.forEachIndexed { weekday, day ->
                    day ?: return@forEachIndexed
                    val topLeft = Offset(x, monthRow + weekday * (cell + gap))
                    val size = Size(cell, cell)
                    val radius = CornerRadius(cell * 0.22f)
                    drawRoundRect(cellColor(Analytics.level(day.count, heat.max), colors), topLeft, size, radius)
                    if (day.paused) {
                        // Hatching: two diagonals over the muted cell.
                        val stroke = Stroke(width = (cell * 0.12f).coerceAtLeast(1f))
                        val c = colors.inkTertiary
                        drawLine(c, Offset(topLeft.x + cell * 0.15f, topLeft.y + cell * 0.85f), Offset(topLeft.x + cell * 0.85f, topLeft.y + cell * 0.15f), stroke.width)
                        drawLine(c, Offset(topLeft.x + cell * 0.15f, topLeft.y + cell * 0.5f), Offset(topLeft.x + cell * 0.5f, topLeft.y + cell * 0.15f), stroke.width)
                        drawLine(c, Offset(topLeft.x + cell * 0.5f, topLeft.y + cell * 0.85f), Offset(topLeft.x + cell * 0.85f, topLeft.y + cell * 0.5f), stroke.width)
                    }
                    if (day.epochDay == selected) {
                        drawRoundRect(colors.ink, topLeft, size, radius, style = Stroke(width = (cell * 0.18f).coerceAtLeast(1.5f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatLegend(copy: AnalyticsCopy, showPause: Boolean) {
    val colors = QhTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalAlignment = Alignment.CenterVertically) {
        Text(text = copy.legendLess, style = QhTheme.type.label, color = colors.inkSecondary)
        (0..4).forEach { level -> Swatch(cellColor(level, colors)) }
        Text(text = copy.legendMore, style = QhTheme.type.label, color = colors.inkSecondary)
    }
    if (showPause) {
        Spacer(Modifier.height(QhSpacing.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp)) {
                drawRoundRect(colors.surfaceMuted, cornerRadius = CornerRadius(3.dp.toPx()))
                val w = 1.6.dp.toPx()
                drawLine(colors.inkTertiary, Offset(size.width * 0.15f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.15f), w)
                drawLine(colors.inkTertiary, Offset(size.width * 0.15f, size.height * 0.5f), Offset(size.width * 0.5f, size.height * 0.15f), w)
                drawLine(colors.inkTertiary, Offset(size.width * 0.5f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.5f), w)
            }
            Text(text = copy.legendPause, style = QhTheme.type.label, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

// -- Forecast -------------------------------------------------------------------------------------

@Composable
private fun ForecastSection(
    ui: AnalyticsUi,
    copy: AnalyticsCopy,
    progress: ProgressCopy,
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenPlan: () -> Unit,
) {
    val colors = QhTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val language = copy.language
    Column(Modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.forecastTitle)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.forecastHint, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        if (ui.forecasts.isEmpty()) {
            QhCard(onClick = onOpenPlan) { Text(text = copy.forecastEmpty, style = QhTheme.type.callout, color = colors.inkSecondary) }
            return@Column
        }
        // Collapsed: the last surah that is learned (for orientation) and the next ones; expanded: the whole queue.
        val firstOpen = ui.forecasts.indexOfFirst { it.state != QueueState.Learned }.let { if (it < 0) ui.forecasts.size else it }
        val start = if (expanded) 0 else (firstOpen - 1).coerceAtLeast(0)
        val all = ui.forecasts.drop(start)
        val visible = if (expanded) all else all.take(CollapsedRows)
        val hidden = ui.forecasts.size - visible.size
        QhCard(
            contentPadding = PaddingValues(vertical = QhSpacing.x1),
            modifier = if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize(QhMotion.gentle()),
        ) {
            visible.forEachIndexed { index, item ->
                if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
                ForecastRow(item, ui, copy, planCopy(language).let { plan -> { day: Int -> plan.dateLabel(day, ui.today) } }) {
                    onOpenReader(Dest.Reader(surah = item.surah, origin = Origin.MAP))
                }
            }
            ExpandRow(show = hidden > 0 || expanded, expanded = expanded, hiddenCount = hidden, progress = progress) { expanded = !expanded }
        }
    }
}

@Composable
private fun ForecastRow(
    item: SurahForecast,
    ui: AnalyticsUi,
    copy: AnalyticsCopy,
    dateLabel: (Int) -> String,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val current = item.state == QueueState.Current
    val name = ui.nameOf(item.surah)
    val right = when (item.state) {
        QueueState.Learned -> copy.forecastLearned
        else -> item.finishEpochDay?.let { copy.forecastDate(dateLabel(it)) } ?: ""
    }
    val left = when (item.state) {
        QueueState.Learned -> null
        else -> copy.forecastLeft(item.remainingAyahs)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (current) Modifier.background(colors.primaryContainer) else Modifier)
            .pressable(onClick = onClick, shape = QhTheme.shapes.sm, role = Role.Button)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(name, if (current) copy.forecastCurrent else null, left, right.ifBlank { null }).joinToString(", ")
            },
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SurahNumberBadge(number = item.surah, status = null, learnedFraction = 0f, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(text = name, style = type.callout.copy(fontWeight = FontWeight.SemiBold), color = if (current) colors.onPrimaryContainer else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(if (current) copy.forecastCurrent else null, left).joinToString(" · ")
            if (sub.isNotBlank()) Text(text = sub, style = type.label, color = if (current) colors.onPrimaryContainer else colors.inkSecondary)
        }
        if (right.isNotBlank()) {
            Text(
                text = right,
                style = type.label,
                color = when {
                    item.state == QueueState.Learned -> colors.learned
                    current -> colors.onPrimaryContainer
                    else -> colors.inkSecondary
                },
            )
        }
    }
}

// -- Hardest places -------------------------------------------------------------------------------

@Composable
private fun HardPlacesSection(
    ui: AnalyticsUi,
    copy: AnalyticsCopy,
    onOpenReader: (Dest.Reader) -> Unit,
    onOpenDrill: () -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Column(Modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.hardPlacesTitle)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.hardPlacesHint, style = type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        if (ui.hardPlaces.isEmpty()) {
            QhCard { Text(text = copy.hardPlacesEmpty, style = type.callout, color = colors.inkSecondary) }
            return@Column
        }
        QhCard(contentPadding = PaddingValues(vertical = QhSpacing.x1)) {
            ui.hardPlaces.forEachIndexed { index, place ->
                if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
                val name = ui.nameOf(place.surah)
                val title = copy.hardPlace(name, place.ayah)
                val detail = listOfNotNull(
                    if (place.misses > 0) copy.hardMisses(place.misses) else null,
                    if (place.current) copy.hardMarked else null,
                ).joinToString(" · ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(
                            onClick = { onOpenReader(Dest.Reader(surah = place.surah, ayah = place.ayah, origin = Origin.MAP)) },
                            shape = QhTheme.shapes.sm,
                            role = Role.Button,
                        )
                        .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
                        .semantics(mergeDescendants = true) { contentDescription = "$title, $detail" },
                    horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SurahNumberBadge(number = place.surah, status = null, learnedFraction = 0f, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(text = title, style = type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (detail.isNotBlank()) Text(text = detail, style = type.label, color = colors.due)
                    }
                }
            }
        }
        if (ui.hardPlaces.any { it.current }) {
            Spacer(Modifier.height(QhSpacing.x3))
            QhButton(
                text = copy.hardPlacesTrain,
                onClick = onOpenDrill,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Primary,
                size = QhButtonSize.L,
            )
        }
    }
}
