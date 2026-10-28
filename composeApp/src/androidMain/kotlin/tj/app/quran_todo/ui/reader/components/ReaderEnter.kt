package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.EnterExitState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.LocalNavAnimatedVisibilityScope
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion

/**
 * The entrance of everything that is not a shared element (docs/redesign/ANDROID_REDESIGN.md, 7): after the
 * shared header has started to settle, the rest fades in and slides up 24dp, delayed by 120 ms. One state per
 * reader; every item reads [progress] in a graphics layer, so the animation never recomposes anything.
 * It plays once per back stack entry: coming back to the reader shows the content at once.
 */
private const val EnterDelayMillis = 120
private const val ExitFadeMillis = 110

@Stable
class ReaderEnterState internal constructor(skip: Boolean) {
    internal val progress = Animatable(if (skip) 1f else 0f)

    /** True once the animation is over (or was skipped); items then drop their modifier. */
    var finished by mutableStateOf(skip)
        internal set

    /** Fades the content out when the reader is popped by a button (not by a gesture), see [rememberReaderEnter]. */
    internal val exitAlpha = Animatable(1f)

    /** True from the start of such a pop until the reader is shown again. */
    var leaving by mutableStateOf(false)
        internal set
}

@Composable
fun rememberReaderEnter(): ReaderEnterState {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable { mutableStateOf(false) }
    val state = remember { ReaderEnterState(skip = reduced || played) }
    LaunchedEffect(state) {
        if (!state.finished) {
            // The delay is part of the tween (not a coroutine delay) so it follows the system animation
            // scale together with the shared transition it waits for.
            state.progress.animateTo(
                1f,
                tween(QhMotion.Long, delayMillis = EnterDelayMillis, easing = QhMotion.EmphasizedDecelerate),
            )
            state.finished = true
        }
        played = true
    }

    // On the way back the title flies down through the lines under it: clear them away quickly. A gesture
    // (seeking) keeps the content, so the screen can be dragged away whole and put back.
    val scope = LocalNavAnimatedVisibilityScope.current
    val leaving = scope != null && !reduced &&
        scope.transition.targetState == EnterExitState.PostExit && !scope.transition.isSeeking
    LaunchedEffect(state, leaving) {
        if (leaving) state.leaving = true
        state.exitAlpha.animateTo(if (leaving) 0f else 1f, tween(ExitFadeMillis, easing = QhMotion.EmphasizedAccelerate))
        if (!leaving) state.leaving = false
    }
    return state
}

/** Fade in and slide up by [distance] following [state]; a no-op once the entrance is over. */
@Composable
fun Modifier.readerEnter(state: ReaderEnterState, distance: Dp = 24.dp): Modifier {
    if (state.finished && !state.leaving) return this
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    return graphicsLayer {
        val p = state.progress.value
        alpha = p * state.exitAlpha.value
        translationY = (1f - p) * distancePx
    }
}
