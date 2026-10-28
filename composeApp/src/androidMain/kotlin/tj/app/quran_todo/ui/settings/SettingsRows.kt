package tj.app.quran_todo.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/**
 * A titled group: an overline header, then one card of rows (zero padding; rows bring their own), and
 * an optional [footnote] in small type under the card.
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title, Modifier.padding(horizontal = QhSpacing.x1))
        Spacer(Modifier.height(QhSpacing.x2))
        QhCard(contentPadding = PaddingValues(0.dp), content = content)
        if (footnote != null) {
            Text(
                text = footnote,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier.padding(start = QhSpacing.x1, end = QhSpacing.x1, top = QhSpacing.x2),
            )
        }
    }
}

/**
 * A settings row with a header (icon tile, [title], an optional [value] on the right) and free
 * [content] below it, for controls too wide for the right side of a row: segments, swatches, chips.
 */
@Composable
internal fun SettingsBlock(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    value: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconTile(icon, tint = tint)
            Text(
                text = title,
                style = QhTheme.type.title2,
                color = QhTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                val reduced = LocalReducedMotion.current
                Crossfade(
                    targetState = value,
                    animationSpec = if (reduced) snap() else tween(QhMotion.Short),
                    label = "block-value",
                ) { shown ->
                    Text(
                        text = shown,
                        style = QhTheme.type.callout.tabular(),
                        color = QhTheme.colors.primary,
                        maxLines = 1,
                    )
                }
            }
        }
        content()
    }
}

/**
 * A row of choice chips right inside the settings card (speed, repeat count): no sheet, one tap.
 * The chosen option's [labelOf] also shows in the block header.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChipChoiceBlock(
    title: String,
    icon: ImageVector,
    tint: Color,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsBlock(
        title = title,
        icon = icon,
        tint = tint,
        modifier = modifier,
        value = labelOf(selected),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                QhChip(
                    label = labelOf(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/**
 * The frame of a settings row with a custom [body] in the middle, laid out exactly like the design
 * system's rows (16dp inset, 12dp gaps, 56dp minimum height, 32dp icon tile).
 */
@Composable
internal fun SettingsRowFrame(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QhIconTile(icon, tint = tint)
        Column(Modifier.weight(1f), content = body)
        if (trailing != null) trailing()
    }
}

/**
 * A row with a stepper on the right. The description sits under the whole header row, so the title
 * keeps room next to the 136dp stepper.
 */
@Composable
internal fun StepperRow(
    title: String,
    description: String?,
    icon: ImageVector,
    tint: Color,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.card)
            .padding(top = QhSpacing.x2, bottom = QhSpacing.x3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconTile(icon, tint = tint)
            Text(
                text = title,
                style = QhTheme.type.title2,
                color = QhTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            ValueStepper(
                value = value,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                canDecrease = canDecrease,
                canIncrease = canIncrease,
                decreaseLabel = decreaseLabel,
                increaseLabel = increaseLabel,
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier.padding(start = 32.dp + QhSpacing.x3, end = QhSpacing.x1),
            )
        }
    }
}

/** What the offline-audio package is doing, as the row shows it. */
internal sealed interface OfflineUi {
    data object Idle : OfflineUi
    data class Running(val done: Int, val total: Int) : OfflineUi
    data object Ready : OfflineUi
    data object Partial : OfflineUi
}

private enum class OfflineKind { Idle, Running, Ready, Partial }

private class LastRunning {
    var value: OfflineUi.Running = OfflineUi.Running(0, 0)
}

private fun OfflineUi.kind() = when (this) {
    OfflineUi.Idle -> OfflineKind.Idle
    is OfflineUi.Running -> OfflineKind.Running
    OfflineUi.Ready -> OfflineKind.Ready
    OfflineUi.Partial -> OfflineKind.Partial
}

/**
 * "Download for offline" with its progress inline: while the package downloads the description
 * turns into a counter and a [QhLinearProgress], the row ignores taps; when it ends the row says
 * whether everything arrived.
 */
@Composable
internal fun OfflineDownloadRow(
    state: OfflineUi,
    copy: SettingsCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val running = state is OfflineUi.Running
    val icon: ImageVector
    val tint: Color
    when (state) {
        OfflineUi.Ready -> {
            icon = Icons.Rounded.CheckCircle
            tint = colors.learned
        }
        OfflineUi.Partial -> {
            icon = Icons.Rounded.ErrorOutline
            tint = colors.due
        }
        else -> {
            icon = Icons.Rounded.Download
            tint = colors.primary
        }
    }
    val reduced = LocalReducedMotion.current
    // The counter keeps its last numbers while the "running" text fades out after the download ended.
    val lastRunning = remember { LastRunning() }
    if (state is OfflineUi.Running) lastRunning.value = state

    SettingsRowFrame(
        icon = icon,
        tint = tint,
        modifier = modifier
            .pressable(onClick = onClick, enabled = !running, scale = 0.99f)
            .animateContentSize(QhMotion.gentle<IntSize>().orSnap()),
        trailing = if (running || state == OfflineUi.Ready) {
            null
        } else {
            {
                Text(
                    text = copy.offlineGet,
                    style = QhTheme.type.label,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(QhTheme.shapes.full)
                        .background(colors.primaryContainer)
                        .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2),
                )
            }
        },
    ) {
        Text(
            text = copy.offlineTitle,
            style = QhTheme.type.title2,
            color = if (running) colors.inkSecondary else colors.ink,
        )
        AnimatedContent(
            targetState = state.kind(),
            transitionSpec = {
                if (reduced) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn(tween(QhMotion.Medium)) togetherWith fadeOut(tween(QhMotion.Short))
                }
            },
            label = "offline-body",
        ) { kind ->
            when (kind) {
                OfflineKind.Idle -> Text(
                    text = copy.offlineDescription,
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                )
                OfflineKind.Running -> {
                    val done = lastRunning.value.done
                    val total = lastRunning.value.total
                    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                        Text(
                            text = copy.offlineProgressLabel(done, total),
                            style = QhTheme.type.callout.tabular(),
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        QhLinearProgress(
                            progress = if (total > 0) done.toFloat() / total else 0f,
                            modifier = Modifier.padding(bottom = QhSpacing.x1),
                        )
                    }
                }
                OfflineKind.Ready -> Text(
                    text = copy.offlineReady,
                    style = QhTheme.type.callout,
                    color = colors.learned,
                )
                OfflineKind.Partial -> Text(
                    text = copy.offlinePartial,
                    style = QhTheme.type.callout,
                    color = colors.due,
                )
            }
        }
    }
}
