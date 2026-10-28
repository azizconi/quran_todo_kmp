package tj.app.quran_todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Looks of [QhButton]. */
enum class QhButtonVariant {
    /** Solid `primary`. */
    Primary,

    /** Solid `primaryContainer`. */
    Tonal,

    /** Text only. */
    Ghost,

    /** Solid `danger`, for destructive actions. */
    Danger,
}

/** Heights of [QhButton]. The touch target is always at least 48dp. */
enum class QhButtonSize(internal val height: Dp, internal val horizontalPadding: Dp) {
    L(56.dp, 28.dp),
    M(48.dp, 22.dp),
    S(36.dp, 16.dp),
}

/**
 * A pill button. [icon] goes on the left; [loading] swaps the content for a three-dot spinner (the
 * button keeps its width and ignores presses). `Primary` + `L` carries the soft colored shadow.
 */
@Composable
fun QhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: QhButtonVariant = QhButtonVariant.Primary,
    size: QhButtonSize = QhButtonSize.L,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val shape = QhTheme.shapes.full
    val targetContainer = when {
        !enabled -> if (variant == QhButtonVariant.Ghost) Color.Transparent else colors.surfaceMuted
        variant == QhButtonVariant.Primary -> colors.primary
        variant == QhButtonVariant.Tonal -> colors.primaryContainer
        variant == QhButtonVariant.Ghost -> Color.Transparent
        else -> colors.danger
    }
    val targetContent = when {
        !enabled -> colors.inkTertiary
        variant == QhButtonVariant.Primary -> colors.onPrimary
        variant == QhButtonVariant.Tonal -> colors.onPrimaryContainer
        variant == QhButtonVariant.Ghost -> colors.primary
        else -> colors.onDanger
    }
    val container by animateColorAsState(targetContainer, QhMotion.snappy<Color>().orSnap(), label = "button-container")
    val content by animateColorAsState(targetContent, QhMotion.snappy<Color>().orSnap(), label = "button-content")
    val textStyle = when (size) {
        QhButtonSize.L -> type.title2
        QhButtonSize.M -> type.callout.copy(fontWeight = FontWeight.SemiBold)
        QhButtonSize.S -> type.label
    }
    val withShadow = variant == QhButtonVariant.Primary && size == QhButtonSize.L && enabled

    Box(
        modifier
            .minimumInteractiveComponentSize()
            .then(if (withShadow) Modifier.softShadow(shape) else Modifier)
            .heightIn(min = size.height)
            .pressable(
                onClick = onClick,
                enabled = enabled && !loading,
                shape = shape,
                highlight = variant == QhButtonVariant.Ghost,
            )
            .clip(shape)
            .background(container)
            .padding(horizontal = size.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.alpha(if (loading) 0f else 1f),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(QhSpacing.iconInline))
            }
            Text(text, style = textStyle, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) DotSpinner(content)
    }
}

/**
 * A 48dp touch target with a 40dp round `surfaceMuted` background ([filled] = false: no background).
 * Pass a [contentDescription] in the app language; use null only for purely decorative buttons.
 */
@Composable
fun QhIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    tint: Color = QhTheme.colors.ink,
    iconSize: Dp = QhSpacing.iconBar,
) {
    val colors = QhTheme.colors
    Box(
        modifier
            .size(QhSpacing.minTouch)
            .pressable(
                onClick = onClick,
                enabled = enabled,
                shape = CircleShape,
                highlight = !filled,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (filled) colors.surfaceMuted else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else colors.inkTertiary,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Three pulsing dots; static under reduced motion. */
@Composable
internal fun DotSpinner(color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "dot-spinner")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val pulse = transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        val value = if (reduced) 0.7f else pulse.value
                        alpha = value
                        scaleX = 0.7f + 0.3f * value
                        scaleY = 0.7f + 0.3f * value
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
