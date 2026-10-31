package tj.app.quran_todo.ui.downloads

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Sd
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.i18n.DownloadsCopy
import tj.app.quran_todo.ui.i18n.ayahCountLabel
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.settings.nativeName
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** What the buttons of a row do; the screen fills it with calls to the view model. */
internal class RowActions(
    val download: (RowUi) -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val cancel: (RowUi) -> Unit,
    val delete: (RowUi) -> Unit,
)

/** The reciter (changed in the sound settings), "Wi-Fi only" and "With translation": the options of every download. */
@Composable
internal fun OptionsCard(
    ui: DownloadsUi,
    copy: DownloadsCopy,
    language: AppLanguage,
    onWifiOnly: (Boolean) -> Unit,
    onWithTranslation: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    QhCard(modifier, contentPadding = PaddingValues(0.dp)) {
        QhValueRow(
            title = copy.reciter,
            value = ui.reciter.shortName(language),
            description = copy.reciterHint,
            icon = Icons.Rounded.RecordVoiceOver,
            iconTint = colors.gold,
        )
        QhRowDivider()
        QhSwitchRow(
            title = copy.wifiOnly,
            description = copy.wifiOnlyHint,
            checked = ui.wifiOnly,
            onCheckedChange = onWifiOnly,
            icon = Icons.Rounded.Wifi,
            iconTint = colors.primary,
        )
        QhRowDivider()
        QhSwitchRow(
            title = copy.withTranslationLabel(language.nativeName()),
            description = copy.withTranslationHint,
            checked = ui.withTranslation,
            onCheckedChange = onWithTranslation,
            icon = Icons.Rounded.Translate,
            iconTint = colors.due,
        )
    }
}

/**
 * Everything that was asked for is one queue: what is downloading and how far, how much is left, the failures with "Retry", and
 * "Pause all" / "Resume" / "Cancel".
 */
@Composable
internal fun QueueCard(
    queue: QueueUi,
    wifiOnly: Boolean,
    copy: DownloadsCopy,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val failure = copy.failureLabel(queue.failedAyahs, queue.failedTranslations)
    val status = when {
        !queue.hasActive -> null
        queue.paused -> copy.queuePaused
        queue.phase == WorkPhase.WAITING -> if (wifiOnly) copy.queueWaitingWifi else copy.queueWaitingNetwork
        else -> null
    }
    QhCard(modifier.animateContentSize(QhMotion.gentle<IntSize>().orSnap())) {
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            if (queue.hasActive) {
                Text(
                    text = copy.queueTitleLabel(queue.title, queue.done, queue.total),
                    style = QhTheme.type.title2.tabular(),
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                QhLinearProgress(progress = if (queue.total > 0) queue.done.toFloat() / queue.total else 0f)
                val details = listOfNotNull(
                    status,
                    queue.remainingBytes?.let { copy.queueRemainingLabel(it) },
                    queue.waiting.takeIf { it > 0 }?.let { copy.queueWaitingLabel(it) },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(text = details, style = QhTheme.type.callout, color = colors.inkSecondary)
                }
            }
            if (failure.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    Text(
                        text = failure,
                        style = QhTheme.type.callout,
                        color = colors.danger,
                        modifier = Modifier.weight(1f),
                    )
                    QhButton(text = copy.retry, onClick = onRetry, variant = QhButtonVariant.Tonal, size = QhButtonSize.S)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalAlignment = Alignment.CenterVertically) {
                if (queue.hasActive) {
                    QhButton(
                        text = if (queue.paused) copy.resume else copy.pauseAll,
                        onClick = if (queue.paused) onResume else onPause,
                        variant = QhButtonVariant.Tonal,
                        size = QhButtonSize.S,
                        icon = if (queue.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    )
                }
                QhButton(text = copy.cancelAll, onClick = onCancelAll, variant = QhButtonVariant.Ghost, size = QhButtonSize.S)
            }
        }
    }
}

/** "Downloads · 54 MB" with "Delete all downloads", "Listening cache · 230 MB" with "Clear", and the free space of the phone. */
@Composable
internal fun StorageCard(
    storage: StorageUi,
    copy: DownloadsCopy,
    onDeleteAll: () -> Unit,
    onClearCache: () -> Unit,
    onDeleteRecordings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    QhCard(modifier, contentPadding = PaddingValues(0.dp)) {
        StorageLine(
            title = copy.storageDownloads,
            value = copy.sizeLabel(storage.downloadsBytes),
            hint = copy.storageDownloadsHint,
            action = copy.storageDeleteAll,
            actionEnabled = storage.downloadsBytes > 0L,
            icon = Icons.Rounded.Download,
            tint = colors.primary,
            onAction = onDeleteAll,
        )
        QhRowDivider()
        StorageLine(
            title = copy.storageCache,
            value = copy.sizeLabel(storage.cacheBytes),
            hint = copy.storageCacheHint,
            action = copy.storageClear,
            actionEnabled = storage.cacheBytes > 0L,
            icon = Icons.Rounded.DeleteSweep,
            tint = colors.gold,
            onAction = onClearCache,
        )
        QhRowDivider()
        StorageLine(
            title = copy.storageRecordings,
            value = copy.sizeLabel(storage.recordingsBytes),
            hint = copy.storageRecordingsHint,
            action = copy.storageDeleteRecordings,
            actionEnabled = storage.recordingsBytes > 0L,
            icon = Icons.Rounded.Mic,
            tint = colors.due,
            onAction = onDeleteRecordings,
        )
        QhRowDivider()
        QhValueRow(
            title = copy.storageFree,
            value = copy.sizeLabel(storage.freeBytes),
            icon = Icons.Rounded.Sd,
            iconTint = colors.learned,
        )
    }
}

@Composable
private fun StorageLine(
    title: String,
    value: String,
    hint: String,
    action: String,
    actionEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onAction: () -> Unit,
) {
    val colors = QhTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
            QhIconTile(icon, tint = tint)
            Column(Modifier.weight(1f)) {
                Text(text = title, style = QhTheme.type.title2, color = colors.ink)
                Text(text = hint, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
            Text(text = value, style = QhTheme.type.title2.tabular(), color = colors.ink, maxLines = 1)
        }
        QhButton(
            text = action,
            onClick = onAction,
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.S,
            enabled = actionEnabled,
            modifier = Modifier.padding(start = 32.dp + QhSpacing.x3),
        )
    }
}

/**
 * One surah, juz or "everything in my plan": the size before downloading ("≈ 54 MB"), then what is on the device ("Downloaded 120 of
 * 564 · 12 MB", "Downloaded · 54 MB") or where it is in the queue, and the button that fits the state: download, pause, resume,
 * remove from the queue, delete.
 */
@Composable
internal fun DownloadRow(
    row: RowUi,
    copy: DownloadsCopy,
    language: AppLanguage,
    arabicStyle: TextStyle,
    actions: RowActions,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val title = rowTitle(row, copy)
    val line = rowLine(row, copy, language)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = QhSpacing.screen, end = QhSpacing.x2, top = QhSpacing.x2, bottom = QhSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowBadge(row)
            Spacer(Modifier.width(QhSpacing.x3))
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "$title, ${line.text}" },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    Text(
                        text = title,
                        style = QhTheme.type.title2,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.arabicName.isNotBlank()) {
                        Text(text = row.arabicName, style = arabicStyle, color = colors.primary, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (line.done) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = colors.learned,
                            modifier = Modifier.size(QhSpacing.iconInline - 4.dp),
                        )
                    }
                    Text(
                        text = line.text,
                        style = QhTheme.type.callout.tabular(),
                        color = if (line.failed) colors.danger else colors.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                line.progress?.let { progress ->
                    Spacer(Modifier.height(QhSpacing.x1))
                    QhLinearProgress(progress = progress, height = 4.dp)
                }
            }
            RowButtons(row, copy, title, actions)
        }
        QhRowDivider(startIndent = QhSpacing.screen + 40.dp + QhSpacing.x3)
    }
}

private class RowLine(val text: String, val done: Boolean = false, val failed: Boolean = false, val progress: Float? = null)

internal fun rowTitle(row: RowUi, copy: DownloadsCopy): String = when (row.kind) {
    DownloadKind.SURAH -> row.name
    DownloadKind.JUZ -> copy.juzTitleLabel(row.number)
    DownloadKind.PLAN -> copy.planAll
}

private fun rowLine(row: RowUi, copy: DownloadsCopy, language: AppLanguage): RowLine {
    fun fraction(done: Int, total: Int) = if (total > 0) done.toFloat() / total else 0f
    // What is known without the files: "≈ 54 MB · 564 ayahs".
    val about = when (row.kind) {
        DownloadKind.SURAH -> ayahCountLabel(row.ayahCount, language)
        DownloadKind.JUZ -> copy.juzSurahsLabel(row.firstSurah, row.lastSurah)
        DownloadKind.PLAN -> copy.planAllSubtitleLabel(row.surahCount)
    }
    val untouched = listOfNotNull(row.estimateBytes?.let(copy::estimateLabel), about).joinToString(" · ")
    val onDevice = when (val state = row.state) {
        is RowState.Complete -> RowLine(copy.rowCompleteLabel(state.bytes), done = true)
        is RowState.Partial -> RowLine(copy.rowPartialLabel(state.done, state.total, state.bytes))
        RowState.NotDownloaded -> RowLine(untouched)
    }
    return when (val queue = row.queue) {
        RowQueue.None -> onDevice
        RowQueue.Waiting -> RowLine(copy.rowQueued)
        is RowQueue.Active -> RowLine(copy.rowDownloadingLabel(queue.done, queue.total), progress = fraction(queue.done, queue.total))
        is RowQueue.Paused -> RowLine(
            "${copy.queuePaused} · ${copy.rowDownloadingLabel(queue.done, queue.total)}",
            progress = fraction(queue.done, queue.total),
        )
        RowQueue.Failed -> RowLine(onDevice.text, failed = true)
    }
}

@Composable
private fun RowBadge(row: RowUi) {
    val colors = QhTheme.colors
    when (row.kind) {
        // The plain medallion: the filled one means "memorized" on the home screen, and downloaded is not that.
        DownloadKind.SURAH -> SurahNumberBadge(number = row.number, status = null, learnedFraction = 0f, size = 40.dp)
        DownloadKind.JUZ -> Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, colors.gold.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = row.number.toString(), style = QhTheme.type.label.tabular(), color = colors.ink, textAlign = TextAlign.Center)
        }
        DownloadKind.PLAN -> QhIconTile(Icons.Rounded.Download, tint = colors.primary, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun RowButtons(row: RowUi, copy: DownloadsCopy, title: String, actions: RowActions) {
    val colors = QhTheme.colors
    @Composable
    fun button(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = colors.ink, onClick: () -> Unit) {
        QhIconButton(
            icon = icon,
            contentDescription = "$label: $title",
            onClick = onClick,
            filled = false,
            tint = tint,
        )
    }
    when (row.queue) {
        is RowQueue.Active -> button(Icons.Rounded.Pause, copy.actionPause, colors.primary, actions.pause)
        is RowQueue.Paused -> {
            button(Icons.Rounded.PlayArrow, copy.actionResume, colors.primary, actions.resume)
            button(Icons.Rounded.Close, copy.actionCancel, colors.inkSecondary) { actions.cancel(row) }
        }
        RowQueue.Waiting -> button(Icons.Rounded.Close, copy.actionCancel, colors.inkSecondary) { actions.cancel(row) }
        RowQueue.Failed -> button(Icons.Rounded.Download, copy.actionDownload, colors.danger) { actions.download(row) }
        RowQueue.None -> when (row.state) {
            RowState.NotDownloaded -> button(Icons.Rounded.Download, copy.actionDownload, colors.primary) { actions.download(row) }
            is RowState.Partial -> {
                button(Icons.Rounded.Download, copy.actionDownload, colors.primary) { actions.download(row) }
                button(Icons.Rounded.Delete, copy.actionDelete, colors.inkSecondary) { actions.delete(row) }
            }
            is RowState.Complete -> button(Icons.Rounded.Delete, copy.actionDelete, colors.inkSecondary) { actions.delete(row) }
        }
    }
}

/** A question with one danger button on top and a quiet cancel under it. */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.xl
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, shape)
                .padding(QhSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            QhIconTile(Icons.Rounded.DeleteSweep, tint = colors.danger)
            Text(text = title, style = QhTheme.type.title1, color = colors.ink, textAlign = TextAlign.Center)
            Text(text = message, style = QhTheme.type.body, color = colors.inkSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(QhSpacing.x1))
            QhButton(
                text = confirm,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Danger,
                size = QhButtonSize.M,
            )
            QhButton(
                text = cancel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }
}
