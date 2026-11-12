package tj.app.quran_todo.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.home.PlanRowUi
import tj.app.quran_todo.ui.i18n.TabsCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.i18n.rememberTabsCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The words of the row, one string: "Juz Amma · covered 10 of 564 · finish ~2 March" (UX task U15). Pure of Compose. */
internal fun planRowText(row: PlanRowUi, copy: TabsCopy): String {
    val language = copy.language
    val finish = row.finishEpochDay?.let { planCopy(language).dateLabel(it, row.today) }
    return copy.planRow(progressCopy(language).scopeName(row.scope), row.covered, row.total, finish)
}

/**
 * The plan under the lesson card (UX task U15): the name of the plan, how far it is covered and when it ends at this pace, and the
 * word "Change" at the end. The whole row opens "My plan". Coverage, not memorizing: the word is "covered".
 */
@Composable
internal fun PlanRow(row: PlanRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val copy = rememberTabsCopy()
    // The language is read here so a change of it rebuilds the text.
    val language = LocalAppLanguage.current
    val text = remember(row, copy, language) { planRowText(row, copy) }
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = copy.planRowDescription(text) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x2),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = QhSpacing.minTouch - QhSpacing.x4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Text(
                text = text,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = copy.planChange, style = QhTheme.type.title2, color = colors.primary, maxLines = 1)
        }
    }
}
