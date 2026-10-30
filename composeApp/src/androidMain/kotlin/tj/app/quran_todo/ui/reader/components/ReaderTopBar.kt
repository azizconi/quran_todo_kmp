package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.i18n.MushafCopy
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.reader.MushafPlace
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Height of the bar's button row (below the status bar). */
val ReaderTopBarRowHeight = 56.dp

/**
 * The reader's top bar. Over the surah header it is only the back button and the two actions; once the
 * header cartouche has scrolled under it, [collapsed] reaches 1 and a hairline, the surah name with the ayah on top
 * ("12 / 286"), the place in the mushaf under it ("Juz 2 · Hizb 3 · p. 22") and the 2dp reading progress appear. The
 * bar itself is always opaque paper, status bar included.
 *
 * [collapsed], [ayahNumber], [place] and [progress] are read inside draw/graphics blocks or leaf composables, so
 * scrolling does not recompose the bar; [place] is a derived state, so the line only changes when the juz, the hizb or the
 * page of the top ayah really does.
 */
@Composable
fun ReaderTopBar(
    surahName: String,
    totalAyahs: Int,
    collapsed: State<Float>,
    ayahNumber: State<Int>,
    place: State<MushafPlace?>,
    progress: State<Float>,
    copy: ReaderCopy,
    mushaf: MushafCopy,
    onBack: () -> Unit,
    onTextSettings: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** An extra action between "Aa" and "more" (the eye of the memorize mode); null leaves the bar as it was. */
    extraAction: (@Composable () -> Unit)? = null,
    /** Goes on the "Aa" button (the spotlight tour points at it). */
    textSettingsModifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            // The bar sits over the list: taps on its empty part must not reach the ayahs behind it.
            .pointerInput(Unit) { }
            .drawBehind {
                // Opaque paper over the whole bar including the status bar inset: the header scrolls
                // under it and must not show through. Only the hairline and the title fade in.
                drawRect(colors.readerBg)
                val fraction = collapsed.value
                if (fraction > 0f) {
                    drawLine(
                        color = colors.hairline.copy(alpha = fraction),
                        start = Offset(0f, size.height - 0.5.dp.toPx()),
                        end = Offset(size.width, size.height - 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ReaderTopBarRowHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = copy.back,
                onClick = onBack,
                filled = false,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = QhSpacing.x1)
                    .graphicsLayer { alpha = collapsed.value },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = surahName,
                        style = QhTheme.type.title2,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .semantics { heading() },
                    )
                    Spacer(Modifier.width(QhSpacing.x2))
                    CompactAyahCounter(ayahNumber = ayahNumber, totalAyahs = totalAyahs, copy = copy)
                }
                PlaceLine(place = place, mushaf = mushaf)
            }
            QhIconButton(
                icon = Icons.Rounded.TextFields,
                contentDescription = copy.textSettings,
                onClick = onTextSettings,
                filled = false,
                modifier = textSettingsModifier,
            )
            extraAction?.invoke()
            QhIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = copy.more,
                onClick = onMore,
                filled = false,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer { alpha = collapsed.value },
        ) {
            ReadingProgress(progress)
        }
    }
}

/** "12 / 286" (the ayah on top) where the number rolls; it recomposes while reading, so the bar stays still. */
@Composable
private fun CompactAyahCounter(ayahNumber: State<Int>, totalAyahs: Int, copy: ReaderCopy) {
    val style = QhTheme.type.callout
    val color = QhTheme.colors.inkSecondary
    val number = ayahNumber.value
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = copy.ayahOf(number, totalAyahs) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCounter(value = number, style = style, color = color)
        Text(" / $totalAyahs", style = style, color = color, maxLines = 1)
    }
}

/**
 * "Juz 2 · Hizb 3 · p. 22" for the ayah on top. [place] is a derived state of the scroll position: this leaf is the only
 * thing that recomposes, and only when the value really changed (a data class compares equal), so scrolling inside one
 * page does not touch it.
 */
@Composable
private fun PlaceLine(place: State<MushafPlace?>, mushaf: MushafCopy) {
    val current = place.value ?: return
    val text = remember(current, mushaf) { mushaf.placeText(current) }
    Text(
        text = text,
        style = QhTheme.type.callout,
        color = QhTheme.colors.inkSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ReadingProgress(progress: State<Float>) {
    QhLinearProgress(progress = progress.value, height = 2.dp)
}
