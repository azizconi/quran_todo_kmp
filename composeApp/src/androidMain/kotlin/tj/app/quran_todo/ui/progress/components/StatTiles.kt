package tj.app.quran_todo.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.rememberDrillCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.progress.TilesUi
import tj.app.quran_todo.ui.progress.TodayUi
import tj.app.quran_todo.ui.progress.rememberRollingValue
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/**
 * The four tiles in a 2x2 grid: the day streak, today's lesson ("Lesson done" / "2 of 5 left"), the retention of the last
 * 30 days (the share of repetitions that were not forgotten) and the difficult ayahs right now. The numbers roll up from 0
 * when the tiles appear; the two tiles of a row are equally high at any font size.
 */
@Composable
internal fun StatTiles(
    tiles: TilesUi,
    copy: ProgressCopy,
    onOpenDrill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val drill = rememberDrillCopy()
    val today = tiles.today
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        TileRow {
            NumberTile(
                icon = Icons.Rounded.LocalFireDepartment,
                tint = colors.gold,
                value = tiles.streakDays,
                label = copy.tileStreak,
                delayMillis = TileStartMillis,
            )
            TextTile(
                icon = if (today is TodayUi.Done) Icons.Rounded.CheckCircle else Icons.Rounded.Replay,
                tint = if (today is TodayUi.Done) colors.learned else colors.primary,
                text = copy.todayText(today),
                label = copy.tileToday,
            )
        }
        TileRow {
            RetentionTile(tiles = tiles, copy = copy, delayMillis = TileStartMillis + 2 * TileStepMillis)
            NumberTile(
                icon = Icons.Rounded.PriorityHigh,
                tint = colors.due,
                value = tiles.hardAyahs,
                label = copy.tileHard,
                delayMillis = TileStartMillis + 3 * TileStepMillis,
                // With difficult ayahs the tile opens their drill (task F2a).
                action = if (tiles.hardAyahs > 0) TileAction(drill.tileTrain, onOpenDrill) else null,
            )
        }
    }
}

/** Two tiles of equal width and equal height (the taller one decides). */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        content = content,
    )
}

/** A tile whose value is a count that rolls up. */
@Composable
private fun RowScope.NumberTile(
    icon: ImageVector,
    tint: Color,
    value: Int,
    label: String,
    delayMillis: Long,
    action: TileAction? = null,
) {
    val shown = rememberRollingValue(target = value, startDelayMillis = delayMillis)
    TileFrame(icon = icon, tint = tint, label = label, description = "$label: $value", action = action) {
        AnimatedCounter(value = shown, style = QhTheme.type.headline, color = QhTheme.colors.ink)
    }
}

/** A tile whose value is a short sentence ("Lesson done", "2 of 5 left"). */
@Composable
private fun RowScope.TextTile(
    icon: ImageVector,
    tint: Color,
    text: String,
    label: String,
) {
    TileFrame(icon = icon, tint = tint, label = label, description = "$label: $text") {
        Text(
            text = text,
            style = QhTheme.type.title1,
            color = QhTheme.colors.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The retention: a rolling percentage with the sign, or a dash while no repetition has been done in 30 days. */
@Composable
private fun RowScope.RetentionTile(tiles: TilesUi, copy: ProgressCopy, delayMillis: Long) {
    val colors = QhTheme.colors
    val percent = tiles.retentionPercent
    val shown = rememberRollingValue(target = percent ?: 0, startDelayMillis = delayMillis)
    TileFrame(
        icon = Icons.Rounded.Psychology,
        tint = colors.primary,
        label = copy.tileRetention,
        description = copy.retentionDescription(percent, tiles.retentionRepetitions),
    ) {
        if (percent == null) {
            Text(text = copy.retentionValue(null), style = QhTheme.type.headline.tabular(), color = colors.inkSecondary)
        } else {
            val sign = QhTheme.type.title1
            Row(verticalAlignment = Alignment.Top) {
                if (copy.percentFirst) Text("%", style = sign, color = colors.inkSecondary, modifier = Modifier.padding(top = QhSpacing.x1, end = 2.dp))
                AnimatedCounter(value = shown, style = QhTheme.type.headline, color = colors.ink)
                if (!copy.percentFirst) Text("%", style = sign, color = colors.inkSecondary, modifier = Modifier.padding(top = QhSpacing.x1, start = 2.dp))
            }
        }
    }
}

/** The card of a tile: the round icon, the value, the label (two lines reserved, so rows stay even). */
@Composable
private fun RowScope.TileFrame(
    icon: ImageVector,
    tint: Color,
    label: String,
    description: String,
    action: TileAction? = null,
    value: @Composable () -> Unit,
) {
    val colors = QhTheme.colors
    val onAction = action?.onClick
    QhCard(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = if (action != null) "$description. ${action.label}" else description
                if (action != null) {
                    role = Role.Button
                    onClick(label = action.label) {
                        action.onClick()
                        true
                    }
                }
            },
        onClick = onAction,
        contentPadding = PaddingValues(QhSpacing.card),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconInline))
        }
        Spacer(Modifier.height(QhSpacing.x3))
        value()
        Spacer(Modifier.height(QhSpacing.x1))
        Text(
            text = label,
            style = QhTheme.type.callout,
            color = colors.inkSecondary,
            minLines = 2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (action != null) {
            // "Practice": the tile is a button, and says so.
            Text(
                text = action.label,
                style = QhTheme.type.label,
                color = colors.due,
                maxLines = 1,
                modifier = Modifier.padding(top = QhSpacing.x1),
            )
        }
    }
}

/** What a tile that opens something says ([label]) and does ([onClick]). */
private class TileAction(val label: String, val onClick: () -> Unit)

private const val TileStartMillis = 250L
private const val TileStepMillis = 90L
