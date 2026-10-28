package tj.app.quran_todo.ui.progress.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.ayahCountLabel
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.progress.ActivityDayUi
import tj.app.quran_todo.ui.progress.ActivityUi
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private val BarsHeight = 112.dp
private val BubbleHeadroom = 40.dp
private val BubbleGap = 6.dp
private val StubHeight = 3.dp
private val MinBarHeight = 8.dp

/** Bars grow one after another, this far apart. */
private const val BarStaggerMillis = 20L

/** Time the section needs to settle before the bars start to grow. */
private const val BarsStartMillis = 150L
private const val BubbleLifetimeMillis = 4_500L

/**
 * Activity over the last two weeks: one bar per day, today's in the primary color and the rest in the
 * container tone. The bars grow when the section appears; a tap on a day shows its date and count in a
 * bubble above the bar.
 */
@Composable
internal fun ActivitySection(
    activity: ActivityUi,
    copy: ProgressCopy,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(
            title = copy.activityTitle,
            trailing = {
                Text(
                    text = ayahCountLabel(activity.total, copy.language),
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                )
            },
        )
        Spacer(Modifier.height(QhSpacing.x3))
        QhCard {
            ActivityChart(days = activity.days, copy = copy)
            if (activity.total == 0) {
                Spacer(Modifier.height(QhSpacing.x3))
                Text(
                    text = copy.activityEmpty,
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ActivityChart(days: List<ActivityDayUi>, copy: ProgressCopy) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val count = days.size
    val peak = max(days.maxOfOrNull { it.count } ?: 0, 1)

    var chosenIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var width by remember { mutableIntStateOf(0) }
    LaunchedEffect(chosenIndex) {
        if (chosenIndex != null) {
            delay(BubbleLifetimeMillis)
            chosenIndex = null
        }
    }

    // How tall each bar is, 0..1 of the full height. They start at 0 and grow one after another the
    // first time; later changes (an ayah was marked while the screen is open) just move the bar.
    val heights = remember(count) { List(count) { Animatable(0f) } }
    val started = remember(count) { BooleanArray(count) }
    LaunchedEffect(days, reduced) {
        days.forEachIndexed { index, day ->
            val target = day.count.toFloat() / peak
            launch {
                if (reduced) {
                    heights[index].snapTo(target)
                } else {
                    // A bar waits for its turn only before it has started to grow.
                    if (!started[index]) {
                        delay(BarsStartMillis + index * BarStaggerMillis)
                        started[index] = true
                    }
                    heights[index].animateTo(target, QhMotion.gentle())
                }
            }
        }
    }

    val barsPx = with(density) { BarsHeight.toPx() }
    val headroomPx = with(density) { BubbleHeadroom.toPx() }
    val stubPx = with(density) { StubHeight.toPx() }
    val minBarPx = with(density) { MinBarHeight.toPx() }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BarsHeight + BubbleHeadroom)
                .onSizeChanged { width = it.width },
        ) {
            Canvas(Modifier.matchParentSize()) {
                if (count == 0) return@Canvas
                val slot = size.width / count
                val barWidth = min(slot * 0.58f, 16.dp.toPx())
                val corner = CornerRadius(5.dp.toPx())
                val floor = size.height
                drawLine(
                    color = colors.hairline,
                    start = Offset(0f, floor - 0.5.dp.toPx()),
                    end = Offset(size.width, floor - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                days.forEachIndexed { index, day ->
                    val isToday = index == count - 1
                    val left = slot * index + (slot - barWidth) / 2f
                    if (day.count == 0) {
                        drawRoundRect(
                            color = if (isToday) colors.primary.copy(alpha = 0.45f) else colors.hairline,
                            topLeft = Offset(left, floor - stubPx),
                            size = Size(barWidth, stubPx),
                            cornerRadius = CornerRadius(stubPx / 2f),
                        )
                    } else {
                        val height = max(minBarPx, heights[index].value * barsPx)
                        val top = floor - height
                        drawRoundRect(
                            color = if (isToday) colors.primary else colors.primaryContainer,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, height),
                            cornerRadius = corner,
                        )
                        if (!isToday) {
                            // The container tone alone is faint on the card: a thin edge gives it a shape.
                            drawRoundRect(
                                color = colors.primary.copy(alpha = 0.3f),
                                topLeft = Offset(left, top),
                                size = Size(barWidth, height),
                                cornerRadius = corner,
                                style = Stroke(1.dp.toPx()),
                            )
                        }
                    }
                    if (chosenIndex == index) {
                        val height = if (day.count == 0) stubPx else max(minBarPx, heights[index].value * barsPx)
                        drawRoundRect(
                            color = colors.ink,
                            topLeft = Offset(left - 2.dp.toPx(), floor - height - 2.dp.toPx()),
                            size = Size(barWidth + 4.dp.toPx(), height + 4.dp.toPx()),
                            cornerRadius = CornerRadius(7.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
            }

            // One touch target per day: tapping, and TalkBack, work on the whole column.
            Row(Modifier.matchParentSize()) {
                days.forEachIndexed { index, day ->
                    val date = remember(day.epochDay) { LocalDate.fromEpochDays(day.epochDay) }
                    val label = if (index == count - 1) copy.today else copy.date(date.dayOfMonth, date.monthNumber)
                    val description = "$label: ${copy.activityValue(day.count)}"
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressable(
                                onClick = { chosenIndex = if (chosenIndex == index) null else index },
                                shape = QhTheme.shapes.xs,
                                scale = 1f,
                                highlight = false,
                            )
                            .semantics {
                                contentDescription = description
                                selected = chosenIndex == index
                            },
                    )
                }
            }

            val chosen = chosenIndex
            if (chosen != null && chosen in days.indices && width > 0) {
                val day = days[chosen]
                val date = LocalDate.fromEpochDays(day.epochDay)
                val label = if (chosen == count - 1) copy.today else copy.date(date.dayOfMonth, date.monthNumber)
                val barHeight = if (day.count == 0) stubPx else max(minBarPx, day.count.toFloat() / peak * barsPx)
                ActivityBubble(
                    text = "$label · ${copy.activityValue(day.count)}",
                    anchorX = width.toFloat() / count * (chosen + 0.5f),
                    anchorTop = headroomPx + barsPx - barHeight,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x2))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, day ->
                val date = remember(day.epochDay) { LocalDate.fromEpochDays(day.epochDay) }
                val isToday = index == count - 1
                Text(
                    text = copy.weekday(date.dayOfWeek.ordinal),
                    style = type.label.copy(fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium),
                    color = if (isToday) colors.primary else colors.inkSecondary,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/** A small inverse pill above a bar. It fills the chart ([modifier] = `matchParentSize`) and places itself. */
@Composable
private fun ActivityBubble(
    text: String,
    anchorX: Float,
    anchorTop: Float,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val appear = remember(text) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(text, reduced) {
        if (!reduced) appear.animateTo(1f, QhMotion.snappy())
    }
    Box(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
            val x = (anchorX - placeable.width / 2f)
                .coerceIn(0f, (constraints.maxWidth - placeable.width).coerceAtLeast(0).toFloat())
            val y = (anchorTop - placeable.height - BubbleGap.roundToPx()).coerceAtLeast(0f)
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
            }
        },
    ) {
        Text(
            text = text,
            style = QhTheme.type.label,
            color = colors.bg,
            maxLines = 2,
            modifier = Modifier
                .graphicsLayer {
                    alpha = appear.value
                    val pop = 0.9f + 0.1f * appear.value
                    scaleX = pop
                    scaleY = pop
                }
                .clip(QhTheme.shapes.sm)
                .background(colors.ink)
                .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2),
        )
    }
}
