package tj.app.quran_todo.ui.progress.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.i18n.AnalyticsCopy
import tj.app.quran_todo.ui.theme.QhTheme

/** Two rows in one card: the detailed analytics ("In detail") and "My notes" (task F5a). */
@Composable
internal fun DetailsEntries(
    copy: AnalyticsCopy,
    onOpenDetails: () -> Unit,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QhCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        Column {
            QhNavRow(
                title = copy.detailsTitle,
                description = copy.detailsBody,
                onClick = onOpenDetails,
                icon = Icons.Rounded.Insights,
                iconTint = QhTheme.colors.primary,
            )
            QhRowDivider()
            QhNavRow(
                title = copy.notesRowTitle,
                description = copy.notesRowBody,
                onClick = onOpenNotes,
                icon = Icons.Rounded.EditNote,
                iconTint = QhTheme.colors.gold,
            )
        }
    }
}
