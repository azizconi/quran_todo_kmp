package tj.app.quran_todo.ui.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSearchField
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.home.HomeStateBuilder
import tj.app.quran_todo.ui.home.SurahRowUi
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.i18n.ayahCountLabel
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import kotlin.math.roundToInt

private val RowHeight = 56.dp

/**
 * The queue of surahs of the plan. A preset is read-only (and long lists are folded to a few rows around the
 * surah being learned); the person's own list ([editable]) has a drag handle and a remove button on every row.
 * Dragging moves a row with the finger while the others make room; [onMove] is called once, when the finger
 * lifts, with the old and the new position. TalkBack users move a row with the "Move up / down" actions.
 */
@Composable
internal fun SurahQueue(
    rows: List<QueueRowUi>,
    editable: Boolean,
    copy: PlanCopy,
    expanded: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (surah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The dropped row stays where it was dropped until the new order comes back from the plan (a frame or two).
    var dropped by remember { mutableStateOf<List<QueueRowUi>?>(null) }
    LaunchedEffect(rows) { dropped = null }
    val list = dropped ?: rows
    // The gesture handlers of the rows that stay are not restarted: they must read the current list and callback.
    val latestList by rememberUpdatedState(list)
    val latestOnMove by rememberUpdatedState(onMove)
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val rowHeightPx = with(density) { RowHeight.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var lastSlot by remember { mutableIntStateOf(-1) }

    // Read through state: the gesture handlers of the rows that stay are not restarted when a row is added.
    val rowCount by rememberUpdatedState(list.size)

    fun slotFor(from: Int, offset: Float): Int =
        (from + (offset / rowHeightPx).roundToInt()).coerceIn(0, (rowCount - 1).coerceAtLeast(0))

    // A long preset is folded: a window of rows that starts just above the surah being learned.
    val currentIndex = list.indexOfFirst { it.current }.coerceAtLeast(0)
    val windowStart = if (expanded || editable || list.size <= FoldedRows + 1) 0 else (currentIndex - 1).coerceIn(0, list.size - FoldedRows)
    val shown = if (expanded || editable || list.size <= FoldedRows + 1) list else list.subList(windowStart, windowStart + FoldedRows)

    Column(modifier.fillMaxWidth()) {
        shown.forEachIndexed { shownIndex, row ->
            val index = windowStart + shownIndex
            key(row.surah) {
                // Read through derived state: only a change of the slot recomposes the rows, not every pixel of the drag.
                val slot by remember { derivedStateOf { if (dragging >= 0) slotFor(dragging, dragOffset) else -1 } }
                val shift = when {
                    dragging < 0 || index == dragging -> 0f
                    dragging < index && index <= slot -> -rowHeightPx
                    slot <= index && index < dragging -> rowHeightPx
                    else -> 0f
                }
                val animatedShift = animateFloatAsState(
                    targetValue = shift,
                    // While no row is dragged (the drop) the rows must not slide back: they were re-ordered already.
                    animationSpec = if (dragging >= 0 && !reduced) QhMotion.snappy<Float>() else snap(),
                    label = "queue-shift",
                )
                val currentIndexOfRow by rememberUpdatedState(index)
                val isDragged = dragging == index
                QueueRow(
                    row = row,
                    copy = copy,
                    editable = editable,
                    isDragged = isDragged,
                    // Read in the graphics layer: the dragged row follows the finger without recomposing.
                    offsetY = { if (isDragged) dragOffset else animatedShift.value },
                    onMoveUp = { if (index > 0) onMove(index, index - 1) },
                    onMoveDown = { if (index < list.lastIndex) onMove(index, index + 1) },
                    onRemove = { onRemove(row.surah) },
                    dragModifier = Modifier.pointerInput(row.surah) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = currentIndexOfRow
                                dragOffset = 0f
                                lastSlot = currentIndexOfRow
                                haptics.perform(HapticKind.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val target = slotFor(dragging, dragOffset)
                                if (target != lastSlot) {
                                    lastSlot = target
                                    haptics.perform(HapticKind.Step)
                                }
                            },
                            onDragEnd = {
                                val from = dragging
                                val to = slotFor(from, dragOffset)
                                dragging = -1
                                dragOffset = 0f
                                if (from >= 0 && to != from) {
                                    haptics.perform(HapticKind.Tick)
                                    dropped = latestList.toMutableList().apply { add(to, removeAt(from)) }
                                    latestOnMove(from, to)
                                }
                            },
                            onDragCancel = {
                                dragging = -1
                                dragOffset = 0f
                            },
                        )
                    },
                )
            }
        }
    }
}

private const val FoldedRows = 5

/** One row of the queue: [handle] badge name (ayahs) [state] [remove]. Fixed height, so dragging can count rows. */
@Composable
private fun QueueRow(
    row: QueueRowUi,
    copy: PlanCopy,
    editable: Boolean,
    isDragged: Boolean,
    offsetY: () -> Float,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val subtitle = ayahCountLabel(row.ayahCount, copy.language)
    val stateLabel = when {
        row.learned -> copy.learnedMark
        row.current -> copy.currentMark
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = offsetY()
                if (isDragged) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 8.dp.toPx()
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    clip = true
                }
            }
            .background(if (isDragged) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(row.name).append(", ").append(subtitle)
                    stateLabel?.let { append(", ").append(it) }
                }
                if (editable) {
                    customActions = listOf(
                        CustomAccessibilityAction(copy.moveUp) { onMoveUp(); true },
                        CustomAccessibilityAction(copy.moveDown) { onMoveDown(); true },
                        CustomAccessibilityAction(copy.remove) { onRemove(); true },
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        if (editable) {
            Box(
                dragModifier
                    .size(width = 36.dp, height = RowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = null,
                    tint = colors.inkTertiary,
                    modifier = Modifier.size(QhSpacing.iconBar),
                )
            }
        }
        SurahNumberBadge(
            number = row.surah,
            status = if (row.learned) SurahTodoStatus.LEARNED else null,
            learnedFraction = if (row.learned) 1f else 0f,
            size = 32.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = type.title2,
                color = if (row.learned) colors.inkTertiary else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = type.callout,
                color = if (row.learned) colors.inkTertiary else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            row.learned -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(QhSpacing.iconBar),
            )

            row.current -> Text(
                text = copy.currentMark,
                style = type.label,
                color = colors.gold,
                maxLines = 1,
                modifier = Modifier
                    .clip(QhTheme.shapes.full)
                    .background(colors.goldContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (editable) {
            QhIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "${copy.remove}: ${row.name}",
                onClick = onRemove,
                filled = false,
                tint = colors.inkSecondary,
                iconSize = 20.dp,
            )
        }
    }
}

/**
 * "Add a surah": a search field (by name or number, [tj.app.quran_todo.ui.home.SurahSearch]) over the list
 * of all surahs. A tap adds the surah to the end of the queue and keeps the sheet open, so several can be
 * added; the ones in the queue show a check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSurahSheet(
    catalog: List<SurahRowUi>,
    inQueue: Set<Int>,
    copy: PlanCopy,
    onAdd: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    var query by remember { mutableStateOf("") }
    val index = remember(catalog) { HomeStateBuilder.searchIndex(catalog) }
    val byNumber = remember(catalog) { catalog.associateBy { it.number } }
    val results = remember(index, byNumber, query) { index.search(query).mapNotNull { byNumber[it] } }

    QhSheet(onDismiss = onDismiss) {
        Column(Modifier.imePadding()) {
            Text(copy.addTitle, style = type.title1, color = colors.ink)
            Spacer(Modifier.height(QhSpacing.x3))
            QhSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = copy.searchPlaceholder,
                clearContentDescription = copy.clearSearch,
            )
            Spacer(Modifier.height(QhSpacing.x2))
            if (results.isEmpty()) {
                EmptyState(title = copy.noResults, modifier = Modifier.height(SheetListHeight))
            } else {
                LazyColumn(Modifier.height(SheetListHeight)) {
                    items(results, key = { it.number }) { row ->
                        val added = row.number in inQueue
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(RowHeight)
                                .pressable(onClick = { if (!added) onAdd(row.number) }, enabled = !added)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = if (added) "${row.name}, ${copy.added}" else row.name
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
                        ) {
                            SurahNumberBadge(row.number, row.status, row.learnedFraction, size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(row.name, style = type.title2, color = if (added) colors.inkTertiary else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    ayahCountLabel(row.ayahCount, copy.language),
                                    style = type.callout,
                                    color = colors.inkSecondary,
                                    maxLines = 1,
                                )
                            }
                            if (added) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.learned, modifier = Modifier.size(QhSpacing.iconBar))
                            } else {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(QhSpacing.iconBar))
                            }
                            Spacer(Modifier.width(QhSpacing.x1))
                        }
                    }
                }
            }
        }
    }
}

private val SheetListHeight = 360.dp

private const val DayMillis = 86_400_000L

/** The date picker for "finish by": only days after today can be chosen. [onPick] gets an epoch day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetDateDialog(
    initialDay: Int,
    today: Int,
    copy: PlanCopy,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay.toLong() * DayMillis,
        selectableDates = object : SelectableDates {
            // The picker works in UTC midnights: the day number is the floor of the division.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = Math.floorDiv(utcTimeMillis, DayMillis) > today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Math.floorDiv(it, DayMillis).toInt()) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(copy.ok, style = QhTheme.type.title2, color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(copy.cancel, style = QhTheme.type.title2, color = colors.primary) }
        },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = colors.surface),
    ) {
        DatePicker(state = state)
    }
}
