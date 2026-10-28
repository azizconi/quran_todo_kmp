package tj.app.quran_todo.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Semantic haptic effects (docs/redesign/ANDROID_REDESIGN.md, section 3.6). */
enum class HapticKind {
    /** Any button or card press. */
    Tick,

    /** Segments, chips, switches. */
    Segment,

    /** Slider steps, the ayah wheel. */
    Step,

    /** Long press. */
    LongPress,

    /** "Memorized", end of a session. */
    Success,

    /** Errors, "Forgot". */
    Reject,
}

/** Plays [HapticKind]s. Obtain it from [LocalHaptics]. */
fun interface Haptics {
    fun perform(kind: HapticKind)

    companion object {
        /** Does nothing; the default outside of a theme and when the user switched haptics off. */
        val None = Haptics { }
    }
}

/** Provided by `QhTheme`; [Haptics.None] outside of it. */
val LocalHaptics = staticCompositionLocalOf { Haptics.None }

/**
 * Haptics backed by [View.performHapticFeedback], which respects the system "touch feedback" setting.
 * Newer constants fall back to older ones on older Android versions. Returns [Haptics.None] when
 * [enabled] is false (the in-app toggle).
 */
@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { if (enabled) ViewHaptics(view) else Haptics.None }
}

private class ViewHaptics(private val view: View) : Haptics {
    override fun perform(kind: HapticKind) {
        view.performHapticFeedback(constantFor(kind))
    }

    private fun constantFor(kind: HapticKind): Int {
        val sdk = Build.VERSION.SDK_INT
        return when (kind) {
            HapticKind.Tick -> HapticFeedbackConstants.CONTEXT_CLICK
            HapticKind.Segment ->
                if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            HapticKind.Step ->
                if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            HapticKind.LongPress -> HapticFeedbackConstants.LONG_PRESS
            HapticKind.Success ->
                if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            HapticKind.Reject ->
                if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
    }
}
