package tj.app.quran_todo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * Screen-to-screen transitions of the NavHost (docs/redesign/ANDROID_REDESIGN.md, 3.4).
 *
 * Forward: the new screen fades in while sliding 6% of the width, the old one scales to 96% and fades.
 * Back is the mirror image. With reduced motion every transition is a 150 ms crossfade.
 * Shared elements are layered on top of these by the screens.
 */
object NavTransitions {
    // TODO(0A-merge): take these from QhMotion once the design system is merged.
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private const val MoveMillis = 380
    private const val FadeInMillis = 220
    private const val FadeInDelayMillis = 60
    private const val FadeOutMillis = 160
    private const val ReducedMillis = 150
    private const val SlideFraction = 0.06f
    private const val ScaleFraction = 0.96f

    private fun slideDistance(width: Int): Int = (width * SlideFraction).toInt()

    fun enter(reducedMotion: Boolean): EnterSpec = if (reducedMotion) {
        { fadeIn(tween(ReducedMillis)) }
    } else {
        {
            fadeIn(tween(FadeInMillis, delayMillis = FadeInDelayMillis)) +
                slideInHorizontally(tween(MoveMillis, easing = EmphasizedDecelerate), ::slideDistance)
        }
    }

    fun exit(reducedMotion: Boolean): ExitSpec = if (reducedMotion) {
        { fadeOut(tween(ReducedMillis)) }
    } else {
        {
            fadeOut(tween(FadeOutMillis)) +
                scaleOut(tween(MoveMillis, easing = Emphasized), targetScale = ScaleFraction)
        }
    }

    fun popEnter(reducedMotion: Boolean): EnterSpec = if (reducedMotion) {
        { fadeIn(tween(ReducedMillis)) }
    } else {
        {
            fadeIn(tween(FadeInMillis, delayMillis = FadeInDelayMillis)) +
                scaleIn(tween(MoveMillis, easing = EmphasizedDecelerate), initialScale = ScaleFraction)
        }
    }

    fun popExit(reducedMotion: Boolean): ExitSpec = if (reducedMotion) {
        { fadeOut(tween(ReducedMillis)) }
    } else {
        {
            fadeOut(tween(FadeOutMillis)) +
                slideOutHorizontally(tween(MoveMillis, easing = Emphasized), ::slideDistance)
        }
    }
}
