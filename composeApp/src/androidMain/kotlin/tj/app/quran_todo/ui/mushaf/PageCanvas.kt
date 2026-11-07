package tj.app.quran_todo.ui.mushaf

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.ui.theme.QhColors
import tj.app.quran_todo.ui.theme.QhTheme

/** How an ayah is drawn: as it is, dimmed (outside "Check yourself"), marked hard, or hidden (the one being recalled, the ones waiting). */
internal enum class AyahLook { Normal, Dimmed, Hard, Hidden, Waiting }

/** What marks the ayahs of a page: the picked one, the one that sounds, and the looks of "Check yourself". */
@Immutable
internal class PageMarks(
    val selected: AyahKey? = null,
    val playing: AyahKey? = null,
    /** The ayah of "Check yourself" that is being recalled: it has the underlay too, also while it is hidden. */
    val current: AyahKey? = null,
    val looks: Map<AyahKey, AyahLook> = emptyMap(),
) {
    fun lookOf(key: AyahKey): AyahLook = looks[key] ?: AyahLook.Normal

    val anyHidden: Boolean get() = looks.values.any { it == AyahLook.Hidden || it == AyahLook.Waiting }
}

/** Hidden text blurs on Android 12+; below it, rounded bars stand in for the words (the same as "Check yourself" in the text reader). */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Where the glyphs of a line sit inside it, as a share of the line height: a little under the middle, the way the mushaf sets them. */
private const val GlyphCenter = 0.5f

/**
 * One page of the mushaf drawn on a canvas: the underlays, the headers and Bismillahs, the words (every glyph measured once in
 * [layout]), the hidden ayahs behind a blur. The canvas is as wide as the screen and [contentHeight] px high; the block of text is
 * in the middle of it. [slot] is the height of a line in px, read while drawing, so a height that changes does not recompose.
 *
 * A tap on an ayah is [onTapAyah]; anywhere else it is [onTapBlank] (the margins show and hide the bars). A long press on an ayah is
 * [onLongPressAyah].
 */
@Composable
internal fun PageBody(
    layout: PageLayout,
    marks: PageMarks,
    slot: State<Float>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onTapAyah: (AyahKey) -> Unit,
    onLongPressAyah: (AyahKey) -> Unit,
    onTapBlank: () -> Unit,
) {
    val colors = QhTheme.colors
    val haptics = LocalHapticFeedback.current
    val lineCount = layout.page.lineCount
    val latestTapAyah by rememberUpdatedState(onTapAyah)
    val latestLong by rememberUpdatedState(onLongPressAyah)
    val latestBlank by rememberUpdatedState(onTapBlank)
    val latestMarks by rememberUpdatedState(marks)

    fun lineAt(y: Float, height: Float): Int {
        val top = pageTop(height, slot.value, lineCount)
        val line = ((y - top) / slot.value).toInt() + 1
        return if (y < top || line !in 1..lineCount) -1 else line
    }

    Box(
        modifier
            .then(
                if (interactive) {
                    Modifier.pointerInput(layout) {
                        detectTapGestures(
                            onTap = { point ->
                                val x = point.x - (size.width - layout.blockWidth) / 2f
                                val line = lineAt(point.y, size.height.toFloat())
                                val key = if (line > 0) layout.ayahAt(x, line) else null
                                if (key != null) latestTapAyah(key) else latestBlank()
                            },
                            onLongPress = { point ->
                                val x = point.x - (size.width - layout.blockWidth) / 2f
                                val line = lineAt(point.y, size.height.toFloat())
                                val key = if (line > 0) layout.ayahAt(x, line) else null
                                if (key != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    latestLong(key)
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val m = latestMarks
            val left = (size.width - layout.blockWidth) / 2f
            val top = pageTop(size.height, slot.value, lineCount)
            drawUnderlays(layout, m, colors, left, top, slot.value)
            drawHeaders(layout, colors, left, top, slot.value)
            drawBismillahs(layout, colors, left, top, slot.value)
            drawWords(layout, m, colors, left, top, slot.value, hidden = false)
            if (!BlurSupported) drawBars(layout, m, colors, left, top, slot.value)
        }
        if (BlurSupported && marks.anyHidden) {
            val radius = layout.glyphSize * BlurOfGlyph
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                    },
            ) {
                val left = (size.width - layout.blockWidth) / 2f
                val top = pageTop(size.height, slot.value, lineCount)
                drawWords(layout, latestMarks, colors, left, top, slot.value, hidden = true)
            }
        }
    }
}

/** Blur radius of a hidden ayah in glyph sizes (about 14 dp for the glyphs of a phone, as in the text reader). */
private const val BlurOfGlyph = 0.5f

private const val HiddenAlpha = 0.5f
private const val WaitingAlpha = 0.4f
private const val DimmedAlpha = 0.4f

/** The top of the first line place: pages with fewer lines than the body can hold (1 and 2) are centred in it. */
internal fun pageTop(height: Float, slot: Float, lineCount: Int): Float = ((height - lineCount * slot) / 2f).coerceAtLeast(0f)

private fun lineCenter(top: Float, slot: Float, number: Int): Float = top + (number - 1) * slot + slot * GlyphCenter

private fun DrawScope.drawUnderlays(layout: PageLayout, marks: PageMarks, colors: QhColors, left: Float, top: Float, slot: Float) {
    val playing = colors.primary.copy(alpha = if (colors.isDark) 0.30f else 0.18f)
    val picked = colors.gold.copy(alpha = if (colors.isDark) 0.30f else 0.24f)
    val pad = layout.glyphSize * 0.12f
    val corner = CornerRadius(layout.glyphSize * 0.28f)
    fun paint(key: AyahKey?, color: Color) {
        if (key == null) return
        for (span in layout.spansOf(key)) {
            val y = top + (span.lineNumber - 1) * slot
            drawRoundRect(
                color = color,
                topLeft = Offset(left + span.left - pad, y + slot * 0.1f),
                size = Size(span.right - span.left + 2 * pad, slot * 0.8f),
                cornerRadius = corner,
            )
        }
    }
    // The picked one first, so the sound lies over it.
    paint(marks.selected, picked)
    paint(marks.current, picked)
    paint(marks.playing, playing)
}

private fun DrawScope.drawWords(
    layout: PageLayout,
    marks: PageMarks,
    colors: QhColors,
    left: Float,
    top: Float,
    slot: Float,
    hidden: Boolean,
) {
    for (placed in layout.words) {
        val look = marks.lookOf(placed.word.key)
        val isHidden = look == AyahLook.Hidden || look == AyahLook.Waiting
        if (isHidden != hidden) continue
        val base = when {
            placed.word.isAyahEnd -> colors.primary
            look == AyahLook.Hard -> colors.due
            else -> colors.ink
        }
        val alpha = when (look) {
            AyahLook.Dimmed -> DimmedAlpha
            AyahLook.Hidden -> HiddenAlpha
            AyahLook.Waiting -> WaitingAlpha
            else -> 1f
        }
        val y = lineCenter(top, slot, placed.lineNumber) - placed.layout.size.height / 2f
        drawText(placed.layout, color = base.copy(alpha = base.alpha * alpha), topLeft = Offset(left + placed.x, y))
    }
}

/** Without a blur, the words of a hidden ayah are covered by rounded bars, one for each line the ayah is on. */
private fun DrawScope.drawBars(layout: PageLayout, marks: PageMarks, colors: QhColors, left: Float, top: Float, slot: Float) {
    if (!marks.anyHidden) return
    val corner = CornerRadius(layout.glyphSize * 0.2f)
    for (span in layout.spans) {
        val look = marks.lookOf(span.key)
        if (look != AyahLook.Hidden && look != AyahLook.Waiting) continue
        val alpha = if (look == AyahLook.Hidden) 0.34f else 0.22f
        val barHeight = layout.glyphSize * 0.62f
        val y = lineCenter(top, slot, span.lineNumber) - barHeight / 2f
        drawRoundRect(
            color = colors.ink.copy(alpha = alpha),
            topLeft = Offset(left + span.left, y),
            size = Size(span.right - span.left, barHeight),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawBismillahs(layout: PageLayout, colors: QhColors, left: Float, top: Float, slot: Float) {
    for (text in layout.bismillahs) {
        val y = lineCenter(top, slot, text.lineNumber) - text.layout.size.height / 2f
        drawText(text.layout, color = colors.ink, topLeft = Offset(left + text.x, y))
    }
}

/**
 * The header of a surah: a thin double frame with rounded corners in the gold of the app, a small rhombus between two dots at each
 * end, and the name of the surah in the Hafs font between them. Lines, dots and rhombi only: no stars and no eight-sided figures.
 */
private fun DrawScope.drawHeaders(layout: PageLayout, colors: QhColors, left: Float, top: Float, slot: Float) {
    val outerStroke = 1.5.dp.toPx()
    val innerStroke = 1.dp.toPx()
    val gold = colors.gold
    for (header in layout.headers) {
        val height = (slot * 0.84f).coerceAtMost(layout.glyphSize * 1.9f)
        val y = top + (header.lineNumber - 1) * slot + (slot - height) / 2f
        val width = layout.blockWidth
        val corner = height * 0.42f
        drawRoundRect(
            color = gold,
            topLeft = Offset(left + outerStroke / 2f, y + outerStroke / 2f),
            size = Size(width - outerStroke, height - outerStroke),
            cornerRadius = CornerRadius(corner),
            style = Stroke(outerStroke),
        )
        val gap = 4.dp.toPx()
        drawRoundRect(
            color = gold.copy(alpha = 0.55f),
            topLeft = Offset(left + gap, y + gap),
            size = Size(width - 2 * gap, height - 2 * gap),
            cornerRadius = CornerRadius((corner - gap).coerceAtLeast(0f)),
            style = Stroke(innerStroke),
        )
        // The rhombus with its two dots, at both ends of the frame.
        val centerY = y + height / 2f
        val half = height * 0.17f
        val inset = corner * 0.95f + half
        for (side in intArrayOf(-1, 1)) {
            val cx = if (side < 0) left + inset else left + width - inset
            val rhombus = Path().apply {
                moveTo(cx, centerY - half)
                lineTo(cx + half, centerY)
                lineTo(cx, centerY + half)
                lineTo(cx - half, centerY)
                close()
            }
            drawPath(rhombus, gold)
            val dotRadius = 1.6.dp.toPx()
            drawCircle(gold.copy(alpha = 0.8f), dotRadius, Offset(cx - half * 2.2f, centerY))
            drawCircle(gold.copy(alpha = 0.8f), dotRadius, Offset(cx + half * 2.2f, centerY))
        }
        val nameY = centerY - header.layout.size.height / 2f
        drawText(header.layout, color = colors.ink, topLeft = Offset(left + header.x, nameY))
    }
}
