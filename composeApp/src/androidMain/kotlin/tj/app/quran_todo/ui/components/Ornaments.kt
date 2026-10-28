package tj.app.quran_todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

// -- Geometry --------------------------------------------------------------------------------------
// The motif is the eight-pointed star (Rub el Hizb): two squares, one turned 45 degrees.

/** Radius of the notches of the star's silhouette relative to its points: cos(45deg) / cos(22.5deg). */
private const val NotchRatio = 0.7653669f

private fun regularPolygon(center: Offset, radius: Float, sides: Int, startAngle: Double): Path {
    val path = Path()
    for (i in 0 until sides) {
        val angle = startAngle + i * 2.0 * PI / sides
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** One of the two squares of the star; [turned] is the square rotated by 45 degrees. */
private fun starSquare(center: Offset, radius: Float, turned: Boolean): Path =
    regularPolygon(center, radius, 4, -PI / 2 + if (turned) PI / 4 else 0.0)

/** The outer silhouette of the star: 16 vertices alternating between points and notches. */
private fun starSilhouette(center: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0 until 16) {
        val angle = -PI / 2 + i * PI / 8
        val r = if (i % 2 == 0) radius else radius * NotchRatio
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawStar(
    center: Offset,
    radius: Float,
    stroke: Color,
    strokeWidth: Float,
    fill: Color = Color.Transparent,
) {
    if (fill.alpha > 0f) drawPath(starSilhouette(center, radius), fill)
    val style = Stroke(width = strokeWidth, join = StrokeJoin.Miter)
    drawPath(starSquare(center, radius, turned = false), stroke, style = style)
    drawPath(starSquare(center, radius, turned = true), stroke, style = style)
}

// -- Star -----------------------------------------------------------------------------------------

/**
 * The eight-pointed star: two square outlines, one turned 45 degrees. [fill] paints the silhouette
 * behind the lines (transparent by default).
 */
@Composable
fun EightPointStar(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = QhTheme.colors.gold,
    strokeWidth: Dp = 1.5.dp,
    fill: Color = Color.Transparent,
) {
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        drawStar(
            center = center,
            radius = this.size.minDimension / 2f - stroke * 0.75f,
            stroke = color,
            strokeWidth = stroke,
            fill = fill,
        )
    }
}

/**
 * The star drawing itself along its outline over [drawMillis] (onboarding, empty states). With
 * [breathing] it then slowly pulses between 100% and 103% every 4 seconds. Under reduced motion it is
 * simply shown complete.
 */
@Composable
fun AnimatedEightPointStar(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = QhTheme.colors.gold,
    strokeWidth: Dp = 1.5.dp,
    drawMillis: Int = 900,
    breathing: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(drawMillis, easing = FastOutSlowInEasing))
    }
    val pulse = if (breathing && !reduced) {
        rememberInfiniteTransition(label = "star-breath").animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "star-breath-scale",
        ).value
    } else {
        1f
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
    ) {
        val stroke = strokeWidth.toPx()
        val radius = this.size.minDimension / 2f - stroke * 0.75f
        val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val t = progress.value * 2f
        drawPartialPath(starSquare(center, radius, turned = false), t.coerceIn(0f, 1f), color, style)
        drawPartialPath(starSquare(center, radius, turned = true), (t - 1f).coerceIn(0f, 1f), color, style)
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

// -- Surah number badge ----------------------------------------------------------------------------

/**
 * A star with the surah number inside; its look carries the status without relying on color alone:
 * no status is a hairline outline, `LEARNING` is a gold outline with a ring arc of
 * [learnedFraction] (memorized ayahs / all ayahs), `LEARNED` is a solid emerald star. The star "pops"
 * (`bouncy()`) when it becomes learned.
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
    // Numerals are part of the ornament: keep them the size of the star, independent of font scale.
    val numberSize = with(density) { (size * if (number >= 100) 0.27f else 0.32f).toSp() }
    val starFill = colors.learned
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
            drawStar(
                center = center,
                radius = this.size.minDimension * 0.41f,
                stroke = outline,
                strokeWidth = this.size.minDimension * 0.034f,
                fill = starFill.copy(alpha = fillAlpha),
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
 * A repeating lattice of eight-pointed stars in 1dp lines at 5-8% alpha: the background of the hero
 * card and the reader header. Fill the parent with it (`Modifier.matchParentSize()`).
 *
 * @param scrollOffset current scroll offset in px; the pattern follows it at [parallax] speed (0.2 by
 * default). Ignored under reduced motion.
 */
@Composable
fun StarLattice(
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
            val strokePx = 1.dp.toPx()
            val columns = (size.width / cellPx).toInt() + 2
            val rows = (size.height / cellPx).toInt() + 3
            val lattice = Path()
            for (row in -1 until rows) {
                for (column in 0 until columns) {
                    val c = Offset((column + 0.5f) * cellPx, (row + 0.5f) * cellPx)
                    lattice.addPath(starSquare(c, cellPx * 0.46f, turned = false))
                    lattice.addPath(starSquare(c, cellPx * 0.46f, turned = true))
                }
            }
            val style = Stroke(strokePx)
            val tint = color.copy(alpha = alpha)
            onDrawBehind {
                // The pattern is periodic, so wrapping the shift by one cell is seamless.
                val shift = if (reduced) 0f else (scrollOffset() * parallax) % cellPx
                translate(top = -shift) { drawPath(lattice, tint, style = style) }
            }
        },
    )
}

/** Hairline, a small star, hairline, in gold at 60% fading out toward the edges. */
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
        EightPointStar(size = 12.dp, color = line, strokeWidth = 1.dp)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(line, Color.Transparent))),
        )
    }
}

/**
 * The frame of the reader header: a rounded rectangle with a double gold line and a star on each
 * side. [background] is the color behind the cartouche (the reader paper); it masks the line behind
 * the side stars. [content] is centered (the Arabic surah name).
 */
@Composable
fun SurahCartouche(
    modifier: Modifier = Modifier,
    background: Color = QhTheme.colors.readerBg,
    content: @Composable BoxScope.() -> Unit,
) {
    val gold = QhTheme.colors.gold
    val starRadius = 11.dp
    Box(
        modifier
            .padding(horizontal = starRadius)
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
                val r = starRadius.toPx()
                drawStar(Offset(0f, size.height / 2f), r, gold, outerStroke, fill = background)
                drawStar(Offset(size.width, size.height / 2f), r, gold, outerStroke, fill = background)
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
