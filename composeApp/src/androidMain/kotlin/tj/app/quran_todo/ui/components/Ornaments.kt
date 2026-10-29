package tj.app.quran_todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.amiri_quran
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

// The motif is Arabic calligraphy in the bundled Amiri Quran font (the bismillah ligature, iqra, a
// word of praise), the mihrab arch, a 12-lobed medallion, an ogee lattice and a rhombus. Pointed
// radial figures are deliberately absent: neither drawn ones, nor icons, nor sparkles.

// -- Calligraphy -----------------------------------------------------------------------------------

/** Phrases the app writes as calligraphy; all of them are shaped by Amiri Quran. */
object Calligraphy {
    /** The bismillah: in Amiri Quran this single code point is one calligraphic ligature. */
    const val Bismillah = "﷽"

    /** "Read!" (iqra), the first word revealed. */
    const val Iqra = "ٱقْرَأْ"

    /** "Praise be to God". */
    const val Alhamdulillah = "ٱلْحَمْدُ لِلَّهِ"

    /** "Memorization" (hifz). */
    const val Hifz = "حِفْظ"
}

/** Duration of the single glint over the letters. */
private const val GlintMillis = 900

/** Line height of calligraphy as a multiple of the font size: room for vowel marks and ligature tails. */
private const val CalligraphyLineHeight = 1.7f

/**
 * Distance from the top of the calligraphy line box to its baseline, in em: Amiri Quran has an ascent
 * of 1.815 em and a descent of 0.634 em, and the [CalligraphyLineHeight] trim is split evenly.
 */
private const val CalligraphyBaseline = 1.44f

/**
 * [text] written in Amiri Quran as an illustration: it is revealed from right to left like ink being
 * laid down (a soft edge sweeps leftward over [revealMillis] after [revealDelayMillis]) and, if
 * [shimmer], a single glint then runs over the letters. [size] is the font size in dp, so the system
 * font scale does not apply; when the text is wider than the available width, the size shrinks to
 * fit. Under reduced motion it is shown complete, with no glint. Decorative: hidden from TalkBack.
 *
 * @param revealMillis 0 shows the text at once.
 */
@Composable
fun CalligraphyText(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = QhTheme.colors.gold,
    revealMillis: Int = 1_400,
    revealDelayMillis: Int = 0,
    shimmer: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val font = Font(Res.font.amiri_quran, weight = FontWeight.Normal, style = FontStyle.Normal)
    val family = remember(font) { FontFamily(font) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val instant = reduced || revealMillis <= 0
    val reveal = remember { Animatable(if (instant) 1f else 0f) }
    // The glint runs 0 -> 1; outside that open range it is not drawn.
    val glint = remember { Animatable(0f) }
    LaunchedEffect(reduced, revealMillis, revealDelayMillis, shimmer) {
        if (reduced) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        if (revealMillis <= 0) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(1f, tween(revealMillis, revealDelayMillis, FastOutSlowInEasing))
        }
        if (shimmer) glint.animateTo(1f, tween(GlintMillis, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        val fitted = remember(text, size, maxWidthPx, family, density) {
            val naturalWidth = measurer.measure(
                text = text,
                style = TextStyle(fontFamily = family, fontSize = with(density) { size.toSp() }),
                softWrap = false,
                maxLines = 1,
            ).size.width
            if (naturalWidth > maxWidthPx && naturalWidth > 0) size * (maxWidthPx / naturalWidth) else size
        }
        val fontSize = with(density) { fitted.toSp() }
        val highlight = lerp(color, Color.White, 0.55f)

        Text(
            text = text,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val width = this.size.width
                    val progress = reveal.value
                    if (progress < 1f) {
                        // Ink laid down from the right: everything left of the soft edge is still hidden.
                        val feather = width * 0.18f
                        val edge = lerp(width + feather, -feather, progress)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Black),
                                startX = edge,
                                endX = edge + feather,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    val sweep = glint.value
                    if (sweep > 0f && sweep < 1f) {
                        // A band running right to left, drawn only over the letters.
                        val band = width * 0.22f
                        val start = lerp(width, -band, sweep)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(highlight.copy(alpha = 0f), highlight, highlight.copy(alpha = 0f)),
                                startX = start,
                                endX = start + band,
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
            style = TextStyle(
                fontFamily = family,
                fontSize = fontSize,
                lineHeight = fontSize * CalligraphyLineHeight,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Center,
                color = color,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

// -- Mihrab -----------------------------------------------------------------------------------------

private const val MihrabDrawMillis = 900
private const val MihrabInnerDelayMillis = 150

/** The mihrab arch inset by [inset] px from the outline, in a box of [w] x [h] px; it ends at the base line. */
private fun mihrabPath(w: Float, h: Float, inset: Float): Path {
    val left = 0.12f * w + inset
    val right = 0.88f * w - inset
    val top = 0.04f * h + 1.2f * inset
    val shoulder = 0.45f * h
    val bottom = h - inset
    return Path().apply {
        moveTo(left, bottom)
        lineTo(left, shoulder)
        cubicTo(left, 0.22f * h, 0.30f * w, 0.12f * h + 0.6f * inset, 0.5f * w, top)
        cubicTo(0.70f * w, 0.12f * h + 0.6f * inset, right, 0.22f * h, right, shoulder)
        lineTo(right, bottom)
    }
}

/**
 * A mihrab arch (as on the app icon) that draws itself in gold, an inner arch following it, with
 * [word] written in calligraphy inside: the default illustration of empty states. The box is [width]
 * wide and 7/6 of that tall. Under reduced motion it is shown complete.
 */
@Composable
fun MihrabCalligraphy(
    modifier: Modifier = Modifier,
    width: Dp = 88.dp,
    word: String = Calligraphy.Iqra,
) {
    val gold = QhTheme.colors.gold
    val reduced = LocalReducedMotion.current
    val outer = remember { Animatable(if (reduced) 1f else 0f) }
    val inner = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            outer.snapTo(1f)
            inner.snapTo(1f)
        } else {
            coroutineScope {
                launch { outer.animateTo(1f, tween(MihrabDrawMillis, easing = FastOutSlowInEasing)) }
                launch {
                    inner.animateTo(
                        1f,
                        tween(MihrabDrawMillis, delayMillis = MihrabInnerDelayMillis, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    val height = width * 7f / 6f
    val wordSize = width * 0.36f
    Box(modifier.size(width, height)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val outerStyle = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val innerStyle = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPartialPath(mihrabPath(w, h, inset = 0f), outer.value, gold, outerStyle)
            drawPartialPath(mihrabPath(w, h, inset = 6.dp.toPx()), inner.value, gold.copy(alpha = 0.5f), innerStyle)
            val base = Path().apply {
                moveTo(0.04f * w, h)
                lineTo(0.96f * w, h)
            }
            drawPartialPath(base, outer.value, gold, outerStyle)
        }
        CalligraphyText(
            text = word,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // The line box is placed so that the baseline of the word sits at 0.74 of the height.
                .offset(y = height * 0.74f - wordSize * CalligraphyBaseline),
            size = wordSize,
            revealMillis = 700,
            revealDelayMillis = 450,
            shimmer = false,
        )
    }
}

/** A checkmark that draws itself in [durationMillis]. */
@Composable
fun AnimatedCheckmark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = QhTheme.colors.learned,
    strokeWidth: Dp = 3.dp,
    durationMillis: Int = 420,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMillis, easing = QhMotion.Emphasized))
    }
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.54f)
            lineTo(w * 0.43f, h * 0.74f)
            lineTo(w * 0.80f, h * 0.30f)
        }
        drawPartialPath(
            path,
            progress.value,
            color,
            Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.drawPartialPath(path: Path, fraction: Float, color: Color, style: Stroke) {
    if (fraction <= 0f) return
    if (fraction >= 1f) {
        drawPath(path, color, style = style)
        return
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val segment = Path()
    measure.getSegment(0f, measure.length * fraction, segment, startWithMoveTo = true)
    drawPath(segment, color, style = style)
}


// -- Medallion ---------------------------------------------------------------------------------------

/**
 * The outline of a medallion with [lobes] petal-shaped lobes: points on a circle of 0.90 [radius],
 * neighbours joined by a quadratic curve bulging to 1.06 [radius] at the middle of their angle, so
 * the outer edge of the curve reaches about 0.97 [radius].
 */
private fun medallionPath(center: Offset, radius: Float, lobes: Int = 12): Path {
    val step = 2.0 * PI / lobes
    fun point(k: Int, r: Float, angleOffset: Double = 0.0): Offset {
        val angle = -PI / 2 + k * step + angleOffset
        return Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
    }
    val path = Path()
    val first = point(0, radius * 0.90f)
    path.moveTo(first.x, first.y)
    for (k in 0 until lobes) {
        val control = point(k, radius * 1.06f, angleOffset = step / 2)
        val next = point(k + 1, radius * 0.90f)
        path.quadraticTo(control.x, control.y, next.x, next.y)
    }
    path.close()
    return path
}

/** Radius of the medallion outline relative to the radius passed to [medallionPath]. */
private const val MedallionExtent = 0.97f

private fun DrawScope.drawMedallion(
    center: Offset,
    radius: Float,
    stroke: Color,
    strokeWidth: Float,
    fill: Color = Color.Transparent,
) {
    val path = medallionPath(center, radius)
    if (fill.alpha > 0f) drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
}

/**
 * A 12-lobed medallion in a [size] box, outlined with [strokeWidth]. [fill] paints the silhouette
 * behind the line (transparent by default).
 */
@Composable
fun Medallion(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = QhTheme.colors.gold,
    strokeWidth: Dp = 1.5.dp,
    fill: Color = Color.Transparent,
) {
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        drawMedallion(
            center = center,
            radius = (this.size.minDimension / 2f - stroke) / MedallionExtent,
            stroke = color,
            strokeWidth = stroke,
            fill = fill,
        )
    }
}

// -- Surah number badge ----------------------------------------------------------------------------

/**
 * A medallion with the surah number inside; its look carries the status without relying on color
 * alone: no status is a hairline outline, `LEARNING` is a gold outline with a ring arc of
 * [learnedFraction] (memorized ayahs / all ayahs) around a slightly smaller medallion, `LEARNED` is a
 * solid emerald medallion. The medallion "pops" (`bouncy()`) when it becomes learned.
 */
@Composable
fun SurahNumberBadge(
    number: Int,
    status: SurahTodoStatus?,
    learnedFraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val learned = status == SurahTodoStatus.LEARNED
    val learning = status == SurahTodoStatus.LEARNING

    val fillAlpha by animateFloatAsState(if (learned) 1f else 0f, QhMotion.snappy<Float>().orSnap(), label = "badge-fill")
    val ringAlpha by animateFloatAsState(if (learning) 1f else 0f, QhMotion.snappy<Float>().orSnap(), label = "badge-ring")
    val sweep by animateFloatAsState(
        targetValue = if (learning) learnedFraction.coerceIn(0f, 1f) else 0f,
        animationSpec = QhMotion.gentle<Float>().orSnap(),
        label = "badge-sweep",
    )
    val outline by animateColorAsState(
        targetValue = when {
            learned -> colors.learned
            learning -> colors.learning
            else -> colors.hairline
        },
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "badge-outline",
    )
    val numberColor by animateColorAsState(
        targetValue = if (learned) colors.onLearned else colors.ink,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "badge-number",
    )

    val pop = remember { Animatable(1f) }
    var previousLearned by remember { mutableStateOf(learned) }
    LaunchedEffect(learned) {
        if (learned && !previousLearned && !reduced) {
            pop.snapTo(0.78f)
            pop.animateTo(1f, QhMotion.bouncy())
        }
        previousLearned = learned
    }

    val density = LocalDensity.current
    // Numerals are part of the ornament: keep them the size of the medallion, independent of font scale.
    val numberSize = with(density) { (size * if (number >= 100) 0.27f else 0.32f).toSp() }
    val medallionFill = colors.learned
    val track = colors.learning

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ringStroke = this.size.minDimension * 0.05f
            val ringRadius = this.size.minDimension / 2f - ringStroke / 2f
            if (ringAlpha > 0f) {
                val topLeft = Offset(center.x - ringRadius, center.y - ringRadius)
                val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
                drawCircle(track.copy(alpha = 0.22f * ringAlpha), ringRadius, style = Stroke(ringStroke))
                if (sweep > 0f) {
                    drawArc(
                        color = track.copy(alpha = ringAlpha),
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(ringStroke, cap = StrokeCap.Round),
                    )
                }
            }
            drawMedallion(
                center = center,
                radius = this.size.minDimension * lerp(0.44f, 0.38f, ringAlpha),
                stroke = outline,
                strokeWidth = this.size.minDimension * 0.034f,
                fill = medallionFill.copy(alpha = fillAlpha),
            )
        }
        Text(
            text = number.toString(),
            style = QhTheme.type.label.tabular(),
            fontSize = numberSize,
            fontWeight = FontWeight.Bold,
            color = numberColor,
        )
    }
}

// -- Lattice, divider, cartouche, rosette ----------------------------------------------------------

/**
 * A repeating ogee lattice in 1dp lines at 5-9% alpha, made of two mirrored sine waves per column
 * with a dot where neighbouring columns touch: the background of the hero card and the reader
 * header. Fill the parent with it (`Modifier.matchParentSize()`).
 *
 * @param cell width of one column in dp; the vertical period of the waves is 1.5 cells.
 * @param scrollOffset current scroll offset in px; the pattern follows it at [parallax] speed (0.2 by
 * default). Ignored under reduced motion.
 */
@Composable
fun ArabesqueLattice(
    modifier: Modifier = Modifier,
    color: Color = QhTheme.colors.gold,
    alpha: Float = 0.07f,
    cell: Dp = 28.dp,
    scrollOffset: () -> Float = { 0f },
    parallax: Float = 0.2f,
) {
    val reduced = LocalReducedMotion.current
    Box(
        modifier.clipToBounds().drawWithCache {
            val cellPx = cell.toPx()
            val period = cellPx * 1.5f
            val amplitude = cellPx / 2f
            val strokePx = 1.dp.toPx()
            val columns = (size.width / cellPx).toInt() + 1
            val waves = Path()
            val step = period / 16f
            for (k in -1..columns + 1) {
                val x0 = k * cellPx
                for (sign in intArrayOf(1, -1)) {
                    var y = -period
                    var first = true
                    while (y <= size.height + period) {
                        val x = x0 + sign * amplitude * sin(2.0 * PI * y / period).toFloat()
                        if (first) waves.moveTo(x, y) else waves.lineTo(x, y)
                        first = false
                        y += step
                    }
                }
            }
            val dots = ArrayList<Offset>()
            var row = -1
            while (row * period <= size.height + period) {
                for (k in -1..columns + 1) {
                    dots += Offset(k * cellPx + amplitude, row * period + 0.75f * period)
                }
                row++
            }
            val style = Stroke(strokePx)
            val tint = color.copy(alpha = alpha)
            val dotWidth = 2.6.dp.toPx()
            onDrawBehind {
                // The pattern is periodic, so wrapping the shift by one period is seamless.
                val shift = if (reduced) 0f else (scrollOffset() * parallax) % period
                translate(top = -shift) {
                    drawPath(waves, tint, style = style)
                    drawPoints(dots, PointMode.Points, tint, strokeWidth = dotWidth, cap = StrokeCap.Round)
                }
            }
        },
    )
}

/** Hairline, a small rhombus between two dots, hairline, in gold at 60% fading out toward the edges. */
@Composable
fun OrnamentDivider(
    modifier: Modifier = Modifier,
    color: Color = QhTheme.colors.gold,
) {
    val line = color.copy(alpha = 0.6f)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, line))),
        )
        Canvas(Modifier.size(width = 36.dp, height = 10.dp)) {
            val dotRadius = 1.4.dp.toPx()
            val dotOffset = 11.dp.toPx()
            val half = 4.5.dp.toPx()
            drawCircle(line, dotRadius, Offset(center.x - dotOffset, center.y))
            drawCircle(line, dotRadius, Offset(center.x + dotOffset, center.y))
            val rhombus = Path().apply {
                moveTo(center.x, center.y - half)
                lineTo(center.x + half, center.y)
                lineTo(center.x, center.y + half)
                lineTo(center.x - half, center.y)
                close()
            }
            drawPath(rhombus, line)
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(line, Color.Transparent))),
        )
    }
}

/**
 * The frame of the reader header: a rounded rectangle with a double gold line and a round "ear" on
 * each side (a ring with a dot in its middle). [background] is the color behind the cartouche (the
 * reader paper); it masks the frame line behind the ears. [content] is centered (the Arabic surah
 * name).
 */
@Composable
fun SurahCartouche(
    modifier: Modifier = Modifier,
    background: Color = QhTheme.colors.readerBg,
    content: @Composable BoxScope.() -> Unit,
) {
    val gold = QhTheme.colors.gold
    val earRadius = 10.dp
    Box(
        modifier
            .padding(horizontal = earRadius)
            .drawBehind {
                val outerStroke = 1.5.dp.toPx()
                val innerStroke = 1.dp.toPx()
                val corner = 22.dp.toPx()
                val gap = 5.dp.toPx()
                drawRoundRect(
                    color = gold,
                    topLeft = Offset(outerStroke / 2f, outerStroke / 2f),
                    size = Size(size.width - outerStroke, size.height - outerStroke),
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(outerStroke),
                )
                drawRoundRect(
                    color = gold.copy(alpha = 0.55f),
                    topLeft = Offset(gap, gap),
                    size = Size(size.width - 2 * gap, size.height - 2 * gap),
                    cornerRadius = CornerRadius((corner - gap + outerStroke).coerceAtLeast(0f)),
                    style = Stroke(innerStroke),
                )
                for (x in floatArrayOf(0f, size.width)) {
                    val ear = Offset(x, size.height / 2f)
                    drawCircle(background, earRadius.toPx(), ear)
                    drawCircle(gold, earRadius.toPx(), ear, style = Stroke(outerStroke))
                    drawCircle(gold.copy(alpha = 0.55f), 5.2.dp.toPx(), ear, style = Stroke(innerStroke))
                    drawCircle(gold, 1.8.dp.toPx(), ear)
                }
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}


/**
 * An ayah-number rosette: a circle with eight petals and the number inside, ringed by the ayah's
 * status (gold: memorizing, emerald: memorized). A terracotta dot marks a [weak] ayah.
 */
@Composable
fun AyahRosette(
    number: Int,
    modifier: Modifier = Modifier,
    status: AyahTodoStatus? = null,
    weak: Boolean = false,
    size: Dp = 34.dp,
) {
    val colors = QhTheme.colors
    val ringColor by animateColorAsState(
        targetValue = when (status) {
            AyahTodoStatus.LEARNING -> colors.learning
            AyahTodoStatus.LEARNED -> colors.learned
            null -> Color.Transparent
        },
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "rosette-ring",
    )
    val density = LocalDensity.current
    val numberSize = with(density) { (size * if (number >= 100) 0.27f else 0.32f).toSp() }
    val petalFill = colors.goldContainer
    val petalLine = colors.gold.copy(alpha = 0.7f)
    val dot = colors.due

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val hairline = 1.dp.toPx()
            val ringStroke = 2.dp.toPx()
            // Petals first, then the central disc over their inner halves.
            repeat(8) { index ->
                val angle = -PI / 2 + index * PI / 4
                val petalCenter = Offset(
                    center.x + radius * 0.62f * cos(angle).toFloat(),
                    center.y + radius * 0.62f * sin(angle).toFloat(),
                )
                drawCircle(petalFill, radius * 0.2f, petalCenter)
                drawCircle(petalLine, radius * 0.2f, petalCenter, style = Stroke(hairline))
            }
            drawCircle(petalFill, radius * 0.62f)
            drawCircle(petalLine, radius * 0.62f, style = Stroke(hairline))
            if (ringColor.alpha > 0f) {
                drawCircle(ringColor, radius - ringStroke / 2f, style = Stroke(ringStroke))
            }
            if (weak) {
                drawCircle(dot, radius * 0.2f, Offset(center.x + radius * 0.74f, center.y - radius * 0.74f))
            }
        }
        Text(
            text = number.toString(),
            style = QhTheme.type.label.tabular(),
            fontSize = numberSize,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
        )
    }
}
