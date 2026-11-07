package tj.app.quran_todo.ui.tajweed

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.koin.compose.koinInject
import tj.app.quran_todo.common.tajweed.TajweedSpan
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * [text] with the rules coloured: a colour per span of [spans] (ranges in [text], both ends inclusive), the palette of the light or the
 * dark theme. Only the colour is set: the letters, their order and the font are those of [text], which is never changed.
 */
internal fun tajweedAnnotated(text: String, spans: List<TajweedSpan>, dark: Boolean): AnnotatedString = buildAnnotatedString {
    append(text)
    for (span in spans) {
        var start = span.range.first.coerceIn(0, text.length)
        var end = (span.range.last + 1).coerceIn(start, text.length)
        // A colour must not start or end inside a letter with its sign: the text engine shapes a change of colour as a break, and a dagger
        // alif or a small waw/yeh drawn alone lands in the wrong place (seen on the emulator). So the range takes the letter under a sign
        // it starts with, and the signs that follow its last letter.
        while (start > 0 && start < text.length && isAttached(text[start])) start--
        while (end < text.length && isAttached(text[end])) end++
        if (end > start) addStyle(SpanStyle(color = TajweedPalette.color(span.rule, dark)), start, end)
    }
}

/** Harakat, Quranic signs, the dagger alif and the small waw and yeh: what is drawn on or after the letter before it. */
private fun isAttached(c: Char): Boolean {
    val code = c.code
    return code in 0x064B..0x065F || code in 0x0610..0x061A || code in 0x06D6..0x06ED && code != 0x06DD && code != 0x06DE && code != 0x06E9 ||
        code == 0x0670
}

/**
 * The coloured text of ayah [ayah] of [surah], or null when it is drawn plain: the setting is off, the markup is not on the device (yet),
 * or the ayah has no rules. [text] is what the screen draws (after `splitBismillah`): the rules are moved onto exactly that text.
 *
 * With the setting on, the surah is asked for here (the file is fetched the first time, a surah is read and parsed in the background);
 * rows that are already in the list pick the colours up when they arrive, nothing blocks.
 */
@Composable
internal fun rememberTajweedText(surah: Int, ayah: Int, text: String): AnnotatedString? {
    val enabled by koinInject<UiPrefs>().tajweed.collectAsState()
    if (!enabled) return null
    val store = TajweedStore.shared
    val availability by store.availability.collectAsState()
    val version by store.version.collectAsState()
    val dark = QhTheme.colors.isDark
    LaunchedEffect(surah, availability) { store.prepare(surah) }
    return remember(version, surah, ayah, text, dark) {
        store.spansIfReady(surah, ayah, text)?.takeIf { it.isNotEmpty() }?.let { tajweedAnnotated(text, it, dark) }
    }
}

/**
 * The Arabic text of an ayah with the tajweed colours when the setting is on; plain, exactly as before, when it is off. For rows that
 * have no word-by-word highlight (the ones that do get the colours through `FollowedArabicText`).
 */
@Composable
fun TajweedArabicText(
    surah: Int,
    ayahInSurah: Int,
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val styled = rememberTajweedText(surah, ayahInSurah, text)
    if (styled == null) {
        Text(text = text, style = style, color = color, textAlign = textAlign, modifier = modifier)
    } else {
        Text(text = styled, style = style, color = color, textAlign = textAlign, modifier = modifier)
    }
}

/** Text for the places that already have the [AnnotatedString] (or not): the plain text when [styled] is null. */
@Composable
internal fun TajweedAwareText(
    text: String,
    styled: AnnotatedString?,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    textAlign: TextAlign,
    maxLines: Int,
    overflow: TextOverflow,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    if (styled == null) {
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout ?: {},
            modifier = modifier,
        )
    } else {
        Text(
            text = styled,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout ?: {},
            modifier = modifier,
        )
    }
}
