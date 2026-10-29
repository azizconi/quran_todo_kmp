package tj.app.quran_todo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Fills the element with a placeholder color and sweeps a soft highlight across it every 1.15s.
 * Static under reduced motion. Apply to boxes of the final content's size; see [Skeleton].
 */
@Composable
fun Modifier.shimmer(shape: Shape = RectangleShape): Modifier {
    val colors = QhTheme.colors
    val base = colors.surfaceStrong
    val highlight = colors.surfaceMuted
    if (LocalReducedMotion.current) return background(base, shape)

    val phase = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_150, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-phase",
    )
    return drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(phase.value * size.width, 0f),
            end = Offset((phase.value + 1f) * size.width, size.height * 0.4f),
        )
        drawOutline(outline, brush)
    }
}

/** A shimmering placeholder block. Size it with [modifier]. */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = QhTheme.shapes.xs,
) {
    Box(modifier.shimmer(shape))
}

/** A placeholder for one list row: a round badge, two text lines and a short trailing block. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = QhSpacing.x3),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(Modifier.size(40.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            Skeleton(Modifier.fillMaxWidth(0.55f).height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.35f).height(11.dp))
        }
        Skeleton(Modifier.size(width = 44.dp, height = 18.dp))
    }
}
