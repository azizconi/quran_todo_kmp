package tj.app.quran_todo.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * Motion tokens (docs/redesign/ANDROID_REDESIGN.md, section 3.1).
 *
 * The raw specs here do not know about Reduce Motion. Wrap them with [orSnap] at the call site, or
 * use the helpers in this package (`pressable`, `staggeredEnter`, `AnimatedCounter`, ...) which do it
 * for you.
 */
object QhMotion {
    // Durations, ms.
    const val Instant = 90
    const val Short = 160
    const val Medium = 280
    const val Long = 420
    const val Hero = 520

    // Easing curves.
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Springs.
    /** UI state changes. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.82f, stiffness = 420f)

    /** Checkmarks, the badge "pop". */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = 380f)

    /** Sizes and content. */
    fun <T> gentle() = spring<T>(dampingRatio = 1f, stiffness = 220f)

    /** Press squeeze. */
    fun <T> press() = spring<T>(dampingRatio = 0.7f, stiffness = 900f)

    /** Shared bounds. */
    fun <T> bounds() = spring<T>(dampingRatio = 0.86f, stiffness = 320f)

    /** A tween with the [Emphasized] curve. */
    fun <T> emphasized(durationMillis: Int = Medium): TweenSpec<T> = tween(durationMillis, easing = Emphasized)

    /** A tween with the [EmphasizedDecelerate] curve, for things entering the screen. */
    fun <T> enter(durationMillis: Int = Medium): TweenSpec<T> =
        tween(durationMillis, easing = EmphasizedDecelerate)

    /** A tween with the [EmphasizedAccelerate] curve, for things leaving the screen. */
    fun <T> exit(durationMillis: Int = Short): TweenSpec<T> =
        tween(durationMillis, easing = EmphasizedAccelerate)
}

/**
 * Returns this spec, or an instant `snap()` when [LocalReducedMotion] is on.
 *
 * ```
 * animateFloatAsState(target, QhMotion.snappy<Float>().orSnap())
 * ```
 */
@Composable
fun <T> FiniteAnimationSpec<T>.orSnap(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else this
