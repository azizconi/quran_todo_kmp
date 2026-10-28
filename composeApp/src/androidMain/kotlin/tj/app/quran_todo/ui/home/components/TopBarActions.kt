@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.EightPointStar
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/**
 * The mini progress ring in the top bar: memorized ayahs of the Quran. Inside it is the percentage
 * once there is any progress, and a small filled gold star before that (a bare star outline read as
 * a brightness icon). Opens the progress screen; the ring flies into the big ring there
 * (`SharedKeys.ProgressRing`). [description] is the spoken label ("Progress: 3%").
 */
@Composable
internal fun ProgressAction(
    progress: Float,
    description: String,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val percent = progressPercent(progress)
    // Part of the ornament, like the badge numerals: it does not grow with the system font.
    val percentSize = with(LocalDensity.current) { 10.dp.toSp() }
    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .pressable(onClick = onClick, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ProgressRing(
            progress = progress,
            size = 36.dp,
            strokeWidth = 3.5.dp,
            modifier = Modifier
                // Scale, not remeasure: the stroke and the percentage are fixed in dp, so the ring has to grow
                // as a picture (cross-fading with the big ring) instead of being squeezed into smaller bounds.
                .sharedBoundsKey(SharedKeys.ProgressRing, SharedResize.Scale, zIndexInOverlay = 1f)
                .semantics { contentDescription = description },
        ) {
            if (percent > 0) {
                Text(
                    text = "$percent%",
                    style = QhTheme.type.label.tabular(),
                    fontSize = percentSize,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    maxLines = 1,
                )
            } else {
                EightPointStar(
                    size = 15.dp,
                    color = colors.gold,
                    strokeWidth = 1.25.dp,
                    fill = colors.gold.copy(alpha = 0.3f),
                )
            }
        }
    }
}

/** Whole percent for the ring: 0 only when nothing is memorized, otherwise at least 1. */
internal fun progressPercent(progress: Float): Int =
    if (progress <= 0f) 0 else (progress * 100).roundToInt().coerceIn(1, 100)

/**
 * The gear: turns 90 degrees under the finger before the settings open (its eight teeth make the
 * quarter turn look identical to the resting gear, so it lands seamlessly in the settings header).
 * The icon alone is the shared element (`SharedKeys.SettingsIcon`).
 */
@Composable
internal fun SettingsAction(
    label: String,
    onClick: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var turned by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (turned) 90f else 0f,
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "gear-rotation",
    )
    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .pressable(
                onClick = {
                    if (reduced) {
                        onClick()
                    } else if (!turned) {
                        turned = true
                        scope.launch {
                            delay(GearTurnMillis)
                            onClick()
                            // If the navigation was ignored, let the gear rest again.
                            delay(GearRestMillis)
                            turned = false
                        }
                    }
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Settings,
            contentDescription = label,
            tint = QhTheme.colors.ink,
            modifier = Modifier
                .size(QhSpacing.iconBar + 2.dp)
                .sharedElementKey(SharedKeys.SettingsIcon)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

private const val GearTurnMillis = 110L
private const val GearRestMillis = 700L
