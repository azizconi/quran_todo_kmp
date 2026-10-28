package tj.app.quran_todo.ui.hifz

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhTheme

/** Blur of a hidden ayah. */
private val BlurRadius = 14.dp

/** Opacity of the hidden text of the ayah being recalled, and of an ayah waiting for its turn. */
private const val HiddenTextAlpha = 0.35f
private const val UpcomingTextAlpha = 0.6f

/** The unblur of "Reveal" (docs/redesign/ANDROID_REDESIGN.md, 6.5). */
private const val RevealMillis = 320

/** Hidden text blurs on Android 12+; below it, rounded bars stand in for the lines. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Share of a bar's height inside the line of the Arabic text (the glyphs are about 0.4 of a 1.85x line). */
private const val BarHeightOfLine = 0.4f

/** Opacity of the whole row by what it is in the session: only the ayah being recalled is at full strength. */
private fun rowAlpha(phase: HifzPhase): Float = when (phase) {
    HifzPhase.Current -> 1f
    HifzPhase.Done -> 0.6f
    HifzPhase.Upcoming -> 0.5f
    HifzPhase.Outside -> 0.5f
}

/**
 * The row of an ayah in the mode: dimmed unless it is the ayah being recalled, and its height follows the
 * content (the controls come and go under it) instead of jumping. Returns the row unchanged outside the mode.
 */
@Composable
fun Modifier.hifzRow(state: HifzRowState?): Modifier {
    if (state == null) return this
    val alpha = animateFloatAsState(
        targetValue = rowAlpha(state.phase),
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "hifz-row-alpha",
    )
    return this
        .animateContentSize(QhMotion.gentle<IntSize>().orSnap())
        .graphicsLayer { this.alpha = alpha.value }
}

/**
 * The Arabic text of an ayah (and its translation) as the mode shows it. An ayah being recalled or waiting for
 * its turn is hidden behind a blur (Android 12+) or rounded bars; the blur melts in 320 ms when it is revealed,
 * and holding the ayah being recalled peeks at it. With the first-word hint that word stays sharp. A rated ayah
 * and an ayah outside the session show their text as usual.
 *
 * A screen reader hears "text is hidden" instead of the text while it is hidden.
 */
@Composable
fun HifzAyahContent(
    state: HifzRowState,
    arabic: String,
    arabicStyle: TextStyle,
    translation: String?,
    translationStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHaptics.current

    val covered = when (state.phase) {
        HifzPhase.Current -> !state.revealed
        HifzPhase.Upcoming -> true
        else -> false
    }
    val canPeek = state.phase == HifzPhase.Current && !state.revealed
    var holding by remember { mutableStateOf(false) }
    val peeking = holding && canPeek
    val goal = if (covered && !peeking) 1f else 0f

    // 1 = hidden, 0 = shown. A row that appears while the mode runs starts at its resting value; the rows
    // that are on screen when the mode starts melt into the veil.
    val hidden = remember { Animatable(if (state.animateIn && covered) 0f else goal) }
    LaunchedEffect(goal) {
        if (hidden.value == goal) return@LaunchedEffect
        when {
            reduced -> hidden.snapTo(goal)
            goal == 0f && !peeking -> hidden.animateTo(0f, tween(RevealMillis, easing = QhMotion.EmphasizedDecelerate))
            state.animateIn -> hidden.animateTo(goal, tween(QhMotion.Long, easing = QhMotion.Emphasized))
            else -> hidden.animateTo(goal, QhMotion.snappy())
        }
    }

    // The hint keeps its two layers for as long as the ayah is part of what is still to be recalled, also while
    // it melts open, so the first word never changes from sharp to blurred in between.
    val hintLayers = state.firstWordHint && (state.phase == HifzPhase.Current || state.phase == HifzPhase.Upcoming)
    val firstWordEnd = if (hintLayers) firstWordEnd(arabic) else 0
    val textAlpha = if (state.phase == HifzPhase.Upcoming) UpcomingTextAlpha else HiddenTextAlpha

    Box(
        modifier
            .then(if (canPeek) Modifier.peekGesture(onPeek = { holding = it }, onStart = { haptics.perform(HapticKind.LongPress) }) else Modifier)
            .then(
                if (covered) {
                    val hiddenLabel = state.copy.hiddenText
                    Modifier.clearAndSetSemantics { contentDescription = hiddenLabel }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            VeiledText(
                text = if (firstWordEnd > 0) withoutFirstWord(arabic, firstWordEnd) else AnnotatedString(arabic),
                style = arabicStyle,
                color = colors.ink,
                hidden = hidden,
                coveredAlpha = textAlpha,
                keepEnd = firstWordEnd,
                barHeightOfLine = BarHeightOfLine,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!translation.isNullOrBlank()) {
                VeiledText(
                    text = AnnotatedString(translation),
                    style = translationStyle,
                    color = colors.inkSecondary,
                    hidden = hidden,
                    coveredAlpha = textAlpha,
                    keepEnd = 0,
                    barHeightOfLine = 0.5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
        if (firstWordEnd > 0) {
            // The same text with everything but the first word invisible, laid over the veiled rest.
            Text(
                text = onlyFirstWord(arabic, firstWordEnd),
                style = arabicStyle,
                color = colors.ink,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Index where the first word ends, or 0 when the ayah has no second word (nothing would stay hidden). */
internal fun firstWordEnd(text: String): Int {
    val end = text.indexOfFirst { it.isWhitespace() }
    if (end <= 0) return 0
    return if (text.substring(end).isBlank()) 0 else end
}

private fun withoutFirstWord(text: String, firstEnd: Int): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(color = Color.Transparent), 0, firstEnd)
}

private fun onlyFirstWord(text: String, firstEnd: Int): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(color = Color.Transparent), firstEnd, text.length)
}

/** Remembers the last layout of a text so the bars can follow its lines without recomposing. */
@Stable
private class LayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * Text that can be veiled: [hidden] (1 hidden, 0 shown) drives a blur and a lower opacity on Android 12+;
 * below that, bars the size of its lines are drawn over the text's place while the text itself fades. The first
 * [keepEnd] characters belong to the first-word hint: their line gets no bar over them.
 */
@Composable
private fun VeiledText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    hidden: Animatable<Float, AnimationVector1D>,
    coveredAlpha: Float,
    keepEnd: Int,
    barHeightOfLine: Float,
    modifier: Modifier = Modifier,
) {
    if (BlurSupported) {
        val blurPx = with(LocalDensity.current) { BlurRadius.toPx() }
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            modifier = modifier.graphicsLayer {
                val h = hidden.value.coerceIn(0f, 1f)
                alpha = 1f - (1f - coveredAlpha) * h
                renderEffect = if (h > 0.001f) BlurEffect(blurPx * h, blurPx * h, TileMode.Decal) else null
            },
        )
    } else {
        val barColor = QhTheme.colors.surfaceStrong
        val holder = remember { LayoutHolder() }
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            onTextLayout = { holder.layout = it },
            modifier = modifier
                .drawBehind {
                    val h = hidden.value.coerceIn(0f, 1f)
                    if (h > 0f) drawLineBars(holder.layout, barColor, h, keepEnd, barHeightOfLine)
                }
                .graphicsLayer { alpha = 1f - hidden.value.coerceIn(0f, 1f) },
        )
    }
}

/** Draws a rounded bar over each line of [layout]; [alpha] fades them in. */
private fun DrawScope.drawLineBars(
    layout: TextLayoutResult?,
    color: Color,
    alpha: Float,
    keepEnd: Int,
    heightOfLine: Float,
) {
    layout ?: return
    val gap = 8.dp.toPx()
    val paint = color.copy(alpha = color.alpha * alpha)
    // Where the sharp first word sits on the first line.
    val kept = if (keepEnd > 0) layout.getPathForRange(0, keepEnd).getBounds() else null
    for (line in 0 until layout.lineCount) {
        var left = layout.getLineLeft(line)
        var right = layout.getLineRight(line)
        if (line == 0 && kept != null) {
            if (layout.getParagraphDirection(0) == ResolvedTextDirection.Rtl) right = minOf(right, kept.left - gap) else left = maxOf(left, kept.right + gap)
        }
        if (right - left < 6.dp.toPx()) continue
        val top = layout.getLineTop(line)
        val lineHeight = layout.getLineBottom(line) - top
        val barHeight = lineHeight * heightOfLine
        drawRoundRect(
            color = paint,
            topLeft = Offset(left, top + (lineHeight - barHeight) / 2f),
            size = Size(right - left, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
    }
}

/**
 * Press and hold: [onStart] and `onPeek(true)` once the long-press time has passed with the finger still,
 * `onPeek(false)` when it lifts or the gesture is cancelled. A finger that moves first is a scroll and is left
 * to the list; a hold takes the touch, so the list does not move while peeking.
 */
private fun Modifier.peekGesture(onPeek: (Boolean) -> Unit, onStart: () -> Unit): Modifier =
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val timeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val aborted = withTimeoutOrNull(timeout) {
                var abort = false
                while (!abort) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    abort = change == null || !change.pressed || change.isConsumed ||
                        (change.position - down.position).getDistance() > slop
                }
                true
            }
            if (aborted == null) {
                onStart()
                onPeek(true)
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                    }
                } finally {
                    onPeek(false)
                }
            }
        }
    }
