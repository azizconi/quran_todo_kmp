package tj.app.quran_todo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Animation constants of the staggered entrance (docs/redesign/ANDROID_REDESIGN.md, 3.3). */
object StaggerDefaults {
    /** Only the first items animate; the rest appear at once. */
    const val MaxItems = 8
    const val DelayPerItemMillis = 30
    const val DurationMillis = 300
    val Offset = 16.dp
}

/**
 * Remembers, per screen, whether the entrance stagger has already played. Create one per screen with
 * [rememberStaggerState] and pass it to every [staggeredEnter].
 */
@Stable
class StaggerState internal constructor(
    /** False when the stagger already played for this screen: items then appear immediately. */
    internal val play: Boolean,
) {
    private val shown = mutableSetOf<Int>()

    /** True only the first time [index] is asked for, so items scrolled back into view do not replay. */
    internal fun consume(index: Int): Boolean = shown.add(index)
}

/**
 * One [StaggerState] per screen. The "already played" flag is saveable, so coming back to the screen
 * (back navigation, process restore) does not replay the entrance.
 *
 * @param ready flip to true when the screen's real content is on screen (e.g. after loading). The
 * flag is only set from that moment, so a skeleton phase does not use up the entrance.
 */
@Composable
fun rememberStaggerState(ready: Boolean = true): StaggerState {
    var alreadyPlayed by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val state = remember { StaggerState(play = !alreadyPlayed && !reduced) }
    LaunchedEffect(ready) {
        if (ready) alreadyPlayed = true
    }
    return state
}

/**
 * Fades an item in while sliding it up 16dp, delayed by `index x 30ms`. Only the first 8 items animate.
 * Does nothing when the stagger was already played for this screen, when [enabled] is false, or when
 * reduced motion is on.
 */
@Composable
fun Modifier.staggeredEnter(
    index: Int,
    state: StaggerState,
    enabled: Boolean = true,
): Modifier {
    val animate = remember(index) {
        enabled && state.play && index in 0 until StaggerDefaults.MaxItems && state.consume(index)
    }
    if (!animate) return this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * StaggerDefaults.DelayPerItemMillis).toLong())
        progress.animateTo(1f, tween(StaggerDefaults.DurationMillis, easing = QhMotion.EmphasizedDecelerate))
    }
    val offsetPx = with(LocalDensity.current) { StaggerDefaults.Offset.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}
