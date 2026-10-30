package tj.app.quran_todo.ui.progress.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.progress.ActivityDayUi
import tj.app.quran_todo.ui.progress.ActivityUi
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private val BarsHeight = 112.dp
private val BubbleHeadroom = 44.dp
private val BubbleGap = 6.dp
private val StubHeight = 3.dp
private val MinBarHeight = 8.dp
private val MaxBarWidth = 9.dp

/** Bars grow one after another, this far apart. */
private const val BarStaggerMillis = 12L

/** Time the section needs to settle before the bars start to grow. */
private const val BarsStartMillis = 150L
private const val BubbleLifetimeMillis = 4_500L

/** The days under which a date is written (the first one, then every sixth) and the last one says "Today". */
private const val LabelEvery = 6

/** A date is not written this close to "Today" (the two would run into each other). */
private const val LabelClearance = 9

/**
 * Activity over the last 30 days: one stacked bar per day, the ayahs repeated at the bottom and the new ones on top, in two
 * tones (and a legend). Empty days are a short hairline, so they are visible. The bars grow when the section appears; a tap on
 * a day, or a finger dragged along the bars, shows its date and numbers in a bubble above the bar.
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
                copy.activitySummary(activity.totalLearned, activity.totalReviewed)?.let { summary ->
                    Text(text = summary, style = QhTheme.type.callout, color = colors.inkSecondary)
                }
            },
        )
        Spacer(Modifier.height(QhSpacing.x3))
        QhCard {
            ActivityChart(days = activity.days, copy = copy)
            Spacer(Modifier.height(QhSpacing.x3))
            ActivityLegend(copy)
            if (activity.isEmpty) {
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
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val count = days.size
    val peak = max(days.maxOfOrNull { it.total } ?: 0, 1)

    var chosenIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var width by remember { mutableIntStateOf(0) }
    LaunchedEffect(chosenIndex) {
        if (chosenIndex != null) {
            delay(BubbleLifetimeMillis)
            chosenIndex = null
        }
    }

    // How tall each stack is, 0..1 of the full height. They start at 0 and grow one after another the first time;
    // later changes (a passage was learned while the screen is open) just move the bar.
    val heights = remember(count) { List(count) { Animatable(0f) } }
    val started = remember(count) { BooleanArray(count) }
    LaunchedEffect(days, reduced) {
        days.forEachIndexed { index, day ->
            val target = day.total.toFloat() / peak
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
    val slotOf: (Float) -> Int = { x -> if (width > 0 && count > 0) (x / width * count).toInt().coerceIn(0, count - 1) else 0 }

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
                val barWidth = min(slot * 0.62f, MaxBarWidth.toPx())
                val corner = CornerRadius(min(4.dp.toPx(), barWidth / 2f))
                val floor = size.height
                drawLine(
                    color = colors.hairline,
                    start = Offset(0f, floor - 0.5.dp.toPx()),
                    end = Offset(size.width, floor - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                days.forEachIndexed { index, day ->
                    val left = slot * index + (slot - barWidth) / 2f
                    if (day.total == 0) {
                        val isToday = index == count - 1
                        drawRoundRect(
                            color = if (isToday) colors.primary.copy(alpha = 0.45f) else colors.hairline,
                            topLeft = Offset(left, floor - stubPx),
                            size = Size(barWidth, stubPx),
                            cornerRadius = CornerRadius(stubPx / 2f),
                        )
                    } else {
                        val height = max(minBarPx, heights[index].value * barsPx)
                        val top = floor - height
                        // The share of the stack that is "reviewed" (bottom); the new ayahs are on top.
                        val reviewedPx = height * day.reviewed / day.total
                        val outline = Path().apply { addRoundRect(RoundRect(left, top, left + barWidth, floor, corner)) }
                        clipPath(outline) {
                            drawRect(reviewedTone(colors.gold), Offset(left, floor - reviewedPx), Size(barWidth, reviewedPx))
                            drawRect(colors.primary, Offset(left, top), Size(barWidth, height - reviewedPx))
                        }
                    }
                    if (chosenIndex == index) {
                        val height = if (day.total == 0) stubPx else max(minBarPx, heights[index].value * barsPx)
                        drawRoundRect(
                            color = colors.ink,
                            topLeft = Offset(left - 2.dp.toPx(), floor - height - 2.dp.toPx()),
                            size = Size(barWidth + 4.dp.toPx(), height + 4.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
            }

            // The bars are thin: one touch layer over the whole chart picks the nearest day, by a tap or by a finger dragged along.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(count, width) {
                        detectTapGestures { offset ->
                            val index = slotOf(offset.x)
                            chosenIndex = if (chosenIndex == index) null else index
                        }
                    }
                    .pointerInput(count, width) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> chosenIndex = slotOf(offset.x) },
                            onHorizontalDrag = { change, _ -> chosenIndex = slotOf(change.position.x) },
                        )
                    },
            )

            // For accessibility services: one element per day, with the date and the numbers.
            Row(Modifier.matchParentSize()) {
                days.forEachIndexed { index, day ->
                    val label = dayLabel(day, index == count - 1, copy)
                    val description = "$label: ${copy.activityValue(day.learned, day.reviewed)}"
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                contentDescription = description
                                selected = chosenIndex == index
                                onClick(label = null) {
                                    chosenIndex = if (chosenIndex == index) null else index
                                    true
                                }
                            },
                    )
                }
            }

            val chosen = chosenIndex
            if (chosen != null && chosen in days.indices && width > 0) {
                val day = days[chosen]
                val label = dayLabel(day, chosen == count - 1, copy)
                val barHeight = if (day.total == 0) stubPx else max(minBarPx, day.total.toFloat() / peak * barsPx)
                ActivityBubble(
                    text = "$label · ${copy.activityValue(day.learned, day.reviewed)}",
                    anchorX = width.toFloat() / count * (chosen + 0.5f),
                    anchorTop = headroomPx + barsPx - barHeight,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x2))
        DayLabels(days = days, copy = copy)
    }
}

/** The tone of the repeated ayahs, a little softer than the new ones so the stack reads as two parts. */
private fun reviewedTone(gold: Color): Color = gold.copy(alpha = 0.7f)

private fun dayLabel(day: ActivityDayUi, isToday: Boolean, copy: ProgressCopy): String {
    if (isToday) return copy.today
    val date = LocalDate.fromEpochDays(day.epochDay)
    return copy.date(date.dayOfMonth, date.monthNumber)
}

/**
 * Dates under the bars: the first day and every sixth after it, and "Today" under the last. The first one starts at the
 * left edge and the last ends at the right one, so neither is cut by the card.
 */
@Composable
private fun DayLabels(days: List<ActivityDayUi>, copy: ProgressCopy) {
    val colors = QhTheme.colors
    val count = days.size
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { index, day ->
            val isToday = index == count - 1
            val labelled = (index % LabelEvery == 0 && index <= count - LabelClearance) || isToday
            val align = when {
                index == 0 -> Alignment.Start
                isToday -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
            Box(Modifier.weight(1f)) {
                if (labelled) {
                    Text(
                        text = dayLabel(day, isToday, copy),
                        style = QhTheme.type.label.copy(fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium),
                        color = if (isToday) colors.primary else colors.inkSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(align = align, unbounded = true),
                    )
                }
            }
        }
    }
}

/** What the two tones mean: new ayahs, repeated ayahs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityLegend(copy: ProgressCopy) {
    val colors = QhTheme.colors
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
    ) {
        LegendItem(colors.primary, copy.activityNew)
        LegendItem(reviewedTone(colors.gold), copy.activityReviewed)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text = label, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
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
