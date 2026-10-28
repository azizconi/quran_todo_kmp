package tj.app.quran_todo.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Provided once around the NavHost by the app shell. Null outside of it (previews, catalog). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Provided by every `composable<Dest.X>` entry with its AnimatedContentScope. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared bounds spring from the motion tokens (dampingRatio 0.86, stiffness 320). */
@OptIn(ExperimentalSharedTransitionApi::class)
val QhBoundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = 320f) }

enum class SharedResize {
    /** Content is measured once and scaled to the animated bounds (text, icons). */
    Scale,

    /** Content is re-measured at every frame of the bounds animation (containers). */
    Remeasure,
}

/** True while a shared transition is running; use it to ignore repeated navigation taps. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun isSharedTransitionActive(): Boolean = LocalSharedTransitionScope.current?.isTransitionActive == true

/**
 * [SharedTransitionScope.sharedElement] keyed by a [SharedKeys] value.
 * No-op when there is no shared scope or reduced motion is on.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementKey(
    key: Any,
    boundsTransform: BoundsTransform = QhBoundsTransform,
    zIndexInOverlay: Float = 0f,
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
 * differs between the two screens (row → header, mini player → dock).
 * No-op when there is no shared scope or reduced motion is on.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsKey(
    key: Any,
    resize: SharedResize = SharedResize.Remeasure,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    boundsTransform: BoundsTransform = QhBoundsTransform,
    zIndexInOverlay: Float = 0f,
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
                SharedResize.Scale -> SharedTransitionScope.ResizeMode.ScaleToBounds()
                SharedResize.Remeasure -> SharedTransitionScope.ResizeMode.RemeasureToBounds
            },
            zIndexInOverlay = zIndexInOverlay,
        )
    }
}
