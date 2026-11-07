package tj.app.quran_todo.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** How long a finger must rest on a stepper button before it starts repeating, and the repeat rate. */
private const val HoldDelayMillis = 420L
private const val RepeatMillis = 80L

/**
 * A "minus, number, plus" stepper. The number rolls ([AnimatedCounter]); holding a button repeats the
 * step, each step plays the step haptic. The owner decides what a step means (+1, or the next preset),
 * and says when a direction is no longer possible.
 */
@Composable
internal fun ValueStepper(
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Rounded.Remove, decreaseLabel, canDecrease, onDecrease)
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            AnimatedCounter(value, style = QhTheme.type.title1, color = QhTheme.colors.ink)
        }
        StepButton(Icons.Rounded.Add, increaseLabel, canIncrease, onIncrease)
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val colors = QhTheme.colors
    val currentHaptics by rememberUpdatedState(LocalHaptics.current)
    val reduced = LocalReducedMotion.current
    val currentStep by rememberUpdatedState(onStep)
    val currentEnabled by rememberUpdatedState(enabled)
    var pressed by remember { mutableStateOf(false) }
    // Set once a hold has started repeating, so the release does not add one more step.
    val repeated = remember { BooleanArray(1) }

    val squeeze by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.86f else 1f,
        animationSpec = if (pressed) QhMotion.press<Float>().orSnap() else QhMotion.bouncy<Float>().orSnap(),
        label = "step-squeeze",
    )
    val tint by animateColorAsState(
        targetValue = if (enabled) colors.ink else colors.inkTertiary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "step-tint",
    )

    fun step() {
        currentHaptics.perform(HapticKind.Step)
        currentStep()
    }

    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        repeated[0] = false
                        if (currentEnabled) {
                            pressed = true
                            coroutineScope {
                                val repeating = launch {
                                    delay(HoldDelayMillis)
                                    while (currentEnabled) {
                                        repeated[0] = true
                                        step()
                                        delay(RepeatMillis)
                                    }
                                }
                                tryAwaitRelease()
                                repeating.cancel()
                            }
                            pressed = false
                        }
                    },
                    onTap = { if (currentEnabled && !repeated[0]) step() },
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
                onClick(label = label) {
                    if (currentEnabled) {
                        currentStep()
                        true
                    } else {
                        false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconInline))
        }
    }
}
