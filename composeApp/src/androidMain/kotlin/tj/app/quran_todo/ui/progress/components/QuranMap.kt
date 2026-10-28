package tj.app.quran_todo.ui.progress.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.progress.MapCellUi
import tj.app.quran_todo.ui.progress.MapState
import tj.app.quran_todo.ui.progress.waveIntensity
import tj.app.quran_todo.ui.progress.waveTotalMillis
import tj.app.quran_todo.ui.theme.QhColors
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private const val Columns = 10
private val CellGap = 4.dp
private val BubbleMaxWidth = 300.dp
private val ArrowHeight = 7.dp
private val ArrowHalfWidth = 7.dp

/** A cell with learned ayahs always shows at least this much fill, so the first ayahs are visible. */
private const val MinVisibleFill = 0.12f

/** How long a tapped cell's bubble stays when nothing else happens. */
private const val BubbleLifetimeMillis = 7_000L

/** The map is "on screen" for the wave once this much of it is visible. */
private const val WaveVisibleShare = 0.3f
private const val WaveStartDelayMillis = 450L

/**
 * "Quran map": 114 small squares, ten to a row. A surah that is not started is a muted square, one in
 * progress is filled with gold from the bottom up by its share of memorized ayahs, a memorized one is
 * solid emerald with a small star. When the map first comes on screen a golden glint runs over the
 * cells in order (not under reduced motion). A tap opens a bubble above the cell with the name, the
 * ayah count and "Open".
 */
@Composable
internal fun QuranMapSection(
    cells: List<MapCellUi>,
    copy: ProgressCopy,
    scrollState: ScrollState,
    onOpen: (surah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.mapTitle)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.mapHint, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        QhCard(contentPadding = PaddingValues(QhSpacing.card)) {
            QuranMapGrid(cells = cells, copy = copy, scrollState = scrollState, onOpen = onOpen)
            Spacer(Modifier.height(QhSpacing.x4))
            Legend(copy)
        }
    }
}

@Composable
private fun QuranMapGrid(
    cells: List<MapCellUi>,
    copy: ProgressCopy,
    scrollState: ScrollState,
    onOpen: (surah: Int) -> Unit,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val palette = remember(colors) { CellPalette.of(colors) }
    val density = LocalDensity.current

    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var gridWidth by remember { mutableIntStateOf(0) }

    // -- The wave ----------------------------------------------------------------------------------
    val hasLearned = remember(cells) { cells.any { it.state == MapState.Learned } }
    var wavePlayed by rememberSaveable { mutableStateOf(false) }
    val clock = remember { Animatable(0f) }
    val gridCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val totalMillis = waveTotalMillis(cells.size)
    LaunchedEffect(hasLearned, reduced) {
        if (reduced || !hasLearned || wavePlayed) return@LaunchedEffect
        // Wait until the person can see the map: it may start below the fold.
        while (!gridCoordinates[0].isMostlyVisible()) delay(120)
        // Let the sections settle in (and a shared transition finish) before the glint starts.
        delay(WaveStartDelayMillis)
        wavePlayed = true
        clock.snapTo(0f)
        clock.animateTo(totalMillis, tween(totalMillis.roundToInt(), easing = LinearEasing))
    }
    // Before the wave starts (and while it waits) nothing glints; afterwards it stays at its end.
    val waveTime: () -> Float = remember(reduced, totalMillis) { { if (reduced) totalMillis else clock.value } }

    // -- The bubble --------------------------------------------------------------------------------
    BackHandler(enabled = selected != null) { selected = null }
    LaunchedEffect(selected) {
        if (selected == null) return@LaunchedEffect
        // The bubble goes away by itself, and as soon as the page starts to scroll.
        launch { delay(BubbleLifetimeMillis); selected = null }
        snapshotFlow { scrollState.isScrollInProgress }.first { it }
        selected = null
    }

    val gapPx = with(density) { CellGap.toPx() }
    val cellPx = if (gridWidth > 0) (gridWidth - gapPx * (Columns - 1)) / Columns else 0f
    val rows = (cells.size + Columns - 1) / Columns

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { gridWidth = it.width }
            .onGloballyPositioned { gridCoordinates[0] = it },
    ) {
        // Taps between the cells put the bubble away.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { selected = null } },
        )
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    for (column in 0 until Columns) {
                        val index = row * Columns + column
                        val cell = cells.getOrNull(index)
                        if (cell == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            MapCell(
                                cell = cell,
                                index = index,
                                palette = palette,
                                copy = copy,
                                isSelected = selected == cell.number,
                                waveTime = waveTime,
                                onClick = { selected = if (selected == cell.number) null else cell.number },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        val shown = selected?.let { number -> cells.firstOrNull { it.number == number } }
        if (shown != null && cellPx > 0f) {
            val index = shown.number - 1
            val row = index / Columns
            val column = index % Columns
            val left = column * (cellPx + gapPx)
            val top = row * (cellPx + gapPx)
            // A bubble is about 64dp high (more with a big font): rows too near the top hang it below.
            val roomNeeded = with(density) { (BubbleEstimatedHeight * density.fontScale + ArrowHeight).toPx() }
            MapBubble(
                cell = shown,
                copy = copy,
                anchorCenterX = left + cellPx / 2f,
                anchorTop = top,
                anchorBottom = top + cellPx,
                above = top >= roomNeeded,
                palette = palette,
                onOpen = { onOpen(shown.number) },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private val BubbleEstimatedHeight = 64.dp

private fun LayoutCoordinates?.isMostlyVisible(): Boolean {
    val coordinates = this ?: return false
    if (!coordinates.isAttached || coordinates.size.height <= 0) return false
    val visible = coordinates.boundsInWindow().height.coerceAtLeast(0f)
    return visible / coordinates.size.height >= WaveVisibleShare
}

// -- Cell ----------------------------------------------------------------------------------------

/** Colors the cells draw with, resolved once per theme. */
private class CellPalette(
    val empty: Color,
    val emptyEdge: Color,
    val learningBackground: Color,
    val learning: Color,
    val learned: Color,
    val onLearned: Color,
    val glint: Color,
    val selection: Color,
    val bubble: Color,
    val onBubble: Color,
) {
    companion object {
        fun of(c: QhColors) = CellPalette(
            empty = c.surfaceMuted,
            emptyEdge = c.hairline,
            learningBackground = c.learningContainer,
            learning = c.learning,
            learned = c.learned,
            onLearned = c.onLearned,
            glint = c.gold,
            selection = c.ink,
            bubble = c.ink,
            onBubble = c.bg,
        )
    }
}

@Composable
private fun MapCell(
    cell: MapCellUi,
    index: Int,
    palette: CellPalette,
    copy: ProgressCopy,
    isSelected: Boolean,
    waveTime: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = QhTheme.shapes.xs
    val status = copy.statusOf(cell.state)
    val description = "${cell.number}, ${cell.name}"
    val state = "$status, ${copy.ayahsOf(cell.learnedAyahs, cell.ayahCount)}"
    Box(
        modifier
            .aspectRatio(1f)
            .pressable(onClick = onClick, shape = shape, scale = 0.84f, highlight = false)
            .semantics {
                contentDescription = description
                stateDescription = state
                this.selected = isSelected
            }
            .cellDrawing(cell = cell, index = index, palette = palette, isSelected = isSelected, waveTime = waveTime),
    )
}

private fun ProgressCopy.statusOf(state: MapState): String = when (state) {
    MapState.NotStarted -> statusNotStarted
    MapState.Learning -> statusLearning
    MapState.Learned -> statusLearned
}

private fun Modifier.cellDrawing(
    cell: MapCellUi,
    index: Int,
    palette: CellPalette,
    isSelected: Boolean,
    waveTime: () -> Float,
): Modifier = drawWithCache {
    val corner = CornerRadius(6.dp.toPx())
    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
    val fill = if (cell.fraction > 0f) cell.fraction.coerceAtLeast(MinVisibleFill) else 0f
    onDrawBehind {
        when (cell.state) {
            MapState.NotStarted -> {
                drawRoundRect(palette.empty, cornerRadius = corner)
                // The muted tone alone nearly vanishes on a dark card: a hairline keeps the square.
                drawRoundRect(palette.emptyEdge, cornerRadius = corner, style = Stroke(1.dp.toPx()))
            }

            MapState.Learning -> {
                drawRoundRect(palette.learningBackground, cornerRadius = corner)
                if (fill > 0f) {
                    val height = size.height * fill
                    clipPath(clip) {
                        drawRect(
                            color = palette.learning,
                            topLeft = Offset(0f, size.height - height),
                            size = Size(size.width, height),
                        )
                    }
                }
            }

            MapState.Learned -> {
                // Reading the clock here keeps the glint a draw-only update: nothing recomposes.
                val glint = waveIntensity(waveTime(), index)
                val pop = 1f + 0.16f * glint
                scale(pop, pop, pivot = center) {
                    drawRoundRect(palette.learned, cornerRadius = corner)
                    drawStar(palette.onLearned.copy(alpha = 0.72f))
                    if (glint > 0f) drawRoundRect(palette.glint, cornerRadius = corner, alpha = 0.92f * glint)
                }
            }
        }
        if (isSelected) {
            val inset = 1.dp.toPx()
            drawRoundRect(
                color = palette.selection,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = corner,
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

/** A small eight-pointed star (two squares) in the middle of a memorized cell: status is not only a color. */
private fun DrawScope.drawStar(color: Color) {
    val half = size.minDimension * 0.2f
    val stroke = Stroke(width = 1.dp.toPx())
    val topLeft = Offset(center.x - half, center.y - half)
    val square = Size(half * 2, half * 2)
    drawRect(color, topLeft, square, style = stroke)
    rotate(45f, pivot = center) { drawRect(color, topLeft, square, style = stroke) }
}

// -- Bubble --------------------------------------------------------------------------------------

/**
 * The bubble over a cell. It fills the grid ([modifier] = `matchParentSize`) so that it can sit
 * anywhere inside it; the arrow is drawn here, pointing at the cell, wherever the bubble ends up.
 */
@Composable
private fun MapBubble(
    cell: MapCellUi,
    copy: ProgressCopy,
    anchorCenterX: Float,
    anchorTop: Float,
    anchorBottom: Float,
    above: Boolean,
    palette: CellPalette,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val appear = remember(cell.number) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(cell.number, reduced) {
        if (!reduced) appear.animateTo(1f, QhMotion.snappy())
    }
    val arrowHeight = with(LocalDensity.current) { ArrowHeight.toPx() }
    val arrowHalf = with(LocalDensity.current) { ArrowHalfWidth.toPx() }
    Box(
        modifier
            .drawBehind {
                val path = Path().apply {
                    if (above) {
                        moveTo(anchorCenterX - arrowHalf, anchorTop - arrowHeight - 0.5f)
                        lineTo(anchorCenterX + arrowHalf, anchorTop - arrowHeight - 0.5f)
                        lineTo(anchorCenterX, anchorTop - 1f)
                    } else {
                        moveTo(anchorCenterX - arrowHalf, anchorBottom + arrowHeight + 0.5f)
                        lineTo(anchorCenterX + arrowHalf, anchorBottom + arrowHeight + 0.5f)
                        lineTo(anchorCenterX, anchorBottom + 1f)
                    }
                    close()
                }
                drawPath(path, palette.bubble, alpha = appear.value)
            }
            .layout { measurable, constraints ->
                val maxWidth = min(constraints.maxWidth, BubbleMaxWidth.roundToPx())
                val placeable = measurable.measure(Constraints(maxWidth = maxWidth))
                val x = (anchorCenterX - placeable.width / 2f)
                    .coerceIn(0f, (constraints.maxWidth - placeable.width).coerceAtLeast(0).toFloat())
                val gap = ArrowHeight.roundToPx()
                val y = if (above) anchorTop - placeable.height - gap else anchorBottom + gap
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
                }
            },
    ) {
        BubbleBody(
            cell = cell,
            copy = copy,
            palette = palette,
            onOpen = onOpen,
            modifier = Modifier.graphicsLayer {
                alpha = appear.value
                val pop = 0.92f + 0.08f * appear.value
                scaleX = pop
                scaleY = pop
                transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
            },
        )
    }
}

@Composable
private fun BubbleBody(
    cell: MapCellUi,
    copy: ProgressCopy,
    palette: CellPalette,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = QhTheme.type
    val shape = QhTheme.shapes.md
    val status = copy.statusOf(cell.state)
    val progress = copy.ayahsOf(cell.learnedAyahs, cell.ayahCount)
    Row(
        modifier
            .widthIn(max = BubbleMaxWidth)
            .clip(shape)
            .background(palette.bubble)
            .padding(start = QhSpacing.x4, top = QhSpacing.x2, bottom = QhSpacing.x2, end = QhSpacing.x2)
            // A tap on the bubble itself must not fall through to the cell under it.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${cell.number} · ${cell.name}",
                style = type.title2,
                color = palette.onBubble,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$status · $progress",
                style = type.callout,
                color = palette.onBubble.copy(alpha = 0.8f),
            )
        }
        val pill = QhTheme.shapes.full
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 36.dp)
                .pressable(onClick = onOpen, shape = pill, highlight = false, role = Role.Button)
                .clip(pill)
                .background(palette.onBubble)
                .padding(horizontal = QhSpacing.x4),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = copy.open, style = type.label, color = palette.bubble, maxLines = 1)
        }
    }
}

// -- Legend --------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(copy: ProgressCopy) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        LegendItem(MapState.NotStarted, copy.statusNotStarted)
        LegendItem(MapState.Learning, copy.statusLearning)
        LegendItem(MapState.Learned, copy.statusLearned)
    }
}

@Composable
private fun LegendItem(state: MapState, label: String) {
    val colors = QhTheme.colors
    Row(
        Modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val palette = remember(colors) { CellPalette.of(colors) }
        Box(
            Modifier
                .size(16.dp)
                .cellDrawing(
                    cell = MapCellUi(0, "", 2, if (state == MapState.Learning) 1 else if (state == MapState.Learned) 2 else 0, state),
                    index = 0,
                    palette = palette,
                    isSelected = false,
                    waveTime = { 0f },
                ),
        )
        Text(text = label, style = QhTheme.type.callout, color = colors.inkSecondary)
    }
}
