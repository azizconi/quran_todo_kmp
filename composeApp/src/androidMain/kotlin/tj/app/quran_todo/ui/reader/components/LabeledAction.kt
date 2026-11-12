package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * An action of a reader top bar (U23): the icon with a one-word [label] under it, so the bar needs no tour. The target is at least 48dp
 * both ways. Screen readers hear [contentDescription] (the long name of the action), never the short label.
 */
@Composable
fun LabeledAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .widthIn(min = QhSpacing.minTouch)
            .heightIn(min = QhSpacing.minTouch)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .pressable(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.ink,
            modifier = Modifier.size(QhSpacing.iconBar),
        )
        if (LocalActionLabels.current) Text(
            text = label,
            style = QhTheme.type.label.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** False inside [AdaptiveActionBar] when the labels did not fit: the actions are bare icons. */
private val LocalActionLabels = staticCompositionLocalOf { true }

/**
 * A top bar row: [leading] (the back button), then [title] in all the room that is left, then the [actions] (usually [LabeledAction]s).
 * When the labels under the icons would leave the title less than [minTitleWidth] (scaled with the font) — a 360dp phone, a large
 * font, long Tajik or Uzbek words — the actions lose their labels and stay as icons; TalkBack hears their descriptions either way.
 */
@Composable
fun AdaptiveActionBar(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    minTitleWidth: Dp = 112.dp,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lead = subcompose(BarSlot.Leading, leading).map { it.measure(loose) }
        val leadWidth = lead.sumOf { it.width }
        var acts = subcompose(BarSlot.Labeled) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }.map { it.measure(loose) }
        val minTitle = (minTitleWidth.toPx() * fontScale).toInt()
        if (constraints.maxWidth - leadWidth - acts.sumOf { it.width } < minTitle) {
            acts = subcompose(BarSlot.Bare) {
                CompositionLocalProvider(LocalActionLabels provides false) {
                    Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }.map { it.measure(loose) }
        }
        val actsWidth = acts.sumOf { it.width }
        val titleWidth = (constraints.maxWidth - leadWidth - actsWidth).coerceAtLeast(0)
        val titles = subcompose(BarSlot.Title, title).map { it.measure(loose.copy(minWidth = titleWidth, maxWidth = titleWidth)) }
        val all = lead + titles + acts
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else all.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            for (placeable in all) {
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width
            }
        }
    }
}

private enum class BarSlot { Leading, Labeled, Bare, Title }
