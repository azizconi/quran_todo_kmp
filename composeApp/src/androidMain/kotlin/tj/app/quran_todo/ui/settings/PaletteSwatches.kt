package tj.app.quran_todo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhColors
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.qhColors

/**
 * Three palette swatches, each painted with the real `primary` / `primaryContainer` / `bg` tokens of
 * its palette in the current light or dark mode. The selected one pops (`bouncy()`), gets a ring in
 * its own color and a check badge; selecting plays the segment haptic.
 */
@Composable
internal fun PaletteSwatches(
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
    labelOf: (ThemePalette) -> String,
    modifier: Modifier = Modifier,
) {
    val mode = if (QhTheme.colors.isDark) ThemeMode.DARK else ThemeMode.LIGHT
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        PaletteOrder.forEach { palette ->
            Swatch(
                palette = palette,
                mode = mode,
                selected = palette == selected,
                label = labelOf(palette),
                onClick = { onSelect(palette) },
            )
        }
    }
}

private val PaletteOrder = listOf(ThemePalette.FOREST, ThemePalette.SAND, ThemePalette.OCEAN)

@Composable
private fun Swatch(
    palette: ThemePalette,
    mode: ThemeMode,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val preview = remember(palette, mode) { qhColors(mode, palette) }
    val pop by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = QhMotion.bouncy<Float>().orSnap(),
        label = "swatch-pop",
    )
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "swatch-ring",
    )

    Column(
        Modifier
            .width(88.dp)
            .pressable(
                onClick = onClick,
                haptic = HapticKind.Segment,
                shape = QhTheme.shapes.md,
                role = Role.RadioButton,
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            }
            .padding(vertical = QhSpacing.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        SwatchDisc(preview = preview, selected = selected, pop = pop, ring = ring)
        Text(
            text = label,
            style = QhTheme.type.label,
            color = if (selected) colors.ink else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwatchDisc(preview: QhColors, selected: Boolean, pop: Float, ring: Float) {
    val colors = QhTheme.colors
    val badgeIn = QhMotion.bouncy<Float>().orSnap()
    val badgeOut = QhMotion.snappy<Float>().orSnap()
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        // The ring grows out of the swatch when it becomes selected.
        Box(
            Modifier
                .size(68.dp)
                .graphicsLayer {
                    alpha = ring
                    val scale = 0.9f + 0.1f * ring
                    scaleX = scale
                    scaleY = scale
                }
                .border(2.dp, preview.primary, CircleShape),
        )
        Canvas(
            Modifier
                .size(54.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
        ) {
            // Paper, then the diagonal split: primary top-left, primaryContainer bottom-right.
            drawCircle(preview.bg)
            drawArc(
                color = preview.primary,
                startAngle = 135f,
                sweepAngle = 180f,
                useCenter = true,
            )
            drawArc(
                color = preview.primaryContainer,
                startAngle = 315f,
                sweepAngle = 180f,
                useCenter = true,
            )
            drawCircle(preview.hairline, style = Stroke(1.dp.toPx()))
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = scaleIn(badgeIn, initialScale = 0.2f) + fadeIn(badgeOut),
            exit = scaleOut(badgeOut, targetScale = 0.2f) + fadeOut(badgeOut),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(preview.primary)
                    .border(2.dp, colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = preview.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
