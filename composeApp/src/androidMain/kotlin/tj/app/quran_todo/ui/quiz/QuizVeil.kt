package tj.app.quran_todo.ui.quiz

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.theme.QhTheme

/** Blur of the hidden part, as in "Recall". */
private val BlurRadius = 14.dp

/** Opacity of the hidden text behind the blur. */
private const val HiddenTextAlpha = 0.35f

/** The unblur when the answer is shown. */
private const val RevealMillis = 320

/** Hidden text blurs on Android 12+; below it, rounded bars stand in for the words. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Share of a bar's height inside the line of the Arabic text (the glyphs are about 0.4 of a 1.85x line). */
private const val BarHeightOfLine = 0.4f

/**
 * The Arabic text of an ayah with a veil over a part of it (the quiz "Check"): the first [visibleWords] words are always sharp, the
 * rest is hidden behind a blur (Android 12+) or rounded bars (below) until [revealed], when the veil melts in 320 ms (at once under
 * reduced motion). [visibleWords] = 0 hides the whole ayah. A screen reader hears the visible words and [hiddenLabel] while it is
 * hidden, never the hidden words. Own and simple on purpose: it does not depend on the "Recall" component of the reader.
 */
@Composable
internal fun QuizVeiledText(
    words: AyahWords,
    visibleWords: Int,
    revealed: Boolean,
    style: TextStyle,
    hiddenLabel: String,
    modifier: Modifier = Modifier,
    /** Words that differ from another ayah (task F6): character ranges in the text, drawn in the accent colour once they are shown. */
    emphasis: List<IntRange> = emptyList(),
) {
    val colors = QhTheme.colors
    val accent = colors.primary
    val reduced = LocalReducedMotion.current
    val count = words.wordCount
    val shown = visibleWords.coerceIn(0, count)
    val splitAt = if (shown == 0) 0 else words.ranges[shown - 1].last + 1
    val goal = if (revealed || shown >= count) 0f else 1f

    // 1 = hidden, 0 = shown; a page that opens already revealed starts at its resting value.
    val hidden = remember { Animatable(goal) }
    LaunchedEffect(goal, reduced) {
        if (hidden.value == goal) return@LaunchedEffect
        if (reduced) hidden.snapTo(goal) else hidden.animateTo(goal, tween(RevealMillis, easing = QhMotion.EmphasizedDecelerate))
    }

    if (goal == 0f && hidden.value == 0f) {
        Text(
            text = remember(words, emphasis, accent) { emphasisText(words.text, emphasis, accent) },
            style = style,
            color = colors.ink,
            textAlign = TextAlign.Start,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    val description = remember(words, shown, hiddenLabel) {
        val head = if (splitAt > 0) words.text.substring(0, splitAt) + " " else ""
        head + hiddenLabel
    }
    val veiled = remember(words, splitAt, emphasis, accent) {
        buildAnnotatedString {
            append(emphasisText(words.text, emphasis, accent))
            if (splitAt > 0) addStyle(SpanStyle(color = Color.Transparent, background = Color.Transparent), 0, splitAt)
        }
    }
    val sharp = remember(words, splitAt) {
        buildAnnotatedString {
            append(words.text)
            addStyle(SpanStyle(color = Color.Transparent), splitAt, words.text.length)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .then(if (goal == 1f) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier),
    ) {
        VeiledLayer(veiled, words, shown, style, hidden)
        if (splitAt > 0) {
            // The same text with everything but the first words invisible, laid over the veiled rest.
            Text(
                text = sharp,
                style = style,
                color = colors.ink,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Remembers the last layout of a text so the bars can follow its words without recomposing. */
@Stable
private class LayoutHolder {
    var layout: TextLayoutResult? = null
}

@Composable
private fun VeiledLayer(text: AnnotatedString, words: AyahWords, shown: Int, style: TextStyle, hidden: Animatable<Float, *>) {
    val colors = QhTheme.colors
    if (BlurSupported) {
        val blurPx = with(LocalDensity.current) { BlurRadius.toPx() }
        Text(
            text = text,
            style = style,
            color = colors.ink,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val h = hidden.value.coerceIn(0f, 1f)
                    alpha = 1f - (1f - HiddenTextAlpha) * h
                    renderEffect = if (h > 0.001f) BlurEffect(blurPx * h, blurPx * h, TileMode.Decal) else null
                },
        )
    } else {
        val barColor = colors.surfaceStrong
        val holder = remember { LayoutHolder() }
        Text(
            text = text,
            style = style,
            color = colors.ink,
            textAlign = TextAlign.Start,
            onTextLayout = { holder.layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val h = hidden.value.coerceIn(0f, 1f)
                    val layout = holder.layout
                    if (h > 0f && layout != null) {
                        val paint = barColor.copy(alpha = barColor.alpha * h)
                        for (index in shown until words.wordCount) {
                            val range = words.ranges[index]
                            val box = layout.getPathForRange(range.first, range.last + 1).getBounds()
                            val barHeight = box.height * BarHeightOfLine
                            drawRoundRect(
                                color = paint,
                                topLeft = Offset(box.left, box.top + (box.height - barHeight) / 2f),
                                size = Size(box.width, barHeight),
                                cornerRadius = CornerRadius(barHeight / 2f),
                            )
                        }
                    }
                }
                .graphicsLayer { alpha = 1f - hidden.value.coerceIn(0f, 1f) },
        )
    }
}

/** [text] with the words at [ranges] (both ends inclusive) in [accent] and a faint wash behind them; the plain text when there are none. */
private fun emphasisText(text: String, ranges: List<IntRange>, accent: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in ranges) {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.14f)), start, end)
    }
}
