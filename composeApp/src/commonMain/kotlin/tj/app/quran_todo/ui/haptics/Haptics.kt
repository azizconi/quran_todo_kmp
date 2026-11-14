package tj.app.quran_todo.ui.haptics

import androidx.compose.runtime.staticCompositionLocalOf

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

/**
 * Plays [HapticKind]s. Obtain it from [LocalHaptics].
 *
 * Android: `View.performHapticFeedback` (`rememberHaptics`). iOS (S2): `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` /
 * `UISelectionFeedbackGenerator` (or SwiftUI `sensoryFeedback`): Tick = light impact, Segment and Step = selection, LongPress = medium
 * impact, Success and Reject = the notification generator. Nothing plays when the in-app toggle ([tj.app.quran_todo.ui.state.UiPrefs.hapticsEnabled])
 * is off.
 */
fun interface Haptics {
    fun perform(kind: HapticKind)

    companion object {
        /** Does nothing; the default outside of a theme and when the user switched haptics off. */
        val None = Haptics { }
    }
}

/** Provided by `QhTheme`; [Haptics.None] outside of it. */
val LocalHaptics = staticCompositionLocalOf { Haptics.None }
