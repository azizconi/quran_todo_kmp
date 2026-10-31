package tj.app.quran_todo.ui.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.koin.compose.koinInject
import tj.app.quran_todo.common.audio.timing.WordSpan
import tj.app.quran_todo.common.audio.timing.WordTimings
import tj.app.quran_todo.common.audio.timing.WordTimingsStore
import tj.app.quran_todo.common.audio.timing.startOfWord
import tj.app.quran_todo.common.audio.timing.usableSpans
import tj.app.quran_todo.common.audio.timing.wordAt
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.tajweed.TajweedAwareText
import tj.app.quran_todo.ui.tajweed.rememberTajweedText
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.words.WordMeaningPopup
import tj.app.quran_todo.ui.words.WordMeaningSelection

/**
 * What the text of one ayah needs to follow the recitation word by word. It is given to the ayah that is playing (or paused halfway,
 * or picked) and to no other: only that one polls the player and draws a highlight, so the list never recomposes because of the clock.
 *
 * @property playing audio is sounding right now (the clock runs); when it is false the highlight stays on the word where the
 *   recitation was paused.
 * @property onWordTap called with the start (ms in the ayah's file) of the word that was tapped; null when words cannot be tapped. A
 *   tap that is not on a word is left to the surrounding row (it selects the ayah), and so is a long press.
 */
@Immutable
class WordFollow(
    val playing: Boolean,
    val onWordTap: ((startMs: Int) -> Unit)? = null,
)

/**
 * The word timings of [surah] for the reciter of the settings, or null while they are not on the device and not fetched (yet, or
 * never: offline without a copy). Null always when the setting "Highlight words while listening" is off. Never shows an error.
 */
@Composable
internal fun rememberWordTimings(surah: Int): State<WordTimings?> {
    val prefs = koinInject<UiPrefs>()
    val enabled by prefs.highlightWords.collectAsState()
    val reciter by prefs.reciter.collectAsState()
    val store = WordTimingsStore.shared
    val recitationId = reciter.recitationId
    return produceState<WordTimings?>(
        initialValue = if (enabled) store.local(recitationId, surah) else null,
        enabled,
        recitationId,
        surah,
    ) {
        value = if (enabled) store.local(recitationId, surah) else null
        if (enabled && value == null) value = store.load(recitationId, surah)
    }
}

/** The usable spans ([usableSpans]) of ayah [ayahInSurah], which has [wordCount] words; empty without timings or when they do not fit. */
@Composable
internal fun rememberAyahSpans(surah: Int, ayahInSurah: Int, wordCount: Int): List<WordSpan> {
    val timings by rememberWordTimings(surah)
    return remember(timings, ayahInSurah, wordCount) { usableSpans(timings?.spans(ayahInSurah).orEmpty(), wordCount) }
}

/**
 * Starts reading the timings of [surah] as soon as it is shown, so that they are there when the first ayah starts to sound (they are
 * one small request, cached after the first time).
 */
@Composable
internal fun PrepareWordTimings(surah: Int) {
    rememberWordTimings(surah)
}

/**
 * The Arabic text of an ayah. With a [follow] and timings that fit the text the word that is being recited gets a soft wash in the
 * accent colour (a cross-fade of at most 120 ms between two words, none with reduced motion), and the words can be tapped
 * ([WordFollow.onWordTap]). Without them it is the plain text, as it always was.
 *
 * The clock is the player itself, asked about 16 times a second only while audio sounds and the screen is shown; the speed of
 * playback needs no care because the player's position is the position in the recording.
 */
@Composable
fun FollowedArabicText(
    surah: Int,
    ayahInSurah: Int,
    words: AyahWords,
    follow: WordFollow?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    /**
     * Word meanings (F4b): given to the text of the reader while the setting is on. A tap on a word then picks it ([WordMeaningSelection])
     * instead of seeking to it, and the word gets the wash and the popup with its meaning. Null: nothing of it, as it always was.
     */
    meaning: WordMeaningSelection? = null,
) {
    val spans = if (follow != null) rememberAyahSpans(surah, ayahInSurah, words.wordCount) else emptyList()
    // The tajweed colours (F3b) go over the very same text: the wash of the word and the colours of the rules are drawn together.
    val tajweed = rememberTajweedText(surah, ayahInSurah, words.text)
    if (meaning == null && (follow == null || spans.isEmpty())) {
        TajweedAwareText(
            text = words.text,
            styled = tajweed,
            style = style,
            color = color,
            modifier = modifier,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
    } else {
        FollowedText(words, spans, follow, style, color, modifier, textAlign, maxLines, overflow, tajweed, meaning)
    }
}

/** Holds the layout of the text between the layout pass (which writes it) and the draw pass and the taps (which read it). */
private class WordLayout(val ranges: List<IntRange>) {
    var layout: TextLayoutResult? = null
        set(value) {
            if (value !== field) {
                field = value
                rects = arrayOfNulls(ranges.size)
            }
        }
    private var rects: Array<Rect?> = arrayOfNulls(ranges.size)

    /** Where [word] is drawn (a word never breaks across lines), or null before the first layout. */
    fun rect(word: Int): Rect? {
        val layout = layout ?: return null
        val range = ranges.getOrNull(word) ?: return null
        rects[word]?.let { return it }
        if (range.last + 1 > layout.layoutInput.text.length) return null
        val bounds = layout.getPathForRange(range.first, range.last + 1).getBounds()
        if (bounds.isEmpty) return null
        rects[word] = bounds
        return bounds
    }

    /** The word at [point], allowing [slack] pixels around the words (a finger is bigger than a letter); null when none is near. */
    fun wordNear(point: Offset, slack: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for (word in ranges.indices) {
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

@Composable
private fun FollowedText(
    words: AyahWords,
    spans: List<WordSpan>,
    follow: WordFollow?,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    textAlign: TextAlign,
    maxLines: Int,
    overflow: TextOverflow,
    tajweed: AnnotatedString?,
    meaning: WordMeaningSelection?,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val controller = koinInject<PlaybackController>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wordLayout = remember(words) { WordLayout(words.ranges) }

    // The word that sounds now: -1 before the first word and when nothing is known. While audio plays the player is asked every
    // 60 ms, and only while the screen is shown; paused, it is read once and stays.
    var active by remember(spans) { mutableIntStateOf(-1) }
    val playing = follow?.playing == true
    LaunchedEffect(spans, playing) {
        if (spans.isEmpty()) {
            // Only the meanings are on this text: nothing sounds in it.
            active = -1
            return@LaunchedEffect
        }
        if (!playing) {
            active = wordAt(spans, controller.positionMs())
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            controller.positions().collect { position -> active = wordAt(spans, position) }
        }
    }

    // A short cross-fade between two words: `from` fades out while `to` fades in.
    var from by remember(spans) { mutableIntStateOf(-1) }
    var to by remember(spans) { mutableIntStateOf(-1) }
    val progress = remember(spans) { Animatable(1f) }
    LaunchedEffect(active, reduced) {
        if (active == to) return@LaunchedEffect
        from = to
        to = active
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = CrossFadeMs, easing = LinearEasing))
        }
    }

    val wash = if (colors.isDark) colors.primary.copy(alpha = DarkWashAlpha) else colors.primary.copy(alpha = LightWashAlpha)
    val tap = follow?.onWordTap
    val textModifier = modifier
        .drawBehind {
            val inset = 2.dp.toPx()
            val radius = CornerRadius(6.dp.toPx())
            fun paint(word: Int, alpha: Float) {
                if (word < 0 || alpha <= 0f) return
                val rect = wordLayout.rect(word) ?: return
                drawRoundRect(
                    color = wash.copy(alpha = wash.alpha * alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(rect.left - inset, rect.top),
                    size = Size(rect.width + inset * 2, rect.height),
                    cornerRadius = radius,
                )
            }
            paint(from, 1f - progress.value)
            paint(to, progress.value)
            // The word whose meaning is shown (F4b): the same wash as the word that is recited.
            meaning?.word?.let { paint(it, 1f) }
        }
        .then(
            if (tap == null && meaning == null) {
                Modifier
            } else {
                Modifier.pointerInput(spans, wordLayout, tap, meaning) {
                    // A quick tap on a word seeks to it (or, with the word meanings on, picks it). Everything else (a tap between
                    // words, a long press, a scroll) is not touched here and reaches the row: the events are only consumed when a
                    // word was hit.
                    val slack = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                        val word = wordLayout.wordNear(up.position, slack) ?: return@awaitEachGesture
                        if (meaning != null) {
                            up.consume()
                            meaning.word = word
                            return@awaitEachGesture
                        }
                        val start = startOfWord(spans, word) ?: return@awaitEachGesture
                        up.consume()
                        tap?.invoke(start)
                    }
                }
            },
        )
    if (meaning == null) {
        TajweedAwareText(
            text = words.text,
            styled = tajweed,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { wordLayout.layout = it },
            modifier = textModifier,
        )
    } else {
        // The popup is a child of this box: it is placed from the box, and the word's rectangle is in the coordinates of the text,
        // which fills the box.
        Box(Modifier.fillMaxWidth()) {
            TajweedAwareText(
                text = words.text,
                styled = tajweed,
                style = style,
                color = color,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = overflow,
                onTextLayout = { wordLayout.layout = it },
                modifier = textModifier.fillMaxWidth(),
            )
            meaning.word?.let { picked ->
                wordLayout.rect(picked)?.let { rect -> WordMeaningPopup(meaning, words.wordCount, rect) }
            }
        }
    }
}

private const val CrossFadeMs = 100
private const val LightWashAlpha = 0.20f
private const val DarkWashAlpha = 0.34f
