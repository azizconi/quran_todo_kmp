package tj.app.quran_todo.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.i18n.rememberQuizCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * "Check · 5 questions on what you know" (task F2b): the row under the card "Today's lesson" that opens the quiz. A separate calm row,
 * not part of the lesson: checking yourself is a help, it counts for nothing in the lesson, the circle or the streak, and it stays
 * available while the plan is paused.
 */
@Composable
internal fun QuizRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val copy = rememberQuizCopy()
    val subtitle = copy.entrySubtitle(QuizBuilder.DEFAULT_COUNT)
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${copy.title} · $subtitle" },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            QhIconTile(Icons.Rounded.Quiz, tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text(copy.title, style = QhTheme.type.title2, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(QhSpacing.iconBar),
            )
        }
    }
}
