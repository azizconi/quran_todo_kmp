package tj.app.quran_todo.ui.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.Haptics

/**
 * Haptics of iOS: Tick = light impact, Segment and Step = selection, LongPress = medium impact, Success and Reject = the notification
 * generator. Nothing plays when the in-app toggle ([enabled], `UiPrefs.hapticsEnabled`) is off.
 */
@OptIn(ExperimentalForeignApi::class)
class IosHaptics(private val enabled: StateFlow<Boolean>) : Haptics {
    override fun perform(kind: HapticKind) {
        if (!enabled.value) return
        when (kind) {
            HapticKind.Tick -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
            HapticKind.Segment, HapticKind.Step -> UISelectionFeedbackGenerator().selectionChanged()
            HapticKind.LongPress -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
            HapticKind.Success ->
                UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticKind.Reject ->
                UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        }
    }
}
