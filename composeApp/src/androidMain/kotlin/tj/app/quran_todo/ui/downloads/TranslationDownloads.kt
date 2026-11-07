package tj.app.quran_todo.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.i18n.DownloadsCopy
import tj.app.quran_todo.ui.i18n.TranslationCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** What the buttons of a translation row do; the screen fills it with calls to the view model. */
internal class TranslationActions(
    val download: (TranslationRowUi) -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val cancel: (TranslationRowUi) -> Unit,
    val delete: (TranslationRowUi) -> Unit,
)

/**
 * A row of the section "Translations" (task F4a): the name and the author of the edition, and under them "Download whole · ≈ 0.4 MB",
 * "Downloading…", "In the queue" or "Downloaded". The same buttons as the other rows: download, pause, resume, cancel, delete.
 */
@Composable
internal fun TranslationDownloadRow(
    row: TranslationRowUi,
    copy: DownloadsCopy,
    text: TranslationCopy,
    actions: TranslationActions,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val state = translationLine(row, copy, text)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = QhSpacing.screen, end = QhSpacing.x2, top = QhSpacing.x2, bottom = QhSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconTile(Icons.Rounded.Translate, tint = colors.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(QhSpacing.x3))
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "${row.name}, ${state.text}" },
            ) {
                Text(text = row.name, style = QhTheme.type.title2, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (row.author != row.name) {
                    Text(text = row.author, style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.done) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.learned,
                            modifier = Modifier.size(QhSpacing.iconInline - 4.dp),
                        )
                    }
                    Text(
                        text = state.text,
                        style = QhTheme.type.callout.tabular(),
                        color = if (state.failed) colors.danger else colors.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TranslationButtons(row, copy, actions)
        }
        QhRowDivider(startIndent = QhSpacing.screen + 40.dp + QhSpacing.x3)
    }
}

private class TranslationLine(val text: String, val done: Boolean = false, val failed: Boolean = false)

private fun translationLine(row: TranslationRowUi, copy: DownloadsCopy, text: TranslationCopy): TranslationLine {
    val onDevice = if (row.complete && row.updateAvailable) {
        TranslationLine(text.updateAvailable)
    } else if (row.complete) {
        TranslationLine(text.downloadsComplete, done = true)
    } else {
        TranslationLine(text.offer(copy.sizeLabel(row.wireBytes)))
    }
    return when (row.queue) {
        RowQueue.None -> onDevice
        RowQueue.Waiting -> TranslationLine(copy.rowQueued)
        is RowQueue.Active -> TranslationLine(text.downloadsRunning)
        is RowQueue.Paused -> TranslationLine("${copy.queuePaused} · ${text.downloadsRunning}")
        RowQueue.Failed -> TranslationLine(text.downloadsFailed, failed = true)
    }
}

@Composable
private fun TranslationButtons(row: TranslationRowUi, copy: DownloadsCopy, actions: TranslationActions) {
    val colors = QhTheme.colors
    @Composable
    fun button(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = colors.ink, onClick: () -> Unit) {
        QhIconButton(icon = icon, contentDescription = "$label: ${row.name}", onClick = onClick, filled = false, tint = tint)
    }
    when (row.queue) {
        is RowQueue.Active -> button(Icons.Rounded.Pause, copy.actionPause, colors.primary, actions.pause)
        is RowQueue.Paused -> {
            button(Icons.Rounded.PlayArrow, copy.actionResume, colors.primary, actions.resume)
            button(Icons.Rounded.Close, copy.actionCancel, colors.inkSecondary) { actions.cancel(row) }
        }
        RowQueue.Waiting -> button(Icons.Rounded.Close, copy.actionCancel, colors.inkSecondary) { actions.cancel(row) }
        RowQueue.Failed -> button(Icons.Rounded.Download, copy.actionDownload, colors.danger) { actions.download(row) }
        RowQueue.None ->
            if (row.complete && row.updateAvailable) {
                button(Icons.Rounded.Download, copy.actionDownload, colors.primary) { actions.download(row) }
                button(Icons.Rounded.Delete, copy.actionDelete, colors.inkSecondary) { actions.delete(row) }
            } else if (row.complete) {
                button(Icons.Rounded.Delete, copy.actionDelete, colors.inkSecondary) { actions.delete(row) }
            } else {
                button(Icons.Rounded.Download, copy.actionDownload, colors.primary) { actions.download(row) }
            }
    }
}
