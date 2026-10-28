package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Height of the bar's button row (below the status bar). */
val ReaderTopBarRowHeight = 56.dp

/**
 * The reader's top bar. Over the surah header it is only the back button and the two actions; once the
 * header cartouche has scrolled under it, [collapsed] reaches 1 and the paper-colored backdrop, a hairline,
 * the surah name with "Ayah N / M" and the 2dp reading progress appear.
 *
 * [collapsed], [ayahNumber] and [progress] are read inside draw/graphics blocks or leaf composables, so
 * scrolling does not recompose the bar.
 */
@Composable
fun ReaderTopBar(
    surahName: String,
    totalAyahs: Int,
    collapsed: State<Float>,
    ayahNumber: State<Int>,
    progress: State<Float>,
    copy: ReaderCopy,
    onBack: () -> Unit,
    onTextSettings: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            // The bar sits over the list: taps on its empty part must not reach the ayahs behind it.
            .pointerInput(Unit) { }
            .drawBehind {
                val fraction = collapsed.value
                if (fraction > 0f) {
                    drawRect(colors.readerBg.copy(alpha = 0.96f * fraction))
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
                Text(
                    text = surahName,
                    style = QhTheme.type.title2,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                CompactAyahCounter(ayahNumber = ayahNumber, totalAyahs = totalAyahs, copy = copy)
            }
            QhIconButton(
                icon = Icons.Rounded.TextFields,
                contentDescription = copy.textSettings,
                onClick = onTextSettings,
                filled = false,
            )
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

/** "Ayah 12 / 286" where the number rolls; it is the only part that recomposes while reading. */
@Composable
private fun CompactAyahCounter(ayahNumber: State<Int>, totalAyahs: Int, copy: ReaderCopy) {
    val style = QhTheme.type.callout
    val color = QhTheme.colors.inkSecondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${copy.ayah} ", style = style, color = color)
        AnimatedCounter(value = ayahNumber.value, style = style, color = color)
        Text(" / $totalAyahs", style = style, color = color)
    }
}

@Composable
private fun ReadingProgress(progress: State<Float>) {
    QhLinearProgress(progress = progress.value, height = 2.dp)
}
