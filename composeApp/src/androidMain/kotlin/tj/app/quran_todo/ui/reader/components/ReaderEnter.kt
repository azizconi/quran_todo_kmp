package tj.app.quran_todo.ui.reader.components

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
import kotlinx.coroutines.delay
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion

/**
 * The entrance of everything that is not a shared element (docs/redesign/ANDROID_REDESIGN.md, 7): after the
 * shared header has started to settle, the rest fades in and slides up 24dp, delayed by 120 ms. One state per
 * reader; every item reads [progress] in a graphics layer, so the animation never recomposes anything.
 * It plays once per back stack entry: coming back to the reader shows the content at once.
 */
@Stable
class ReaderEnterState internal constructor(skip: Boolean) {
    internal val progress = Animatable(if (skip) 1f else 0f)

    /** True once the animation is over (or was skipped); items then drop their modifier. */
    var finished by mutableStateOf(skip)
        internal set
}

@Composable
fun rememberReaderEnter(): ReaderEnterState {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable { mutableStateOf(false) }
    val state = remember { ReaderEnterState(skip = reduced || played) }
    LaunchedEffect(state) {
        if (!state.finished) {
            delay(120)
            state.progress.animateTo(1f, tween(QhMotion.Long, easing = QhMotion.EmphasizedDecelerate))
            state.finished = true
        }
        played = true
    }
    return state
}

/** Fade in and slide up by [distance] following [state]; a no-op once the entrance is over. */
@Composable
fun Modifier.readerEnter(state: ReaderEnterState, distance: Dp = 24.dp): Modifier {
    if (state.finished) return this
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    return graphicsLayer {
        val p = state.progress.value
        alpha = p
        translationY = (1f - p) * distancePx
    }
}
