package tj.app.quran_todo.ui.listen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The thin ring of the echo silence ("Your turn"): full when the silence starts, empty when it ends, [remaining] and [total] in
 * whole seconds (a state of the audio session). It fills its parent and draws nothing while [active] is false. With reduced
 * motion the ring does not move smoothly: it steps once a second, and the number of seconds is the text next to it
 * ([YourTurnLine]).
 */
@Composable
internal fun EchoRing(remaining: Int, total: Int, active: Boolean, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val sweep = remember { Animatable(0f) }
    val visible = active && total > 0 && remaining > 0
    LaunchedEffect(remaining, total, visible, reduced) {
        if (!visible) {
            sweep.snapTo(0f)
            return@LaunchedEffect
        }
        if (remaining >= total) sweep.snapTo(1f)
        val target = (remaining - 1).coerceAtLeast(0).toFloat() / total
        if (reduced) sweep.snapTo(target) else sweep.animateTo(target, tween(1_000, easing = LinearEasing))
    }
    if (!visible && sweep.value <= 0f) return
    Canvas(modifier.fillMaxSize()) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        val diameter = size.minDimension - stroke
        val topLeft = Offset(inset + (size.width - size.minDimension) / 2f, inset + (size.height - size.minDimension) / 2f)
        drawArc(
            color = colors.primary.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(stroke),
        )
        drawArc(
            color = colors.primary,
            startAngle = -90f,
            sweepAngle = 360f * sweep.value,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * "Your turn · 4 s": the line that says it is the person's turn to repeat. It always takes its place (a fixed height), so the
 * controls under it do not jump when the silence starts and ends.
 */
@Composable
internal fun YourTurnLine(yourTurn: String, secondsShort: String, remaining: Int, active: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.height(24.dp), contentAlignment = Alignment.Center) {
        if (active && remaining > 0) {
            Text(
                text = "$yourTurn · $remaining $secondsShort",
                style = QhTheme.type.callout,
                color = QhTheme.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
