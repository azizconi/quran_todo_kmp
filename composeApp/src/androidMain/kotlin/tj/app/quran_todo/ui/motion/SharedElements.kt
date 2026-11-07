@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale

/** Provided once around the NavHost by the app shell. Null outside of it (previews, catalog). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Provided by every `composable<Dest.X>` entry with its AnimatedContentScope. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared bounds spring from the motion tokens (dampingRatio 0.86, stiffness 320). The visibility
 * threshold is half a pixel: without it the spring chases a 0.01px target and keeps the overlay
 * (and `isTransitionActive`) alive for a long, invisible tail.
 */
val QhBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.86f, stiffness = 320f, visibilityThreshold = Rect.VisibilityThreshold)
}

enum class SharedResize {
    /**
     * Content is measured once at its own size and uniformly scaled to fit the animated bounds
     * (`ContentScale.Fit`, centered): text and icons. Give the content wrap-content bounds, otherwise
     * a short label is blown up to the width of a wide box.
     */
    Scale,

    /** Content is re-measured at every frame of the bounds animation (containers). */
    Remeasure,
}

/** Fades of the shared content; they decide how the two versions of a shared item blend. */
object SharedFades {
    /** The new container content waits for the box to take shape before it appears. */
    val ContainerEnter: EnterTransition = fadeIn(tween(QhMotion.Medium, delayMillis = 100, easing = QhMotion.EmphasizedDecelerate))

    /** The old container content leaves at once, so it never sits inside the new box. */
    val ContainerExit: ExitTransition = fadeOut(tween(QhMotion.Instant + 20, easing = QhMotion.EmphasizedAccelerate))

    /** Two versions of one label cross-fade while the bounds scale from one to the other. */
    val ContentEnter: EnterTransition = fadeIn(tween(QhMotion.Short + 60))
    val ContentExit: ExitTransition = fadeOut(tween(QhMotion.Short + 60))
}

/** True while a shared transition is running; use it to ignore repeated navigation taps. */
@Composable
fun isSharedTransitionActive(): Boolean = LocalSharedTransitionScope.current?.isTransitionActive == true

/**
 * True when this screen is at rest: its enter transition is over, it is not leaving and no shared flight is
 * running. Use it to fade in decoration that must not fly with a shared element (a tile behind a flying icon)
 * and to fade it out again as soon as the way back starts. Always true outside a NavHost and with reduced
 * motion, where nothing flies.
 */
@Composable
fun rememberSharedSettled(): Boolean {
    val scope = LocalNavAnimatedVisibilityScope.current
    if (scope == null || LocalReducedMotion.current) return true
    val transition = scope.transition
    // After a cancelled predictive back the current state stays PostExit while the target is Visible again, so
    // only "still entering" (PreEnter) and "leaving" (target is not Visible) count as not at rest.
    val atRest = transition.targetState == EnterExitState.Visible && transition.currentState != EnterExitState.PreEnter
    return atRest && !isSharedTransitionActive()
}

/**
 * [SharedTransitionScope.sharedElement] keyed by a [SharedKeys] value.
 * No-op when there is no shared scope or reduced motion is on.
 *
 * The content of the destination is what flies, remeasured to the animated bounds. [zIndexInOverlay]
 * is above containers (0) so an element always travels over the box it leaves or enters.
 */
@Composable
fun Modifier.sharedElementKey(
    key: Any,
    boundsTransform: BoundsTransform = QhBoundsTransform,
    zIndexInOverlay: Float = 1f,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(sharedScope) {
        this@sharedElementKey.sharedElement(
            rememberSharedContentState(key),
            visibilityScope,
            boundsTransform = boundsTransform,
            zIndexInOverlay = zIndexInOverlay,
        )
    }
}

/**
 * [SharedTransitionScope.sharedBounds] keyed by a [SharedKeys] value, for elements whose content
 * differs between the two screens (row -> header, mini player -> dock).
 * No-op when there is no shared scope or reduced motion is on.
 *
 * Defaults follow [resize]: a [SharedResize.Remeasure] container fades its old content out at once and
 * its new content in after a short delay (container transform); a [SharedResize.Scale] label cross-fades
 * between its two versions and travels above the containers.
 */
@Composable
fun Modifier.sharedBoundsKey(
    key: Any,
    resize: SharedResize = SharedResize.Remeasure,
    enter: EnterTransition = if (resize == SharedResize.Remeasure) SharedFades.ContainerEnter else SharedFades.ContentEnter,
    exit: ExitTransition = if (resize == SharedResize.Remeasure) SharedFades.ContainerExit else SharedFades.ContentExit,
    boundsTransform: BoundsTransform = QhBoundsTransform,
    zIndexInOverlay: Float = if (resize == SharedResize.Remeasure) 0f else 2f,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(sharedScope) {
        this@sharedBoundsKey.sharedBounds(
            rememberSharedContentState(key),
            visibilityScope,
            enter = enter,
            exit = exit,
            boundsTransform = boundsTransform,
            resizeMode = when (resize) {
                SharedResize.Scale -> SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.Center)
                SharedResize.Remeasure -> SharedTransitionScope.ResizeMode.RemeasureToBounds
            },
            zIndexInOverlay = zIndexInOverlay,
        )
    }
}
