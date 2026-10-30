package tj.app.quran_todo.ui.onboarding

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.Medallion
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.reader.hideRubSign
import tj.app.quran_todo.ui.settings.PreviewAyah
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

// The three illustrations of the story slides. Everything is drawn in Compose and Canvas: calligraphy text, circles, rounded bars.
// There are no pointed radial shapes anywhere (the owner forbids stars): rosettes are the app's round-petalled AyahRosette, the
// ring is a plain circle. Under Reduce Motion each one stands still in its last frame.

/** The height of the card every illustration sits in: the three slides share it, so the text under them does not jump. */
private val ArtHeight = 240.dp

/** Hides everything left of a soft edge that sweeps from the right to the left as [progress] goes 0..1: ink laid down by a pen. */
internal fun Modifier.inkReveal(progress: () -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = progress()
        if (p < 1f) {
            val feather = size.width * 0.2f
            val edge = lerp(size.width + feather, -feather, p)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = edge, endX = edge + feather),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** The paper the illustrations sit on: the reader's background in a hairline frame. */
@Composable
private fun ArtCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = ArtHeight)
            .clip(shape)
            .background(colors.readerBg)
            .border(1.dp, colors.hairline, shape)
            // Decorative: the text under the card carries the meaning.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// -- Slide 1: read beautifully -----------------------------------------------------------------------

private val RosetteSize = 30.dp
private const val LineGapMillis = 720L
private const val LineWriteMillis = 900

/**
 * A miniature of the reader: the first ayahs of Al-Fatihah are written line after line from right to left, and the round-petalled
 * rosette that closes each ayah blooms on a spring (it opens from nothing, turning a little).
 */
@Composable
internal fun ReadArt(caption: String, ayahs: List<PreviewAyah>, fontStyle: ReadingFontStyle) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val family = getQuranFontFamily(fontStyle)
    // The text of an illustration keeps its size in dp: the system font scale does not grow it (the texts under it do).
    val ayahSize = with(LocalDensity.current) { 24.dp.toSp() }
    val lines = remember(ayahs.size) { List(ayahs.size) { Animatable(if (reduced) 1f else 0f) } }
    val blooms = remember(ayahs.size) { List(ayahs.size) { Animatable(if (reduced) 1f else 0f) } }
    LaunchedEffect(reduced, ayahs.size) {
        if (reduced) {
            lines.forEach { it.snapTo(1f) }
            blooms.forEach { it.snapTo(1f) }
            return@LaunchedEffect
        }
        delay(300)
        ayahs.indices.forEach { index ->
            launch {
                delay(index * LineGapMillis)
                lines[index].animateTo(1f, tween(LineWriteMillis, easing = FastOutSlowInEasing))
                blooms[index].animateTo(1f, QhMotion.bouncy())
            }
        }
    }
    ArtCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(QhSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalAlignment = Alignment.CenterVertically) {
                Medallion(size = 14.dp, strokeWidth = 1.dp)
                Text(text = caption, style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 1)
            }
            // Arabic runs right to left: the text starts at the right edge and its ayah marker closes it on the left.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ayahs.forEachIndexed { index, ayah ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = hideRubSign(ayah.text),
                            style = QhTheme.type.quranBody(family, ayahSize),
                            color = colors.ink,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .inkReveal { lines[index].value },
                        )
                        Box(
                            Modifier.graphicsLayer {
                                val bloom = blooms[index].value
                                scaleX = bloom
                                scaleY = bloom
                                alpha = bloom.coerceIn(0f, 1f)
                                rotationZ = (1f - bloom) * -60f
                            },
                        ) { AyahRosette(number = ayah.number, size = RosetteSize) }
                    }
                }
            }
        }
    }
}

// -- Slide 2: listen and repeat ----------------------------------------------------------------------

private val PlayButtonSize = 76.dp
private val RingSize = 152.dp
private const val RingDots = 5
private const val ArcSpanDegrees = 110f
private const val WaveBars = 27

/**
 * A play button that pulses, a ring around it that a gold arc runs round (the five dots are the ayahs of the passage and light up as
 * the arc passes), the label "1–5 · 3×" of the repeat, and a row of sound-wave bars.
 */
@Composable
internal fun ListenArt(repeatLabel: String) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val sweep = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        launch { sweep.animateTo(360f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart)) }
        launch { pulse.animateTo(1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart)) }
        launch { phase.animateTo((2 * PI).toFloat(), infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart)) }
    }
    val track = colors.surfaceStrong
    val gold = colors.gold
    val bg = colors.readerBg
    val primary = colors.primary
    ArtCard {
        Column(
            Modifier.padding(vertical = QhSpacing.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(RingSize)) {
                    val stroke = 4.dp.toPx()
                    val radius = size.minDimension / 2f - stroke * 2f
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val arcSize = Size(radius * 2f, radius * 2f)
                    // The halo that the beat of the button sends out.
                    val beat = if (reduced) 0.35f else pulse.value
                    drawCircle(
                        color = primary.copy(alpha = 0.26f * (1f - beat)),
                        radius = PlayButtonSize.toPx() / 2f + (radius - PlayButtonSize.toPx() / 2f) * beat,
                        center = center,
                    )
                    drawCircle(track, radius, center, style = Stroke(stroke))
                    val start = if (reduced) -90f else sweep.value - 90f
                    if (reduced) {
                        drawCircle(gold, radius, center, style = Stroke(stroke))
                    } else {
                        drawArc(gold, start, ArcSpanDegrees, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    for (index in 0 until RingDots) {
                        val angle = -90f + index * (360f / RingDots)
                        val gap = ((angle - start) % 360f + 360f) % 360f
                        val lit = reduced || gap <= ArcSpanDegrees
                        val at = Offset(
                            center.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat(),
                            center.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat(),
                        )
                        drawCircle(bg, 8.dp.toPx(), at)
                        drawCircle(if (lit) gold else track, 6.dp.toPx(), at)
                    }
                }
                Box(
                    Modifier
                        .size(PlayButtonSize)
                        .graphicsLayer {
                            val beat = if (reduced) 0f else sin(pulse.value * PI.toFloat())
                            val scale = 1f + 0.07f * beat
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(QhTheme.shapes.full)
                        .background(primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(44.dp))
                }
            }
            Row(
                Modifier
                    .clip(QhTheme.shapes.full)
                    .background(colors.surface)
                    .border(1.dp, colors.hairline, QhTheme.shapes.full)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Repeat, contentDescription = null, tint = colors.gold, modifier = Modifier.size(16.dp))
                Text(text = repeatLabel, style = QhTheme.type.label.tabular(), color = colors.ink, maxLines = 1)
            }
            WaveBars(phase = { if (reduced) 0.9f else phase.value }, modifier = Modifier.size(width = 220.dp, height = 34.dp))
        }
    }
}

/** A row of vertical rounded bars that swell and shrink as a wave runs through them, tallest in the middle. */
@Composable
private fun WaveBars(phase: () -> Float, modifier: Modifier = Modifier) {
    val color = QhTheme.colors.primary
    Canvas(modifier) {
        val barWidth = 4.dp.toPx()
        val step = (size.width - barWidth) / (WaveBars - 1)
        val middle = (WaveBars - 1) / 2f
        val minHeight = 5.dp.toPx()
        for (index in 0 until WaveBars) {
            val envelope = (1f - abs(index - middle) / middle).coerceIn(0f, 1f).pow(0.8f)
            val wave = 0.5f + 0.5f * sin(phase() + index * 0.62f)
            val height = (minHeight + (size.height - minHeight) * (0.15f + 0.85f * envelope * wave)).coerceAtMost(size.height)
            drawRoundRect(
                color = color.copy(alpha = 0.3f + 0.7f * (height / size.height)),
                topLeft = Offset(index * step, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

// -- Slide 3: memorize and do not forget -------------------------------------------------------------

/** Blur needs Android 12; below it a dense veil with the shape of the lines stands in. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
private val BlurRadius = 14.dp
internal val LadderLabels = listOf("+1", "+3", "+7", "+14", "+30")

/**
 * An ayah that is blurred, then melts into focus, and under it the chips of the ladder of repetitions (+1 +3 +7 +14 +30 days)
 * that light up one by one; then it all starts over.
 */
@Composable
internal fun MemorizeArt(ayah: PreviewAyah, fontStyle: ReadingFontStyle) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val family = getQuranFontFamily(fontStyle)
    val hidden = remember { Animatable(if (reduced) 0f else 1f) }
    val lit = remember { mutableStateListOf(*Array(LadderLabels.size) { reduced }) }
    LaunchedEffect(reduced) {
        if (reduced) {
            hidden.snapTo(0f)
            for (index in lit.indices) lit[index] = true
            return@LaunchedEffect
        }
        while (true) {
            hidden.snapTo(1f)
            for (index in lit.indices) lit[index] = false
            delay(1000)
            hidden.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
            delay(350)
            for (index in lit.indices) {
                lit[index] = true
                delay(480)
            }
            delay(2800)
            launch { hidden.animateTo(1f, tween(650)) }
            for (index in lit.indices) lit[index] = false
            delay(900)
        }
    }
    ArtCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.x4, vertical = QhSpacing.x5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x6),
        ) {
            val blurPx = with(LocalDensity.current) { BlurRadius.toPx() }
            val ayahSize = with(LocalDensity.current) { 28.dp.toSp() }
            val veil = colors.surfaceStrong
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = hideRubSign(ayah.text),
                    style = QhTheme.type.quranBody(family, ayahSize),
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (BlurSupported) {
                                Modifier.graphicsLayer {
                                    val h = hidden.value.coerceIn(0f, 1f)
                                    alpha = 1f - 0.35f * h
                                    renderEffect = if (h > 0.001f) BlurEffect(blurPx * h, blurPx * h, TileMode.Decal) else null
                                }
                            } else {
                                Modifier
                                    .drawWithContent {
                                        drawContent()
                                        val h = hidden.value.coerceIn(0f, 1f)
                                        if (h > 0f) {
                                            val bar = size.height * 0.6f
                                            drawRoundRect(
                                                color = veil.copy(alpha = veil.alpha * h),
                                                topLeft = Offset(0f, (size.height - bar) / 2f),
                                                size = Size(size.width, bar),
                                                cornerRadius = CornerRadius(bar / 2f),
                                            )
                                        }
                                    }
                                    .graphicsLayer { alpha = 1f - hidden.value.coerceIn(0f, 1f) }
                            },
                        ),
                )
            }
            LadderRow(lit = { index -> lit[index] })
        }
    }
}

/** The chips of the ladder on a hairline, so they read as days on a line. */
@Composable
private fun LadderRow(lit: (Int) -> Boolean) {
    val colors = QhTheme.colors
    val line = colors.hairline
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx())
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LadderLabels.forEachIndexed { index, label -> LadderChip(label, lit(index)) }
    }
}

@Composable
private fun LadderChip(label: String, lit: Boolean) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val container by animateColorAsState(
        targetValue = if (lit) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "ladder-container",
    )
    val outline by animateColorAsState(
        targetValue = if (lit) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "ladder-outline",
    )
    val content by animateColorAsState(
        targetValue = if (lit) colors.onPrimaryContainer else colors.inkSecondary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "ladder-content",
    )
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(lit, reduced) {
        if (lit && !reduced) {
            scale.snapTo(0.82f)
            scale.animateTo(1f, QhMotion.bouncy())
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .background(container)
            .border(1.5.dp, outline, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = QhTheme.type.label.tabular(), color = content, maxLines = 1)
    }
}
