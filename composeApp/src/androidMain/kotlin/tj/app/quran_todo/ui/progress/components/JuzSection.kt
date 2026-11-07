package tj.app.quran_todo.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.progress.JuzUi
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** Room for "100%" at the largest font the layout is checked at, so that the bars of all the cells are equally long. */
private val PercentWidth = 48.dp

/** Above this font scale the juz go one to a row: two columns would squeeze the names. */
private const val SingleColumnFontScale = 1.15f

/**
 * Progress by juz: thirty thin bars, each the share of the juz that is covered ("Juz 30", its traditional Arabic name, the
 * percentage). A juz starts inside a surah, so the share is counted over the covered ayahs (`HifzRepository.coveredAyahs()` and
 * `JuzIndex.coveredCount`), not over the statuses of surahs. A tap opens the reader at the start of the juz.
 */
@Composable
internal fun JuzSection(
    juz: List<JuzUi>,
    copy: ProgressCopy,
    onOpen: (JuzUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val columns = if (LocalDensity.current.fontScale > SingleColumnFontScale) 1 else 2
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.juzTitle)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.juzHint, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        QhCard(contentPadding = PaddingValues(horizontal = QhSpacing.x2, vertical = QhSpacing.x2)) {
            juz.chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    rowItems.forEach { item ->
                        JuzCell(item = item, copy = copy, onClick = { onOpen(item) }, modifier = Modifier.weight(1f))
                    }
                    // An odd count leaves a hole in the last row: keep the cell the size of the others.
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun JuzCell(
    item: JuzUi,
    copy: ProgressCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val shape = QhTheme.shapes.sm
    val arabic = type.quranInline(getQuranFontFamily(), 17.sp)
    val description = remember(item.juz, item.percent, copy) { copy.juzDescription(item.juz, item.percent) }
    Column(
        modifier
            .heightIn(min = QhSpacing.minTouch)
            .pressable(onClick = onClick, shape = shape, role = Role.Button)
            .padding(horizontal = QhSpacing.x2, vertical = QhSpacing.x2)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        verticalArrangement = Arrangement.Center,
    ) {
        // The name of the juz and its traditional Arabic name on one line, the bar under them at the full width and the
        // percentage at its end: every bar of a column is as long as the others, whatever the Arabic name is.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = copy.juzName(item.juz),
                style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.arabicName,
                style = arabic,
                color = colors.inkSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = QhSpacing.x2),
            )
        }
        Spacer(Modifier.height(QhSpacing.x1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QhLinearProgress(
                progress = item.fraction,
                modifier = Modifier.weight(1f),
                height = 6.dp,
                color = if (item.percent >= 100) colors.learned else colors.primary,
                trackColor = colors.surfaceMuted,
            )
            Text(
                text = copy.percentText(item.percent),
                style = type.label.tabular(),
                color = if (item.percent >= 100) colors.learned else colors.inkSecondary,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = QhSpacing.x2)
                    .widthIn(min = PercentWidth),
            )
        }
    }
}
