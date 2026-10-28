package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.AnimatedEightPointStar
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.theme.QhTheme

private const val OuterDrawMillis = 1_200
private const val InnerDrawMillis = 1_000
private const val InnerDelayMillis = 350L
private const val GlowDelayMillis = 900L
private const val GlowMillis = 900

/**
 * The welcome rosette: a gold eight-pointed star draws itself in 1.2 s, a smaller emerald one draws
 * inside it, a soft glow fades in, and then the whole figure slowly breathes (scale 1 to 1.03 over
 * 4 s). Under reduced motion it simply appears complete and still.
 */
@Composable
internal fun WelcomeRosette(modifier: Modifier = Modifier, size: Dp = 128.dp) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current

    var showInner by remember { mutableStateOf(reduced) }
    var breathing by remember { mutableStateOf(false) }
    val glow = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(reduced) {
        if (reduced) {
            showInner = true
            glow.snapTo(1f)
        } else {
            coroutineScope {
                launch {
                    delay(InnerDelayMillis)
                    showInner = true
                }
                launch {
                    delay(GlowDelayMillis)
                    glow.animateTo(1f, tween(GlowMillis, easing = FastOutSlowInEasing))
                }
                launch {
                    // The outline is done; from here the figure breathes.
                    delay(OuterDrawMillis.toLong())
                    breathing = true
                }
            }
        }
    }

    val pulse by rememberInfiniteTransition(label = "rosette-breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rosette-breath-scale",
    )

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val scale = if (breathing && !reduced) pulse else 1f
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glow.value }
                .background(
                    Brush.radialGradient(listOf(colors.gold.copy(alpha = 0.2f), Color.Transparent)),
                    CircleShape,
                ),
        )
        AnimatedEightPointStar(
            size = size,
            color = colors.gold,
            strokeWidth = 2.dp,
            drawMillis = OuterDrawMillis,
        )
        if (showInner) {
            AnimatedEightPointStar(
                size = size * 0.52f,
                color = colors.primary,
                strokeWidth = 2.dp,
                drawMillis = InnerDrawMillis,
            )
        }
    }
}
