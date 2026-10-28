package tj.app.quran_todo.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * How far a scrolling list has moved, as a 0..1 collapse fraction of the large top bar. Derived from
 * the list's own scroll position, so it is always accurate (also after programmatic scrolls).
 */
@Stable
class QhTopBarState internal constructor(
    private val scrollOffsetPx: () -> Float,
    private val collapseDistancePx: Float,
) {
    /** 0 = fully expanded (list at the top), 1 = collapsed to the compact bar. */
    val collapsedFraction: Float
        get() = (scrollOffsetPx() / collapseDistancePx).coerceIn(0f, 1f)
}

/** Collapse fraction driven by a [LazyListState]. The bar collapses over [collapseDistance] of scroll. */
@Composable
fun rememberQhTopBarState(listState: LazyListState, collapseDistance: Dp = 48.dp): QhTopBarState {
    val distance = with(LocalDensity.current) { collapseDistance.toPx() }
    return remember(listState, distance) {
        QhTopBarState(
            scrollOffsetPx = {
                if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE else listState.firstVisibleItemScrollOffset.toFloat()
            },
            collapseDistancePx = distance,
        )
    }
}

/** Collapse fraction driven by a [ScrollState] (a `verticalScroll` column). */
@Composable
fun rememberQhTopBarState(scrollState: ScrollState, collapseDistance: Dp = 48.dp): QhTopBarState {
    val distance = with(LocalDensity.current) { collapseDistance.toPx() }
    return remember(scrollState, distance) {
        QhTopBarState(scrollOffsetPx = { scrollState.value.toFloat() }, collapseDistancePx = distance)
    }
}

/**
 * A large top bar: the [title] in `display` collapses into a compact `title2` as the list scrolls
 * (see [rememberQhTopBarState]); on the way a hairline and a `bg` @ 96% backdrop appear. Draw it over
 * the list (e.g. in a `Box`) and give the list top content padding for the expanded height.
 *
 * @param overline a line above the large title in the top row (a greeting); it fades out within the
 * first 40% of the collapse and only then the compact title fades in, so they never overlap.
 * @param navigation leading slot, typically a back [QhIconButton].
 * @param actions trailing slot, typically [QhIconButton]s.
 */
@Composable
fun QhLargeTopBar(
    title: String,
    state: QhTopBarState,
    modifier: Modifier = Modifier,
    overline: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.statusBars,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val fraction = state.collapsedFraction
                if (fraction > 0f) {
                    drawRect(colors.bg.copy(alpha = 0.96f * fraction))
                    drawLine(
                        color = colors.hairline.copy(alpha = fraction),
                        start = Offset(0f, size.height - 0.5.dp.toPx()),
                        end = Offset(size.width, size.height - 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = if (navigation == null) QhSpacing.screen - 4.dp else QhSpacing.x1),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (overline != null) {
                    Text(
                        text = overline,
                        style = type.callout,
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Gone within the first 40% of the collapse, before the compact title shows up.
                        modifier = Modifier.graphicsLayer { alpha = overlineAlpha(state.collapsedFraction) },
                    )
                }
                Text(
                    text = title,
                    style = type.title2,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .graphicsLayer { alpha = compactTitleAlpha(state.collapsedFraction, hasOverline = overline != null) }
                        // The large title below is the heading; do not announce the title twice.
                        .clearAndSetSemantics { },
                )
            }
            actions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    val height = (placeable.height * (1f - state.collapsedFraction)).roundToInt()
                    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                }
                .clipToBounds()
                .graphicsLayer { alpha = (1f - state.collapsedFraction * 1.4f).coerceIn(0f, 1f) },
        ) {
            Text(
                text = title,
                style = type.display,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = QhSpacing.screen, end = QhSpacing.screen, bottom = QhSpacing.x2)
                    .semantics { heading() },
            )
        }
    }
}

/** Collapse fraction at which the overline is fully gone (and the compact title starts to appear). */
private const val OverlineFadeOutEnd = 0.4f

/** Opacity of the overline at a collapse [fraction]: gone within the first 40%. */
internal fun overlineAlpha(fraction: Float): Float = (1f - fraction / OverlineFadeOutEnd).coerceIn(0f, 1f)

/**
 * Opacity of the compact title at a collapse [fraction]. With an overline the title waits until the
 * overline is gone, so the two never overlap; without one it fades in with the collapse.
 */
internal fun compactTitleAlpha(fraction: Float, hasOverline: Boolean): Float =
    if (hasOverline) {
        ((fraction - OverlineFadeOutEnd) / (1f - OverlineFadeOutEnd)).coerceIn(0f, 1f)
    } else {
        fraction.coerceIn(0f, 1f)
    }
