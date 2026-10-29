package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import tj.app.quran_todo.ui.motion.rememberSharedSettled

/**
 * A screen asks for [tour] while [ready] is true: its data is loaded (no skeleton on screen) and the targets of the
 * tour's first step exist. The tour starts when the host finds nothing in the way (a sheet or a dialog, a running
 * transition, the onboarding) and the first target is on screen. When [ready] turns false, or the screen leaves, the
 * request is withdrawn; a tour that is already running is dropped without being marked as seen.
 *
 * The screen has to be at rest too (`rememberSharedSettled`): its enter transition is over and nothing flies.
 */
@Composable
fun SpotlightTourEffect(tour: SpotlightTour, ready: Boolean) {
    val spotlight = LocalSpotlight.current
    val settled = rememberSharedSettled()
    val asking = ready && settled
    DisposableEffect(spotlight, tour, asking) {
        if (asking) spotlight.request(tour)
        onDispose { if (asking) spotlight.withdraw(tour) }
    }
}

/**
 * The app root tells the tips which screen is on top and whether it is at rest: [lifecycle] is the one of the back stack entry,
 * which is RESUMED once the transition into the screen is over (the same test that lets a tap navigate). The state is read from
 * the lifecycle itself, through an observer.
 */
@Composable
fun SpotlightNavigationEffect(route: String?, lifecycle: Lifecycle?) {
    val spotlight = LocalSpotlight.current
    var state by remember(lifecycle) { mutableStateOf(lifecycle?.currentState ?: Lifecycle.State.DESTROYED) }
    DisposableEffect(lifecycle) {
        if (lifecycle == null) return@DisposableEffect onDispose { }
        state = lifecycle.currentState
        val observer = LifecycleEventObserver { _, _ -> state = lifecycle.currentState }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val idle = state == Lifecycle.State.RESUMED
    androidx.compose.runtime.SideEffect { spotlight.onNavigation(route, idle) }
}
