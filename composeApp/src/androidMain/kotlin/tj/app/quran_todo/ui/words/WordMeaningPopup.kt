package tj.app.quran_todo.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import tj.app.quran_todo.common.words.WordLookup
import tj.app.quran_todo.common.words.WordMeaningsChapter
import tj.app.quran_todo.ui.i18n.TafsirCopy
import tj.app.quran_todo.ui.i18n.rememberTafsirCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The word of ayah [ayah] of [surah] that was tapped, while "Word meanings" is on (task F4b): [word] is its index in the words of the
 * screen (`displayWords`), null while no word is picked. The text of the ayah draws the picked word with the wash of the word-by-word
 * highlight and shows [WordMeaningPopup] over it.
 */
@Stable
class WordMeaningSelection(val surah: Int, val ayah: Int) {
    var word: Int? by mutableStateOf(null)
}

/** What the popup has to show for the surah: the meanings are coming, are there, or neither the phone nor the network has them. */
@Immutable
internal sealed interface MeaningsLoad {
    data object Loading : MeaningsLoad

    data class Ready(val chapter: WordMeaningsChapter) : MeaningsLoad

    data object Missing : MeaningsLoad
}

/**
 * The popup over a tapped word: the meaning in English, the transliteration and "Word 3 of 7 · in English"; "Loading the meanings…"
 * while the surah is fetched the first time, "An internet connection is needed once for this surah" with "Retry" when it cannot be, and
 * "Word meanings are not available for this ayah" for the ayahs whose words do not line up with the source (10:1, 13:1, 50:34 and any
 * other whose count differs). A tap outside the popup closes it (and goes on to what is under it).
 *
 * [anchor] is where the word is drawn, in the coordinates of the parent of this popup (the text of the ayah).
 */
@Composable
internal fun WordMeaningPopup(
    selection: WordMeaningSelection,
    wordCount: Int,
    anchor: Rect,
    modifier: Modifier = Modifier,
    store: WordMeaningsStore = WordMeaningsStore.shared,
) {
    val word = selection.word ?: return
    val copy = rememberTafsirCopy()
    var attempt by remember(selection.surah) { mutableIntStateOf(0) }
    val load by produceState<MeaningsLoad>(MeaningsLoad.Loading, selection.surah, attempt) {
        val local = store.local(selection.surah)
        if (local != null) {
            value = MeaningsLoad.Ready(local)
            return@produceState
        }
        value = MeaningsLoad.Loading
        val chapter = store.load(selection.surah, force = attempt > 0)
        value = if (chapter != null) MeaningsLoad.Ready(chapter) else MeaningsLoad.Missing
    }
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val margin = with(LocalDensity.current) { QhSpacing.x2.roundToPx() }
    val position = remember(anchor, gap, margin) {
        AboveWordPosition(IntRect(anchor.left.toInt(), anchor.top.toInt(), anchor.right.toInt(), anchor.bottom.toInt()), gap, margin)
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { selection.word = null },
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        WordMeaningCard(
            load = load,
            surah = selection.surah,
            ayah = selection.ayah,
            word = word,
            wordCount = wordCount,
            copy = copy,
            onRetry = { attempt++ },
            modifier = modifier,
        )
    }
}

@Composable
internal fun WordMeaningCard(
    load: MeaningsLoad,
    surah: Int,
    ayah: Int,
    word: Int,
    wordCount: Int,
    copy: TafsirCopy,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    Column(
        modifier
            .widthIn(min = 120.dp, max = maxWidth)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = QhSpacing.x4, vertical = QhSpacing.x3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (load) {
            MeaningsLoad.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = colors.primary, strokeWidth = 2.dp)
                Text(copy.wordsLoading, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
            MeaningsLoad.Missing -> {
                Text(copy.wordsNeedInternet, style = QhTheme.type.callout, color = colors.inkSecondary)
                Text(
                    text = copy.retry,
                    style = QhTheme.type.callout,
                    color = colors.primary,
                    modifier = Modifier
                        .heightIn(min = QhSpacing.minTouch)
                        .pressable(onClick = onRetry, shape = QhTheme.shapes.sm, onClickLabel = copy.retry)
                        .padding(horizontal = QhSpacing.x1, vertical = QhSpacing.x3),
                )
            }
            is MeaningsLoad.Ready -> when (val found = load.chapter.lookup(ayah, word, wordCount)) {
                WordLookup.Unavailable -> Text(copy.wordsUnavailable, style = QhTheme.type.callout, color = colors.inkSecondary)
                is WordLookup.Found -> {
                    Text(found.meaning.text, style = QhTheme.type.title2, color = colors.ink)
                    found.meaning.transliteration?.let {
                        Text(it, style = QhTheme.type.callout.copy(fontStyle = FontStyle.Italic), color = colors.inkSecondary)
                    }
                    Text(
                        copy.word(found.number, found.total),
                        style = QhTheme.type.label,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Above the word, centred on it and kept inside the window; below it when there is no room above. */
private class AboveWordPosition(private val word: IntRect, private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centre = anchorBounds.left + word.center.x
        val x = (centre - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = anchorBounds.top + word.top - popupContentSize.height - gap
        val y = if (above >= margin * 4) above else anchorBounds.top + word.bottom + gap
        return IntOffset(x, y)
    }
}
