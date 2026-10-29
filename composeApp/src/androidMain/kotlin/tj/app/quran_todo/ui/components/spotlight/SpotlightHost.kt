package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion

/** Where the host sits; the overlay and the starter measure the targets against it. */
internal class SpotlightHostRef {
    var coordinates: LayoutCoordinates? = null

    /** The host as a rectangle in its own coordinates, or null when it is not placed yet. */
    fun viewport(): Rect? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        return Rect(0f, 0f, c.size.width.toFloat(), c.size.height.toFloat())
    }
}

/**
 * The layer of the tips, in the root of the app, above the navigation and the mini player and in the same window (a
 * target inside a bottom sheet or a dialog, which are other windows, cannot be pointed at). It provides
 * [LocalSpotlight], starts the tours screens ask for when nothing holds them back, and draws the running one.
 *
 * While a tour runs, what is under it is hidden from accessibility services (TalkBack reads the tip only).
 */
@Composable
fun SpotlightHost(
    controller: SpotlightController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // A dialog, a menu or a sheet takes the focus from the window of the app: the same moment no tour may start.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    SideEffect { controller.windowFocused = windowFocused }

    val hostRef = remember { SpotlightHostRef() }
    val run = controller.run
    // The overlay is kept for the moment it fades out, after the tour is over.
    val held = remember { arrayOfNulls<SpotlightRun>(1) }
    if (run != null) held[0] = run

    CompositionLocalProvider(LocalSpotlight provides controller) {
        Box(modifier.onGloballyPositioned { hostRef.coordinates = it }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (run != null) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                content()
            }
            SpotlightStarter(controller, hostRef)
            val reduced = LocalReducedMotion.current
            AnimatedVisibility(
                visible = run != null,
                enter = fadeIn(tween(if (reduced) QhMotion.Instant else QhMotion.Medium)),
                exit = fadeOut(tween(if (reduced) QhMotion.Instant else QhMotion.Short)),
            ) {
                held[0]?.let { SpotlightOverlay(controller, it, hostRef) }
            }
        }
    }
}

/** How often the targets are looked at while a tour waits for its first step. */
private const val StartPollMillis = 100L

/** After this long without a visible first step the tour gives up until the screen asks again. */
private const val StartGiveUpMillis = 3_500L

/**
 * Starts the tour a screen asked for: once nothing holds it back ([SpotlightGate]), it waits for the layout to be still and
 * the targets to be in place (the same bounds twice in a row), keeps the steps that are on screen and hands them to the
 * controller. No step on screen: nothing starts and nothing is marked, the screen can try again the next time it opens.
 */
@Composable
private fun SpotlightStarter(controller: SpotlightController, host: SpotlightHostRef) {
    LaunchedEffect(controller) {
        snapshotFlow { controller.nextCandidate() }
            .distinctUntilChanged()
            .collectLatest { tour ->
                if (tour == null) return@collectLatest
                delay(if (controller.sheetWasShown) SpotlightTour.DefaultSettleMillis else tour.settleMillis)
                var waited = 0L
                var previous: List<Rect?>? = null
                while (true) {
                    val viewport = host.viewport()
                    val coordinates = host.coordinates
                    if (viewport != null && coordinates != null) {
                        val bounds = HashMap<String, SpotlightBounds?>()
                        val steps = SpotlightSteps.select(tour.steps, viewport) { id ->
                            bounds.getOrPut(id) { controller.registry.boundsOf(id, coordinates) }
                        }
                        val signature = steps.map { bounds[it.targetId]?.rect }
                        if (steps.isNotEmpty() && signature == previous) {
                            controller.start(tour, steps)
                            return@collectLatest
                        }
                        previous = signature
                    }
                    if (waited >= StartGiveUpMillis) {
                        controller.markExhausted(tour)
                        return@collectLatest
                    }
                    delay(StartPollMillis)
                    waited += StartPollMillis
                }
            }
    }
}
