package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalReducedMotion

/**
 * Three bars dancing up and down: "this is playing". Static (three different heights) under reduced
 * motion. Local to the home screen: the design system has no equalizer.
 */
@Composable
internal fun Equalizer(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = List(BarCount) { index ->
        if (reduced) {
            remember { mutableFloatStateOf(StaticHeights[index]) }
        } else {
            transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(Periods[index], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 140),
                ),
                label = "equalizer-bar-$index",
            )
        }
    }
    Canvas(modifier.size(size)) {
        val barWidth = this.size.width * 0.2f
        val gap = (this.size.width - barWidth * BarCount) / (BarCount - 1)
        bars.forEachIndexed { index, height ->
            val h = this.size.height * height.value
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), this.size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private const val BarCount = 3
private val Periods = intArrayOf(520, 700, 610)
private val StaticHeights = floatArrayOf(0.55f, 1f, 0.7f)
