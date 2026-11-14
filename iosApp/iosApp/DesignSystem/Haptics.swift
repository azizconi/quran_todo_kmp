import SwiftUI
import UIKit

/// Semantic haptic effects (port of `Haptics.kt`, ANDROID_REDESIGN.md section 3.6).
enum QhHapticKind: Equatable {
    /// Any button or card press.
    case tick
    /// Segments, chips, switches.
    case segment
    /// Slider steps, the ayah wheel.
    case step
    /// Long press.
    case longPress
    /// "Memorized", end of a session.
    case success
    /// Errors, "Forgot".
    case reject
}

/// Plays `QhHapticKind`s through UIKit feedback generators. The generators are kept and prepared so the first tap has no latency.
/// `enabled` is the in-app toggle; the system "System Haptics" setting is respected by the OS.
final class QhHaptics {
    static let shared = QhHaptics()

    var enabled = true

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let selection = UISelectionFeedbackGenerator()
    private let notification = UINotificationFeedbackGenerator()

    private init() {}

    /// Call when a screen with controls appears.
    func prepare() {
        guard enabled else { return }
        light.prepare()
        selection.prepare()
    }

    func perform(_ kind: QhHapticKind) {
        guard enabled else { return }
        switch kind {
        case .tick:
            light.impactOccurred(intensity: 0.8)
            light.prepare()
        case .segment:
            selection.selectionChanged()
            selection.prepare()
        case .step:
            selection.selectionChanged()
        case .longPress:
            medium.impactOccurred()
        case .success:
            notification.notificationOccurred(.success)
        case .reject:
            notification.notificationOccurred(.error)
        }
    }
}

extension QhHapticKind {
    /// The matching `sensoryFeedback` for declarative use: `.sensoryFeedback(kind.sensory, trigger: value)`.
    var sensory: SensoryFeedback {
        switch self {
        case .tick: return .impact(weight: .light)
        case .segment: return .selection
        case .step: return .selection
        case .longPress: return .impact(weight: .medium)
        case .success: return .success
        case .reject: return .error
        }
    }
}

extension View {
    /// Plays a haptic when `trigger` changes, honouring the in-app toggle.
    func qhHaptic<T: Equatable>(_ kind: QhHapticKind, trigger: T) -> some View {
        onChange(of: trigger) { _, _ in QhHaptics.shared.perform(kind) }
    }
}
