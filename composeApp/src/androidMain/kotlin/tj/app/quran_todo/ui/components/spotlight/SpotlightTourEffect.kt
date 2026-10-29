package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
