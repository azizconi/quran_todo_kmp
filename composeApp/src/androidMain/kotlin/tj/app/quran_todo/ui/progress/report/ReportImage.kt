package tj.app.quran_todo.ui.progress.report

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.ui.components.Calligraphy
import tj.app.quran_todo.ui.progress.analytics.Analytics
import tj.app.quran_todo.ui.theme.QhColors

/** What the card is drawn with: the light tokens of the app, its UI font and the calligraphy font. */
internal class ReportCardStyle(
    val colors: QhColors,
    val uiFont: FontFamily,
    val calligraphyFont: FontFamily,
)

/**
 * The picture of the report: the same summary as the text ([ReportDoc]) as a card in the style of the app: warm paper, the
 * word "hifz" in calligraphy, the ring with the memorized share, the rows. No stars anywhere: the only ornament is a hairline
 * with a dot.
 *
 * It is drawn straight onto an [ImageBitmap] with [CanvasDrawScope] and [TextMeasurer] instead of being composed, because a
 * composable has to be on screen (attached to a window, laid out, in a running composition) to be captured, and the report
 * is made when a button is pressed, over a sheet. A software canvas needs none of that: the result is the same on every
 * device, whatever the system font scale or the current theme (everything is in pixels at a fixed width of [WidthPx]), and
 * nothing can flash on the screen. The cost is that the layout is arithmetic done here (measure each text, stack the blocks).
 */
internal object ReportImage {
    const val WidthPx = 1080

    private const val Outer = 48f
    private const val Pad = 60f
    private const val CardRadius = 56f
    private const val RingSize = 320f
    private const val RingStroke = 36f

    /** Draws [doc]; the height follows the content. Call it on the main thread: [measurer] belongs to the composition. */
    fun render(doc: ReportDoc, style: ReportCardStyle, measurer: TextMeasurer): ImageBitmap {
        val density = Density(density = 1f, fontScale = 1f)
        val colors = style.colors
        val contentWidth = WidthPx - 2 * (Outer + Pad)

        fun layout(text: String, textStyle: TextStyle, width: Float = contentWidth): TextLayoutResult =
            measurer.measure(
                text = text,
                style = textStyle,
                constraints = Constraints(maxWidth = width.toInt()),
                layoutDirection = LayoutDirection.Ltr,
                density = density,
            )

        fun ui(size: Int, weight: Int, color: Color, spacing: Float = 0f) = TextStyle(
            fontFamily = style.uiFont,
            fontSize = size.sp,
            lineHeight = (size * 1.32f).sp,
            fontWeight = FontWeight(weight),
            letterSpacing = spacing.sp,
            color = color,
        )

        val blocks = ArrayList<Block>()

        // -- Header: the calligraphy, the brand, the title and the period -------------------------------
        val calligraphy = layout(
            text = Calligraphy.Hifz,
            textStyle = TextStyle(
                fontFamily = style.calligraphyFont,
                fontSize = 132.sp,
                lineHeight = (132 * 1.6f).sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                textDirection = TextDirection.Rtl,
                color = colors.gold,
            ),
        )
        blocks += Block.centered(calligraphy, gapAfter = 4f)
        blocks += Block.centered(layout("QURAN HIFZ", ui(28, 700, colors.inkSecondary, spacing = 5f)), gapAfter = 22f)
        blocks += Block.centered(layout(doc.title, ui(60, 800, colors.ink)), gapAfter = 10f)
        blocks += Block.centered(layout(doc.period, ui(36, 500, colors.inkSecondary)), gapAfter = 40f)
        blocks += Block.divider(colors.gold.copy(alpha = 0.6f), gapAfter = 40f)

        // -- The ring -----------------------------------------------------------------------------------
        val percent = layout(doc.percentText, ui(88, 800, colors.ink, spacing = -2f))
        blocks += Block.ring(RingSize, RingStroke, doc.fraction, percent, colors.primary.copy(alpha = 0.12f), colors.learned, gapAfter = 26f)
        blocks += Block.centered(layout(doc.caption, ui(36, 600, colors.ink)), gapAfter = 36f)

        // -- The rows -----------------------------------------------------------------------------------
        doc.rows.forEachIndexed { index, row ->
            blocks += Block.rule(colors.hairline, gapAfter = 28f)
            // Not upper-cased: that is wrong for Turkish (i -> I, not İ) and the labels read well as they are.
            blocks += Block.left(layout(row.label, ui(32, 600, colors.inkSecondary)), gapAfter = 6f)
            if (row.value.isNotBlank()) {
                blocks += Block.left(layout(row.value, ui(46, 700, colors.ink)), gapAfter = 6f)
            }
            for (line in row.lines) {
                val text = if (row.bulleted) "•  $line" else line
                blocks += Block.left(layout(text, ui(35, 500, colors.ink)), gapAfter = 4f)
            }
            blocks += Block.gap(if (index == doc.rows.lastIndex) 0f else 26f)
        }

        // -- The heat strip of the days of the period ---------------------------------------------------
        if (doc.strip.isNotEmpty()) {
            blocks += Block.gap(26f)
            blocks += Block.rule(colors.hairline, gapAfter = 28f)
            blocks += Block.left(layout(doc.stripLabel, ui(32, 600, colors.inkSecondary)), gapAfter = 14f)
            blocks += Block.strip(doc.strip, colors, contentWidth)
        }

        val cardHeight = blocks.sumOf { it.height.toDouble() }.toFloat() + 2 * Pad
        val width = WidthPx
        val height = (cardHeight + 2 * Outer).toInt()
        val bitmap = ImageBitmap(width, height, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(colors.bg)
            drawRoundRect(
                color = colors.surface,
                topLeft = Offset(Outer, Outer),
                size = Size(width - 2 * Outer, height - 2 * Outer),
                cornerRadius = CornerRadius(CardRadius),
            )
            drawRoundRect(
                color = colors.hairline,
                topLeft = Offset(Outer, Outer),
                size = Size(width - 2 * Outer, height - 2 * Outer),
                cornerRadius = CornerRadius(CardRadius),
                style = Stroke(3f),
            )
            var top = Outer + Pad
            for (block in blocks) {
                block.draw(this, Outer + Pad, contentWidth, top)
                top += block.height
            }
        }
        return bitmap
    }

    /** One horizontal band of the card: its [height] (with the gap below) and how to draw it. */
    private class Block(val height: Float, val draw: (DrawScope, left: Float, width: Float, top: Float) -> Unit) {
        companion object {
            fun gap(height: Float) = Block(height) { _, _, _, _ -> }

            fun centered(layout: TextLayoutResult, gapAfter: Float) =
                Block(layout.size.height + gapAfter) { scope, left, width, top ->
                    scope.drawText(layout, topLeft = Offset(left + (width - layout.size.width) / 2f, top))
                }

            fun left(layout: TextLayoutResult, gapAfter: Float) =
                Block(layout.size.height + gapAfter) { scope, left, _, top ->
                    scope.drawText(layout, topLeft = Offset(left, top))
                }

            /** One cell per day of the period, wrapped into rows; the colour is the share of the busiest day, in the four steps of the heat map. */
            fun strip(counts: List<Int>, colors: QhColors, contentWidth: Float): Block {
                val cell = 30f
                val gap = 8f
                val perRow = ((contentWidth + gap) / (cell + gap)).toInt().coerceAtLeast(1)
                val rows = (counts.size + perRow - 1) / perRow
                val max = counts.maxOrNull() ?: 0
                return Block(rows * (cell + gap)) { scope, left, _, top ->
                    counts.forEachIndexed { index, count ->
                        val x = left + (index % perRow) * (cell + gap)
                        val y = top + (index / perRow) * (cell + gap)
                        val color = when (Analytics.level(count, max)) {
                            0 -> colors.surfaceMuted
                            1 -> colors.primary.copy(alpha = 0.3f)
                            2 -> colors.primary.copy(alpha = 0.55f)
                            3 -> colors.primary.copy(alpha = 0.8f)
                            else -> colors.primary
                        }
                        scope.drawRoundRect(color, Offset(x, y), Size(cell, cell), CornerRadius(6f))
                    }
                }
            }

            fun rule(color: Color, gapAfter: Float) = Block(2f + gapAfter) { scope, left, width, top ->
                scope.drawRect(color, topLeft = Offset(left, top), size = Size(width, 2f))
            }

            /** A hairline with a dot in the middle (the only ornament of the card). */
            fun divider(color: Color, gapAfter: Float) = Block(12f + gapAfter) { scope, left, width, top ->
                val middle = left + width / 2f
                val y = top + 6f
                scope.drawRect(color, topLeft = Offset(left, y - 1f), size = Size(width / 2f - 26f, 2f))
                scope.drawRect(color, topLeft = Offset(middle + 26f, y - 1f), size = Size(width / 2f - 26f, 2f))
                scope.drawCircle(color, radius = 6f, center = Offset(middle, y))
            }

            fun ring(
                size: Float,
                stroke: Float,
                fraction: Float,
                center: TextLayoutResult,
                track: Color,
                color: Color,
                gapAfter: Float,
            ) = Block(size + gapAfter) { scope, left, width, top ->
                val x = left + (width - size) / 2f
                val inset = stroke / 2f
                val arcSize = Size(size - stroke, size - stroke)
                val arcTopLeft = Offset(x + inset, top + inset)
                scope.drawArc(track, 0f, 360f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke))
                if (fraction > 0f) {
                    // A visible start even for a tiny share, like the ring on the screen.
                    val sweep = (360f * fraction).coerceAtLeast(6f)
                    scope.drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                scope.drawText(
                    center,
                    topLeft = Offset(x + (size - center.size.width) / 2f, top + (size - center.size.height) / 2f),
                )
            }
        }
    }
}
