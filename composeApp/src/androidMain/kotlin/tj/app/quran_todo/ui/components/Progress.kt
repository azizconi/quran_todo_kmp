package tj.app.quran_todo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Animates [progress] with `gentle()`. On first show the value grows from 0 (immediately set under
 * reduced motion). Returns a [State] so the drawing code can read it without recomposing.
 */
@Composable
internal fun animatedProgress(progress: Float, label: String): State<Float> {
    val reduced = LocalReducedMotion.current
    var target by remember { mutableFloatStateOf(if (reduced) progress else 0f) }
    LaunchedEffect(progress) { target = progress }
    return animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = QhMotion.gentle<Float>().orSnap(),
        label = label,
    )
}

/**
 * A circular progress indicator with round caps. [progress] (0..1) is animated and grows from 0 on
 * first show. [content] is centered inside (a number, an icon). Share it across screens with
 * `Modifier.sharedElementKey(SharedKeys.ProgressRing)`.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = QhTheme.colors.primary,
    trackColor: Color = QhTheme.colors.surfaceMuted,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val animated = animatedProgress(progress, "ring")
    Box(
        modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke),
            )
            val sweep = 360f * animated.value
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content?.invoke(this)
    }
}

/**
 * A horizontal progress bar with rounded ends. [progress] (0..1) is animated and grows from 0 on
 * first show. Use `height = 2.dp` for the reader's reading progress.
 */
@Composable
fun QhLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = QhTheme.colors.primary,
    trackColor: Color = QhTheme.colors.surfaceMuted,
) {
    val animated = animatedProgress(progress, "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(trackColor, cornerRadius = radius)
                val width = size.width * animated.value
                if (width > 0f) {
                    drawRoundRect(
                        color = color,
                        size = Size(width.coerceAtLeast(size.height), size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}
