import SwiftUI
import UIKit

/// Motion tokens (port of `QhMotion.kt`, ANDROID_REDESIGN.md section 3.1).
///
/// Android springs are stiffness/damping-ratio pairs; SwiftUI springs are `response` and `dampingFraction`.
/// With unit mass: `response = 2*pi / sqrt(stiffness)`, `dampingFraction = dampingRatio`.
/// The raw values here do not know about Reduce Motion: use `.qhAnimation(_:value:)` or `withQhAnimation`.
enum QhMotion {
    // Durations, seconds.
    static let instant: Double = 0.090
    static let short: Double = 0.160
    static let medium: Double = 0.280
    static let long: Double = 0.420
    static let hero: Double = 0.520

    private static func response(_ stiffness: Double) -> Double { 2 * Double.pi / stiffness.squareRoot() }

    // Springs.
    /// UI state changes (damping 0.82, stiffness 420).
    static let snappy = Animation.spring(response: response(420), dampingFraction: 0.82)
    /// Checkmarks, the badge "pop" (0.55, 380).
    static let bouncy = Animation.spring(response: response(380), dampingFraction: 0.55)
    /// Sizes and content (1.0, 220).
    static let gentle = Animation.spring(response: response(220), dampingFraction: 1.0)
    /// Press squeeze (0.7, 900).
    static let press = Animation.spring(response: response(900), dampingFraction: 0.7)
    /// Shared bounds, matched geometry (0.86, 320).
    static let bounds = Animation.spring(response: response(320), dampingFraction: 0.86)

    // Emphasized curves (Material 3 bezier values, as on Android).
    static func emphasized(_ duration: Double = medium) -> Animation { .timingCurve(0.2, 0, 0, 1, duration: duration) }
    /// For things entering the screen.
    static func enter(_ duration: Double = medium) -> Animation { .timingCurve(0.05, 0.7, 0.1, 1, duration: duration) }
    /// For things leaving the screen.
    static func exit(_ duration: Double = short) -> Animation { .timingCurve(0.3, 0, 0.8, 0.15, duration: duration) }

    /// Control points for UIKit/CoreAnimation use.
    static let emphasizedTiming = CAMediaTimingFunction(controlPoints: 0.2, 0, 0, 1)
    static let enterTiming = CAMediaTimingFunction(controlPoints: 0.05, 0.7, 0.1, 1)

    /// System Reduce Motion or the in-app toggle, for code without access to the environment.
    static var reduced: Bool { UIAccessibility.isReduceMotionEnabled || ThemeSettings.shared.reducedMotion }
}

/// `withAnimation` that snaps (no animation) when Reduce Motion is on.
@discardableResult
func withQhAnimation<Result>(_ animation: Animation = QhMotion.snappy, reduce: Bool? = nil, _ body: () throws -> Result) rethrows -> Result {
    let snap = reduce ?? QhMotion.reduced
    return try withAnimation(snap ? nil : animation, body)
}

extension View {
    /// `.animation(_:value:)` that snaps under Reduce Motion (system or in-app).
    func qhAnimation<V: Equatable>(_ animation: Animation = QhMotion.snappy, value: V) -> some View {
        modifier(QhAnimationModifier(animation: animation, value: value))
    }

    /// A transition that becomes a plain fade under Reduce Motion.
    func qhTransition(_ transition: AnyTransition) -> some View {
        modifier(QhTransitionModifier(transition: transition))
    }
}

private struct QhAnimationModifier<V: Equatable>: ViewModifier {
    @Environment(\.qhReduceMotion) private var reduced
    let animation: Animation
    let value: V

    func body(content: Content) -> some View {
        content.animation(reduced ? nil : animation, value: value)
    }
}

private struct QhTransitionModifier: ViewModifier {
    @Environment(\.qhReduceMotion) private var reduced
    let transition: AnyTransition

    func body(content: Content) -> some View {
        content.transition(reduced ? .opacity : transition)
    }
}
