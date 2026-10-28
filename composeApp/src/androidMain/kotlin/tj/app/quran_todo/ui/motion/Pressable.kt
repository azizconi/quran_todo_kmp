package tj.app.quran_todo.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.theme.QhTheme

/** Defaults of [pressable]. */
object PressableDefaults {
    /** Scale while pressed. */
    const val Scale = 0.97f

    /** Opacity of the `surfaceStrong` veil drawn on a pressed element instead of a ripple. */
    const val PressedVeilAlpha = 0.4f
}

/**
 * The one way to make something clickable in the new UI (docs/redesign/ANDROID_REDESIGN.md, 3.3).
 *
 * - squeezes to [scale] with `QhMotion.press()` on touch down and springs back with `snappy()`;
 * - replaces the gray ripple with a `surfaceStrong` veil (when [highlight]), clipped to [shape];
 * - plays [haptic] on click and `HapticKind.LongPress` on long click ([haptic] = null: silent);
 * - exposes [role] and [onClickLabel] to accessibility services.
 *
 * Put it BEFORE `clip`/`background` so the whole visual scales, and give [shape] the same shape the
 * element is clipped with so the veil follows its corners. All animation collapses to nothing when
 * [LocalReducedMotion] is on (the haptic stays).
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    haptic: HapticKind? = HapticKind.Tick,
    scale: Float = PressableDefaults.Scale,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    highlight: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
): Modifier {
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressed = isPressed && enabled
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = when {
            reduced -> snap()
            pressed -> QhMotion.press()
            else -> QhMotion.snappy()
        },
        label = "pressable",
    )
    val veil = QhTheme.colors.surfaceStrong.copy(alpha = PressableDefaults.PressedVeilAlpha)

    val click: () -> Unit = {
        haptic?.let(haptics::perform)
        onClick()
    }
    val interaction = if (onLongClick == null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = click,
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClick = {
                haptics.perform(HapticKind.LongPress)
                onLongClick()
            },
            onClick = click,
        )
    }

    // Hit testing stays on the unscaled bounds (the interaction modifier is outside the graphicsLayer),
    // otherwise a finger near the edge would fall out of the shrinking element and cancel the press.
    return this
        .then(interaction)
        .graphicsLayer {
            val squeeze = 1f - (1f - scale) * pressProgress
            scaleX = squeeze
            scaleY = squeeze
        }
        .drawWithContent {
            drawContent()
            val alpha = pressProgress.coerceIn(0f, 1f)
            if (highlight && alpha > 0f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = veil,
                    alpha = alpha,
                )
            }
        }
}
