package tj.app.quran_todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

private val ItemHeight = 40.dp
private const val VisibleRows = 5

/**
 * A wheel for picking an ayah number in [minimum]..[maximum]: five rows, the chosen number large
 * in the middle band, the others shrinking and fading toward the edges. Snaps to a number, plays the
 * step haptic on every change and accepts taps on a row. [onValueChange] is called as the wheel
 * settles on numbers; change [value] (or the bounds) from outside and the wheel scrolls to it.
 */
@Composable
fun AyahWheel(
    label: String,
    minimum: Int,
    maximum: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("NAME_SHADOWING")
    val maximum = maxOf(maximum, minimum)
    val colors = QhTheme.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val count = maximum - minimum + 1
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (value - minimum).coerceIn(0, count - 1))
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value)
    // True while the wheel is scrolled by us (to reflect an outside change), so we do not echo it back.
    val programmatic = remember { booleanArrayOf(false) }

    LaunchedEffect(state, minimum, maximum) {
        // Only changes that happen while a scroll is running count as the user's choice; the index also
        // moves when the list itself changes (bounds), and that must not be echoed as a new value.
        snapshotFlow { state.centredIndex()?.let { it to state.isScrollInProgress } }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (index, scrolling) ->
                if (!scrolling || programmatic[0]) return@collect
                val number = (minimum + index).coerceIn(minimum, maximum)
                if (number != latestValue) {
                    haptics.perform(HapticKind.Step)
                    latestOnValueChange(number)
                }
            }
    }
    LaunchedEffect(value, minimum, maximum) {
        // Let a running drag or fling settle first; usually the wheel is then already on the value.
        snapshotFlow { state.isScrollInProgress }.first { !it }
        val target = (value - minimum).coerceIn(0, count - 1)
        if (state.centredIndex() != target) {
            programmatic[0] = true
            try {
                state.animateScrollToItem(target)
            } finally {
                programmatic[0] = false
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = QhTheme.type.callout, color = colors.inkSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(ItemHeight * VisibleRows),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .background(colors.surfaceMuted, QhTheme.shapes.sm),
            )
            LazyColumn(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
                contentPadding = PaddingValues(vertical = ItemHeight * (VisibleRows / 2)),
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges()
                    .semantics {
                        contentDescription = label
                        stateDescription = value.toString()
                    },
            ) {
                items(count, key = { minimum + it }) { index ->
                    WheelRow(
                        number = minimum + index,
                        distance = { state.distanceFromCentre(index) },
                        onClick = { scope.launch { state.animateScrollToItem(index) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun WheelRow(number: Int, distance: () -> Float, onClick: () -> Unit) {
    val colors = QhTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .pressable(onClick = onClick, haptic = null, scale = 0.94f, highlight = false),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = number.toString(),
            style = QhTheme.type.headline.tabular(),
            color = { lerp(colors.primary, colors.inkTertiary, min(distance(), 1f)) },
            modifier = Modifier.graphicsLayer {
                val d = distance().coerceIn(0f, 2.5f)
                val scale = 1f - 0.2f * min(d, 1f) - 0.1f * (min(d, 2f) - min(d, 1f))
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.3f * d
            },
        )
    }
}

/** Index of the row closest to the middle of the viewport, or null while nothing is laid out. */
private fun LazyListState.centredIndex(): Int? {
    val layout = layoutInfo
    val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
}

/** Distance of row [index] from the middle of the viewport, in rows; 3 when it is not on screen. */
private fun LazyListState.distanceFromCentre(index: Int): Float {
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return 3f
    val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return abs(item.offset + item.size / 2f - centre) / item.size
}

/** Fades the content out toward the top and bottom edge with a gradient mask (black is only the mask's opaque end). */
private fun Modifier.fadingEdges(fraction: Float = 0.3f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                fraction to Color.Black,
                1f - fraction to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
