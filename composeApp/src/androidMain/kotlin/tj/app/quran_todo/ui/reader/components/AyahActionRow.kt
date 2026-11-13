package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.AyahActionsCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The row that opens under a tapped ayah of the text reader (UX_PLAN U19): listen from here, repeat this ayah, more (the ayah sheet).
 * It grows and fades in under the text; with "less motion" it just appears.
 */
@Composable
fun AyahActionRow(
    visible: Boolean,
    copy: AyahActionsCopy,
    onListen: () -> Unit,
    onRepeat: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(QhMotion.Short)) + expandVertically(tween(QhMotion.Short)),
        exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(QhMotion.Instant)) + shrinkVertically(tween(QhMotion.Short)),
    ) {
        FitRow(
            weights = ChipWeights,
            spacing = QhSpacing.x2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QhSpacing.x3),
        ) { compact ->
            ActionChip(Icons.Rounded.PlayArrow, copy.listen, tonal = true, compact = compact, onClick = onListen)
            ActionChip(Icons.Rounded.Repeat, copy.repeat, tonal = true, compact = compact, onClick = onRepeat)
            ActionChip(Icons.Rounded.MoreHoriz, copy.more, tonal = false, compact = compact, onClick = onMore)
        }
    }
}

/** Listen is the widest chip, More the narrowest, when there is room to spare. */
private val ChipWeights = listOf(1.5f, 1.1f, 0.9f)

/**
 * Lays the chips in one row across the full width. Every chip first gets the width its label needs on one line, so a long word
 * ("Бештар", "Diğer") never breaks in the middle; the room left over is shared by [weights]. Only when the labels do not fit side by
 * side (narrow screen, large font) is the row composed again with `compact = true` (no icons, less padding); if even that does not
 * fit, the chips shrink in proportion to their natural width and the labels wrap.
 */
@Composable
internal fun FitRow(
    weights: List<Float>,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (compact: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val gap = spacing.roundToPx()
        var measurables = subcompose(false) { content(false) }
        val available = (constraints.maxWidth - gap * (measurables.size - 1)).coerceAtLeast(0)
        var natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        if (natural.sum() > available) {
            measurables = subcompose(true) { content(true) }
            natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        }
        val total = natural.sum()
        val widths = if (total <= available) {
            val extra = available - total
            val weightSum = measurables.indices.sumOf { (weights.getOrElse(it) { 1f }).toDouble() }.toFloat()
            natural.mapIndexed { i, w -> w + (extra * weights.getOrElse(i) { 1f } / weightSum).toInt() }
        } else {
            natural.map { (available.toLong() * it / total.coerceAtLeast(1)).toInt() }
        }
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixedWidth(widths[i].coerceAtLeast(0))) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

/** Icon and label in a pill at least 48 dp high (label only when [compact]); the tonal ones are filled, the ghost one is text only. */
@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    tonal: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val container = if (tonal) colors.primaryContainer else Color.Transparent
    val content = if (tonal) colors.onPrimaryContainer else colors.primary
    Box(
        modifier
            .heightIn(min = QhSpacing.minTouch)
            .pressable(onClick = onClick, shape = shape, highlight = !tonal)
            .clip(shape)
            .background(container)
            .padding(horizontal = if (compact) QhSpacing.x2 else QhSpacing.x3, vertical = QhSpacing.x2),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x1, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = QhTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * The one-time question between the surah header and the first ayah (UX_PLAN U22): "Show the translation under the ayahs?" with
 * "Yes" and "No". An inline line, not a dialog; it folds away once [visible] turns false.
 */
@Composable
fun TranslationOfferLine(
    visible: Boolean,
    copy: AyahActionsCopy,
    onAnswer: (yes: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(QhMotion.Short)) + expandVertically(tween(QhMotion.Medium)),
        exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(QhMotion.Short)) + shrinkVertically(tween(QhMotion.Medium)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x1),
        ) {
            Text(
                text = copy.offerQuestion,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier.weight(1f),
            )
            QhButton(
                text = copy.yes,
                onClick = { onAnswer(true) },
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.S,
            )
            QhButton(
                text = copy.no,
                onClick = { onAnswer(false) },
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.S,
            )
        }
    }
}
