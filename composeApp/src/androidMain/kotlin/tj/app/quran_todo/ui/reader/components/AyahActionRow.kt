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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = QhSpacing.x3),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            ActionChip(Icons.Rounded.PlayArrow, copy.listen, tonal = true, onClick = onListen, modifier = Modifier.weight(1.5f))
            ActionChip(Icons.Rounded.Repeat, copy.repeat, tonal = true, onClick = onRepeat, modifier = Modifier.weight(1.1f))
            ActionChip(Icons.Rounded.MoreHoriz, copy.more, tonal = false, onClick = onMore, modifier = Modifier.weight(0.9f))
        }
    }
}

/** Icon and label in a pill at least 48 dp high; the tonal ones are filled, the ghost one is text only. */
@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    tonal: Boolean,
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
            .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x1, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
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
