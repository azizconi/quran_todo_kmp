package tj.app.quran_todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * A pill-shaped segmented control: a `surfaceMuted` track with a `surface` indicator that travels
 * between the equally wide segments on a `snappy()` spring. [icons], when given, must match
 * [options] in size (null entries are allowed). Plays the segment haptic.
 */
@Composable
fun QhSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
) {
    require(options.isNotEmpty()) { "QhSegmentedControl needs at least one option" }
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val inset = 4.dp
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "segment-position",
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(QhSpacing.minTouch)
            .clip(shape)
            .background(colors.surfaceMuted)
            .padding(inset),
    ) {
        val segmentWidth = maxWidth / options.size
        val segmentWidthPx = with(LocalDensity.current) { segmentWidth.toPx() }
        Box(
            Modifier
                .offset { IntOffset((position * segmentWidthPx).roundToInt(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(1.dp, shape)
                .clip(shape)
                .background(colors.surface),
        )
        Row(Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val content by animateColorAsState(
                    targetValue = if (selected) colors.ink else colors.inkSecondary,
                    animationSpec = QhMotion.snappy<Color>().orSnap(),
                    label = "segment-content",
                )
                Row(
                    Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .pressable(
                            onClick = { if (!selected) onSelect(index) },
                            haptic = if (selected) null else HapticKind.Segment,
                            scale = 0.98f,
                            shape = shape,
                            highlight = false,
                            role = Role.RadioButton,
                        )
                        .semantics { this.selected = selected }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons?.getOrNull(index)?.let { icon ->
                        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = label,
                        style = QhTheme.type.label,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
