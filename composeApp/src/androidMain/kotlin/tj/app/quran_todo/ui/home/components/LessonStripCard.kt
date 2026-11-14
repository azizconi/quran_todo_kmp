package tj.app.quran_todo.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.home.LessonStripUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The compact "Today's lesson" card on top of the Quran tab: the overline "Today's lesson", one line of state ("New: Al-Baqarah 1-5 ·
 * ~14 min", or "Lesson done" with a check) and, while there is something to do, a small button that opens the lesson. A tap on the
 * card itself opens the Lesson tab.
 */
@Composable
internal fun LessonStripCard(
    strip: LessonStripUi,
    copy: HomeCopy,
    onOpenLesson: () -> Unit,
    onOpenLessonTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${copy.lessonTitle}. ${strip.text}" },
        onClick = onOpenLessonTab,
        contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
                Text(copy.lessonTitle.uppercase(), style = QhTheme.type.overline, color = colors.primary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    if (strip.done) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.learned, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = strip.text,
                        style = QhTheme.type.callout,
                        color = if (strip.done) colors.inkSecondary else colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            strip.action?.let { label ->
                QhButton(text = label, onClick = onOpenLesson, size = QhButtonSize.S)
            }
        }
    }
}
