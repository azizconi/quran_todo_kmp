package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
        Text(
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
