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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import tj.app.quran_todo.common.audio.timing.wordRanges
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.theme.QhTheme

/** Blur of a hidden ayah. */
private val BlurRadius = 14.dp

/** Opacity of the hidden text of the ayah being recalled, and of an ayah waiting for its turn. */
private const val HiddenTextAlpha = 0.35f
private const val UpcomingTextAlpha = 0.6f

/** The unblur of "Reveal" (docs/redesign/ANDROID_REDESIGN.md, 6.5). */
private const val RevealMillis = 320

/** A single word opening (a tap) or hiding ("Hide more", a new level) melts in this long. */
private const val WordFadeMillis = 220

/** Hidden text blurs on Android 12+; below it, rounded bars stand in for the words. */
private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Share of a bar's height inside the line of the Arabic text (the glyphs are about 0.4 of a 1.85x line). */
private const val BarHeightOfLine = 0.4f

/** A finger is bigger than a letter: a tap this close to a hidden word (in dp) still means that word. */
private const val WordTapSlackDp = 8

/** The thin line under a word that was opened by a tap: it sits this far (in font sizes) under the baseline. */
private const val UnderlineBelowBaseline = 0.3f

/** Opacity of the first letters of the "first letters" level over the veiled words. */
private const val LetterAlpha = 1f

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
 * and holding the ayah being recalled peeks at it (it counts as two peeks).
 *
 * The hint level ([HifzRowState.hintMode], task F2a) decides which words stay sharp: the first word, every other word,
 * all but the last quarters of the progressive level; at the "first letters" level the first letter of every hidden word
 * is drawn in its place. A tap on a hidden word opens only that word until the ayah is answered; it gets a thin underline
 * and counts as one peek ([HifzRowState.host]). A rated ayah and an ayah outside the session show their text as usual.
 *
 * A screen reader hears "text is hidden" instead of the text while some of it is hidden.
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

    // The words are the ones `displayWords` finds: [arabic] is the display text already (the Bismillah of the first ayah is cut off).
    val words = remember(arabic) { AyahWords(arabic, wordRanges(arabic)) }

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

    // The words that stay sharp. The layers below keep their shape for as long as the ayah is part of what is still to be recalled,
    // also while it melts open, so a word never changes from sharp to blurred in between. A waiting ayah of the progressive level is
    // hidden whole (the quarters are for the ayah being recalled).
    val inSession = state.phase == HifzPhase.Current || state.phase == HifzPhase.Upcoming
    val step = if (state.phase == HifzPhase.Upcoming) RecallHints.ProgressiveSteps else state.hideStep
    val keep: Set<Int> = remember(inSession, state.hintMode, step, state.peeked, words.wordCount) {
        if (!inSession) {
            emptySet()
        } else {
            RecallHints.shownWords(state.hintMode, words.wordCount, step) + state.peeked.filter { it < words.wordCount }
        }
    }
    val shown = remember { ShownWords(keep) }
    LaunchedEffect(keep, reduced) { shown.moveTo(keep, animate = !reduced) }

    val wordLayout = remember(words) { WordLayout(words.ranges) }
    val textAlpha = if (state.phase == HifzPhase.Upcoming) UpcomingTextAlpha else HiddenTextAlpha
    val somethingHidden = keep.size < words.wordCount

    // The tap and the hold work on the latest state without restarting the gesture.
    val latestKeep by rememberUpdatedState(keep)
    val latestState by rememberUpdatedState(state)
    val slack = with(LocalDensity.current) { WordTapSlackDp.dp.toPx() }

    Box(
        modifier
            .then(
                if (canPeek) {
                    Modifier.recallGesture(
                        onTap = { point ->
                            val word = wordLayout.wordNear(point, slack, skip = latestKeep)
                            if (word == null) {
                                false
                            } else {
                                haptics.perform(HapticKind.Tick)
                                latestState.host?.peekWord(latestState.position, word)
                                true
                            }
                        },
                        onHoldStart = {
                            haptics.perform(HapticKind.LongPress)
                            latestState.host?.peekAyah(latestState.position)
                        },
                        onHold = { holding = it },
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (covered && somethingHidden) {
                    val hiddenLabel = state.copy.hiddenText
                    Modifier.clearAndSetSemantics { contentDescription = hiddenLabel }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            VeiledText(
                text = if (inSession) veilSpans(arabic, words.ranges, shown, colors.ink) else AnnotatedString(arabic),
                style = arabicStyle,
                color = colors.ink,
                hidden = hidden,
                coveredAlpha = textAlpha,
                barHeightOfLine = BarHeightOfLine,
                wordLayout = wordLayout,
                shown = if (inSession) shown else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!translation.isNullOrBlank()) {
                VeiledText(
                    text = AnnotatedString(translation),
                    style = translationStyle,
                    color = colors.inkSecondary,
                    hidden = hidden,
                    coveredAlpha = textAlpha,
                    barHeightOfLine = 0.5f,
                    wordLayout = null,
                    shown = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }
        if (inSession) {
            // The same text with only the sharp words visible, laid over the veiled rest.
            Text(
                text = sharpSpans(arabic, words.ranges, shown, colors.ink),
                style = arabicStyle,
                color = Color.Transparent,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            WordMarks(
                words = words,
                wordLayout = wordLayout,
                shown = shown,
                hidden = hidden,
                peeked = state.peeked,
                letters = RecallHints.showsLetters(state.hintMode),
                arabicStyle = arabicStyle,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

// -- Words ---------------------------------------------------------------------------------------------------

/**
 * Which words are sharp, as an amount per word (0 hidden, 1 sharp). It follows the wanted set; a word that joins or leaves it
 * melts over [WordFadeMillis] (at once with reduced motion). Several words can change in one go.
 */
@Stable
private class ShownWords(initial: Set<Int>) {
    private var from by mutableStateOf(initial)
    private var to by mutableStateOf(initial)
    private val progress = Animatable(1f)

    fun amount(word: Int): Float {
        val a = if (word in from) 1f else 0f
        val b = if (word in to) 1f else 0f
        return a + (b - a) * progress.value
    }

    suspend fun moveTo(target: Set<Int>, animate: Boolean) {
        if (target == to) return
        if (!animate) {
            from = target
            to = target
            progress.snapTo(1f)
            return
        }
        // A change that interrupts another one starts from where that one was going. The order keeps every frame in between right:
        // [from] takes the old target first (the look does not change), then the progress goes back to 0 (still the old look),
        // and only then the new target comes in.
        from = to
        progress.snapTo(0f)
        to = target
        progress.animateTo(1f, tween(WordFadeMillis, easing = QhMotion.EmphasizedDecelerate))
    }
}

/**
 * The layout of the Arabic text and where each word sits in it, for the taps, the underlines, the first letters and the bars. The
 * layout is state: what is drawn from it is drawn again when the text is laid out anew.
 */
@Stable
private class WordLayout(private val ranges: List<IntRange>) {
    private var current by mutableStateOf<TextLayoutResult?>(null)
    private var rects = arrayOfNulls<Rect>(ranges.size)

    val layout: TextLayoutResult? get() = current

    /** The number of words. */
    val count: Int get() = ranges.size

    /** True when the first letter of [word] is in a right-to-left run (an Arabic word starts at its right edge). */
    fun startsAtRight(word: Int): Boolean {
        val layout = current ?: return true
        val first = ranges.getOrNull(word)?.first ?: return true
        if (first >= layout.layoutInput.text.length) return true
        return layout.getBidiRunDirection(first) == ResolvedTextDirection.Rtl
    }

    fun update(value: TextLayoutResult) {
        if (value === current) return
        rects = arrayOfNulls(ranges.size)
        current = value
    }

    /** Where [word] is drawn (a word never breaks across lines), or null before the first layout. */
    fun rect(word: Int): Rect? {
        val layout = current ?: return null
        val range = ranges.getOrNull(word) ?: return null
        rects[word]?.let { return it }
        if (range.last + 1 > layout.layoutInput.text.length) return null
        val bounds = layout.getPathForRange(range.first, range.last + 1).getBounds()
        if (bounds.isEmpty) return null
        rects[word] = bounds
        return bounds
    }

    /** The hidden word at [point] (not one of [skip]), allowing [slack] pixels around the words; null when none is near. */
    fun wordNear(point: Offset, slack: Float, skip: Set<Int>): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for (word in ranges.indices) {
            if (word in skip) continue
            val rect = rect(word) ?: continue
            val inflated = Rect(rect.left - slack, rect.top - slack / 2f, rect.right + slack, rect.bottom + slack / 2f)
            if (!inflated.contains(point)) continue
            val distance = (rect.center - point).getDistance()
            if (distance < bestDistance) {
                bestDistance = distance
                best = word
            }
        }
        return best
    }
}

/**
 * The text a word owns: from its first character to the first character of the next word, so the pause marks that stand between
 * two words follow the word before them, and the start of the text (a mark before the first word) goes to the first word.
 */
private fun owned(ranges: List<IntRange>, word: Int, length: Int): IntRange {
    val start = if (word == 0) 0 else ranges[word].first
    val end = if (word == ranges.lastIndex) length else ranges[word + 1].first
    return start until end
}

/** The veiled layer: every word that is (becoming) sharp is faded out of it, the rest stays for the blur. */
private fun veilSpans(text: String, ranges: List<IntRange>, shown: ShownWords, ink: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    ranges.indices.forEach { word ->
        val amount = shown.amount(word)
        if (amount <= 0.001f) return@forEach
        val own = owned(ranges, word, text.length)
        val color = if (amount >= 0.999f) Color.Transparent else ink.copy(alpha = ink.alpha * (1f - amount))
        addStyle(SpanStyle(color = color), own.first, own.last + 1)
    }
}

/** The sharp layer: only the words that are (becoming) sharp have a color. */
private fun sharpSpans(text: String, ranges: List<IntRange>, shown: ShownWords, ink: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    ranges.indices.forEach { word ->
        val amount = shown.amount(word)
        if (amount <= 0.001f) return@forEach
        val own = owned(ranges, word, text.length)
        addStyle(SpanStyle(color = ink.copy(alpha = ink.alpha * amount)), own.first, own.last + 1)
    }
}

/**
 * What is drawn over the text: the thin underline of the words a tap opened, and - at the "first letters" level - the first letter of
 * every hidden word at the place where the word starts, in its own isolated form.
 */
@Composable
private fun WordMarks(
    words: AyahWords,
    wordLayout: WordLayout,
    shown: ShownWords,
    hidden: Animatable<Float, AnimationVector1D>,
    peeked: Set<Int>,
    letters: Boolean,
    arabicStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val letterStyle = remember(arabicStyle, colors.inkSecondary) { arabicStyle.copy(color = colors.inkSecondary) }
    val firstLetters = remember(words, letters) {
        if (letters) words.ranges.map { RecallHints.firstLetter(words.text.substring(it)) } else emptyList()
    }
    val underline = colors.gold
    val fontPx = with(LocalDensity.current) { arabicStyle.fontSize.toPx() }
    Spacer(
        modifier.drawBehind {
            val layout = wordLayout.layout ?: return@drawBehind
            if (letters) drawFirstLetters(wordLayout, firstLetters, shown, hidden.value, measurer, letterStyle)
            if (peeked.isNotEmpty()) drawUnderlines(layout, wordLayout, peeked, underline, fontPx, shown)
        },
    )
}

private fun DrawScope.drawFirstLetters(
    wordLayout: WordLayout,
    letters: List<String?>,
    shown: ShownWords,
    veil: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val strength = veil.coerceIn(0f, 1f)
    if (strength <= 0.001f) return
    letters.forEachIndexed { word, letter ->
        letter ?: return@forEachIndexed
        val alpha = (1f - shown.amount(word)) * strength * LetterAlpha
        if (alpha <= 0.001f) return@forEachIndexed
        val rect = wordLayout.rect(word) ?: return@forEachIndexed
        val measured = measurer.measure(letter, style)
        val x = if (wordLayout.startsAtRight(word)) rect.right - measured.size.width else rect.left
        val y = rect.top + (rect.height - measured.size.height) / 2f
        drawText(measured, topLeft = Offset(x, y), alpha = alpha)
    }
}

private fun DrawScope.drawUnderlines(
    layout: TextLayoutResult,
    wordLayout: WordLayout,
    peeked: Set<Int>,
    color: Color,
    fontPx: Float,
    shown: ShownWords,
) {
    val stroke = 1.5.dp.toPx()
    for (word in peeked) {
        val rect = wordLayout.rect(word) ?: continue
        val amount = shown.amount(word)
        if (amount <= 0.001f) continue
        val line = layout.getLineForVerticalPosition(rect.center.y)
        val y = layout.getLineBaseline(line) + fontPx * UnderlineBelowBaseline
        drawLine(
            color = color.copy(alpha = color.alpha * amount),
            start = Offset(rect.left, y),
            end = Offset(rect.right, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Text that can be veiled: [hidden] (1 hidden, 0 shown) drives a blur and a lower opacity on Android 12+; below that, bars
 * the size of the hidden words (of the lines, for text without [wordLayout]) are drawn over the text's place while the text itself
 * fades. The words that are sharp ([shown]) have been taken out of [text] by the caller.
 */
@Composable
private fun VeiledText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    hidden: Animatable<Float, AnimationVector1D>,
    coveredAlpha: Float,
    barHeightOfLine: Float,
    wordLayout: WordLayout?,
    shown: ShownWords?,
    modifier: Modifier = Modifier,
) {
    val holder = remember { LineLayout() }
    val onLayout: (TextLayoutResult) -> Unit = { result ->
        holder.layout = result
        wordLayout?.update(result)
    }
    if (BlurSupported) {
        val blurPx = with(LocalDensity.current) { BlurRadius.toPx() }
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            onTextLayout = onLayout,
            modifier = modifier.graphicsLayer {
                val h = hidden.value.coerceIn(0f, 1f)
                alpha = 1f - (1f - coveredAlpha) * h
                renderEffect = if (h > 0.001f) BlurEffect(blurPx * h, blurPx * h, TileMode.Decal) else null
            },
        )
    } else {
        val barColor = QhTheme.colors.surfaceStrong
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Start,
            onTextLayout = onLayout,
            modifier = modifier
                .drawBehind {
                    val h = hidden.value.coerceIn(0f, 1f)
                    if (h <= 0f) return@drawBehind
                    if (wordLayout != null && shown != null) {
                        drawWordBars(wordLayout, shown, barColor, h, barHeightOfLine)
                    } else {
                        drawLineBars(holder.layout, barColor, h, barHeightOfLine)
                    }
                }
                .graphicsLayer { alpha = 1f - hidden.value.coerceIn(0f, 1f) },
        )
    }
}

/** Remembers the last layout of a text so the bars can follow its lines without recomposing. */
@Stable
private class LineLayout {
    var layout: TextLayoutResult? = null
}

/** Draws a rounded bar over each word that is not sharp; [alpha] fades them in. */
private fun DrawScope.drawWordBars(wordLayout: WordLayout, shown: ShownWords, color: Color, alpha: Float, heightOfLine: Float) {
    val gap = 2.dp.toPx()
    for (word in 0 until wordLayout.count) {
        val rect = wordLayout.rect(word) ?: continue
        val amount = shown.amount(word)
        if (amount < 0.999f) {
            val barHeight = rect.height * heightOfLine
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha * (1f - amount)),
                topLeft = Offset(rect.left + gap, rect.top + (rect.height - barHeight) / 2f),
                size = Size((rect.width - gap * 2).coerceAtLeast(1f), barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
            )
        }
    }
}

/** Draws a rounded bar over each line of [layout]; [alpha] fades them in. */
private fun DrawScope.drawLineBars(
    layout: TextLayoutResult?,
    color: Color,
    alpha: Float,
    heightOfLine: Float,
) {
    layout ?: return
    val paint = color.copy(alpha = color.alpha * alpha)
    for (line in 0 until layout.lineCount) {
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
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
 * The touch on the text of the ayah being recalled. A quick tap calls [onTap] with the point; when it returns true a hidden word was
 * opened and the touch is used up. Press and hold: [onHoldStart] and `onHold(true)` once the long-press time has passed with the
 * finger still, `onHold(false)` when it lifts or the gesture is cancelled. A finger that moves first is a scroll and is left to the
 * list; a hold takes the touch, so the list does not move while peeking.
 */
private fun Modifier.recallGesture(
    onTap: (Offset) -> Boolean,
    onHoldStart: () -> Unit,
    onHold: (Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val timeout = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var outcome = Touch.Hold
        var upPosition = down.position
        withTimeoutOrNull(timeout) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed) {
                    outcome = Touch.Gone
                    break
                }
                if (!change.pressed) {
                    outcome = Touch.Tap
                    upPosition = change.position
                    if (onTap(change.position)) change.consume()
                    break
                }
                if ((change.position - down.position).getDistance() > slop) {
                    outcome = Touch.Gone
                    break
                }
            }
        }
        if (outcome == Touch.Hold) {
            onHoldStart()
            onHold(true)
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    change.consume()
                }
            } finally {
                onHold(false)
            }
        }
    }
}

private enum class Touch { Hold, Tap, Gone }
