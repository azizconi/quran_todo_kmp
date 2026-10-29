package tj.app.quran_todo.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.progress.TilesUi
import tj.app.quran_todo.ui.progress.rememberRollingValue
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The four tiles in a 2x2 grid: day streak, reviews due today, weak ayahs and ayahs in work. Each
 * number rolls up from 0 when the tiles appear.
 */
@Composable
internal fun StatTiles(
    tiles: TilesUi,
    copy: ProgressCopy,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        TileRow {
            StatTile(
                icon = Icons.Rounded.LocalFireDepartment,
                tint = colors.gold,
                value = tiles.streakDays,
                label = copy.tileStreak,
                delayMillis = TileStartMillis,
            )
            StatTile(
                icon = Icons.Rounded.Replay,
                tint = colors.primary,
                value = tiles.dueToday,
                label = copy.tileDue,
                delayMillis = TileStartMillis + TileStepMillis,
            )
        }
        TileRow {
            StatTile(
                icon = Icons.Rounded.PriorityHigh,
                tint = colors.due,
                value = tiles.weakAyahs,
                label = copy.tileWeak,
                delayMillis = TileStartMillis + 2 * TileStepMillis,
            )
            StatTile(
                icon = Icons.Rounded.AutoStories,
                tint = colors.learning,
                value = tiles.learningAyahs,
                label = copy.tileLearning,
                delayMillis = TileStartMillis + 3 * TileStepMillis,
            )
        }
    }
}

/** Two tiles of equal width; the labels reserve two lines, so the tiles are equally high at any font size. */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        content = content,
    )
}

@Composable
private fun RowScope.StatTile(
    icon: ImageVector,
    tint: Color,
    value: Int,
    label: String,
    delayMillis: Long,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val shown = rememberRollingValue(target = value, startDelayMillis = delayMillis)
    QhCard(
        modifier = Modifier
            .weight(1f)
            .clearAndSetSemantics { contentDescription = "$label: $value" },
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
        AnimatedCounter(value = shown, style = type.headline, color = colors.ink)
        Spacer(Modifier.height(QhSpacing.x1))
        Text(
            text = label,
            style = type.callout,
            color = colors.inkSecondary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val TileStartMillis = 250L
private const val TileStepMillis = 90L
