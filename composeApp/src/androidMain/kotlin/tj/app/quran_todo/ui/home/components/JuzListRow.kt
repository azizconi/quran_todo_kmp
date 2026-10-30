package tj.app.quran_todo.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.home.JuzRowUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.MushafCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * A row of the "Juz" tab, in the manner of [SurahListRow]: the numbered medallion (the same petal medallion as the
 * surahs: the ring carries the memorized share, a solid medallion means the whole juz), "Juz 30" with its Arabic name
 * (the opening words) on the right, "An-Naba 1 — An-Nas 6 · 564 ayahs" and, once something is memorized, "Memorized 120 of 564
 * ayahs". A tap opens the reader at the start of the juz. No shared element: the keys of the reader's header belong to
 * a surah, a juz has none, so the screen changes the ordinary way.
 */
@Composable
internal fun JuzListRow(
    row: JuzRowUi,
    copy: HomeCopy,
    mushaf: MushafCopy,
    arabicStyle: TextStyle,
    onOpen: (JuzRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val title = remember(row.juz, mushaf) { mushaf.juzLabel(row.juz) }
    val learnedLine = remember(row.coveredCount, row.ayahCount, copy) {
        if (row.coveredCount > 0) copy.coverageLine(row.coveredCount, row.ayahCount, 0) else null
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(
                    onClick = { onOpen(row) },
                    scale = RowPressScale,
                    enabled = row.canOpen,
                    onClickLabel = copy.open,
                )
                .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x3)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append(title)
                        append(", ").append(row.subtitle)
                        if (learnedLine != null) append(", ").append(learnedLine)
                    }
                    stateDescription = learnedLine ?: copy.statusNotStarted
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(BadgeSize), contentAlignment = Alignment.Center) {
                SurahNumberBadge(
                    number = row.juz,
                    status = row.status,
                    learnedFraction = row.fraction,
                    size = BadgeSize,
                )
            }
            Spacer(Modifier.width(BadgeGap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = type.title2,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // "Al-Baqarah 253 — Ali 'Imran 92 · 126 ayahs" is long next to the Arabic name: it wraps instead of being cut off.
                Text(
                    text = row.subtitle,
                    style = type.callout,
                    color = colors.inkSecondary,
                    maxLines = SubtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (learnedLine != null) {
                    Text(
                        text = learnedLine,
                        style = type.callout,
                        color = colors.primary,
                        maxLines = SubtitleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(QhSpacing.x3))
            Text(
                text = row.arabicName,
                style = arabicStyle,
                color = colors.primary,
                maxLines = 1,
            )
        }
        // The hairline starts under the name, not under the badge.
        Box(
            Modifier
                .padding(start = QhSpacing.screen + BadgeSize + BadgeGap)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}

private val BadgeSize = 40.dp
private val BadgeGap = 14.dp
private const val RowPressScale = 0.985f
private const val SubtitleLines = 2
