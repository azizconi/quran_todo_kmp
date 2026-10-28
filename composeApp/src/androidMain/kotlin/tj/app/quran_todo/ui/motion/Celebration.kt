package tj.app.quran_todo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.theme.QhTheme

private const val ParticleCount = 14
private const val BurstMillis = 700

/**
 * A small celebration: 14 gold and emerald particles fly out radially and fade in 700ms. Use it for
 * "memorized" and at the end of a memorization session, no more often than it really happens.
 *
 * Draws centered in its bounds and may overflow them, so give it a small box around the thing that
 * is being celebrated (e.g. `Modifier.matchParentSize()` inside a badge). It never intercepts touches.
 *
 * Fires every time [trigger] changes after the first composition (bump a counter to fire it). Plays
 * [haptic] together with the burst; under reduced motion only the haptic remains.
 */
@Composable
fun CelebrationBurst(
    trigger: Int,
    modifier: Modifier = Modifier.fillMaxSize(),
    radius: Dp = 72.dp,
    haptic: HapticKind? = HapticKind.Success,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHaptics.current
    val progress = remember { Animatable(0f) }
    var lastTrigger by remember { mutableIntStateOf(trigger) }
    var seed by remember { mutableIntStateOf(0) }

    LaunchedEffect(trigger) {
        if (trigger == lastTrigger) return@LaunchedEffect
        lastTrigger = trigger
        haptic?.let(haptics::perform)
        if (reduced) return@LaunchedEffect
        seed = trigger
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BurstMillis, easing = QhMotion.EmphasizedDecelerate))
    }

    val particles = remember(seed) {
        val random = Random(seed)
        List(ParticleCount) { index ->
            Particle(
                angle = (index / ParticleCount.toFloat()) * 2f * Math.PI.toFloat() + random.nextFloat() * 0.35f,
                distance = 0.6f + random.nextFloat() * 0.4f,
                size = 2.5f + random.nextFloat() * 2.5f,
                gold = index % 2 == 0,
            )
        }
    }

    Canvas(modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val maxDistance = radius.toPx()
        val alpha = (1f - t).let { it * it }
        particles.forEach { particle ->
            val distance = maxDistance * particle.distance * t
            val center = Offset(
                x = size.width / 2f + cos(particle.angle) * distance,
                y = size.height / 2f + sin(particle.angle) * distance,
            )
            drawCircle(
                color = (if (particle.gold) colors.gold else colors.learned).copy(alpha = alpha),
                radius = particle.size.dp.toPx() * (1f - 0.4f * t),
                center = center,
            )
        }
    }
}

private class Particle(val angle: Float, val distance: Float, val size: Float, val gold: Boolean)
