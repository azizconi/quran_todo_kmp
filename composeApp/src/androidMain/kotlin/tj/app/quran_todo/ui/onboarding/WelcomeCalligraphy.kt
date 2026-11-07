package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tj.app.quran_todo.ui.components.Calligraphy
import tj.app.quran_todo.ui.components.CalligraphyText
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.theme.QhTheme

private const val GlowDelayMillis = 900L
private const val GlowMillis = 900
private const val WriteMillis = 1_600

/**
 * The welcome calligraphy: the bismillah ligature is written in gold from right to left over 1.6 s
 * (shrunk to fit narrow screens, at most 320dp wide) on a soft oval gold glow that fades in after 0.9 s,
 * and a single glint runs over it. Under reduced motion it simply appears complete and still.
 */
@Composable
internal fun WelcomeCalligraphy(modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current

    val glow = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            glow.snapTo(1f)
        } else {
            delay(GlowDelayMillis)
            glow.animateTo(1f, tween(GlowMillis, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .height(128.dp),
        contentAlignment = Alignment.Center,
    ) {
        val glowColor = colors.gold.copy(alpha = 0.16f)
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = glow.value }
                .drawBehind {
                    // A wide oval under the whole line of calligraphy: a round glow read as a stain.
                    val radius = size.height / 2f
                    scale(scaleX = size.width / size.height, scaleY = 1f, pivot = center) {
                        drawCircle(Brush.radialGradient(listOf(glowColor, Color.Transparent), center, radius), radius, center)
                    }
                },
        )
        CalligraphyText(Calligraphy.Bismillah, size = 44.dp, revealMillis = WriteMillis)
    }
}
