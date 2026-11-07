package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhTheme

/** A floating pill above the dock: flat, hairline outline, pressable. */
@Composable
private fun FloatingPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    Row(
        modifier
            .pressable(onClick = onClick, shape = shape, highlight = false)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        leading()
        Text(
            text = label,
            style = QhTheme.type.label,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "To the current ayah": shown when the list has been scrolled away from the ayah that plays. */
@Composable
fun JumpToCurrentPill(
    pointsUp: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingPill(label = label, onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (pointsUp) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            tint = QhTheme.colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** "Playing: Al-Mulk": another surah owns the audio; tapping the chip opens its reader. */
@Composable
fun PlayingElsewhereChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingPill(label = label, onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = QhTheme.colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

private object KeepAwakeOwners {
    val owners = HashSet<Any>()
}

/**
 * Keeps the screen on while [enabled]. Several readers can be alive at once during a transition, so each
 * one registers itself and the flag is cleared only when none is left.
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    val token = remember { Any() }
    DisposableEffect(view, enabled) {
        if (enabled) KeepAwakeOwners.owners.add(token) else KeepAwakeOwners.owners.remove(token)
        view.keepScreenOn = KeepAwakeOwners.owners.isNotEmpty()
        onDispose {
            KeepAwakeOwners.owners.remove(token)
            view.keepScreenOn = KeepAwakeOwners.owners.isNotEmpty()
        }
    }
}
