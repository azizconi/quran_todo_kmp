import SwiftUI

/// A small, calm celebration: 14 gold and emerald round petals fly out radially and fade over about a second, with a soft ring.
/// Use it for "memorized" and at the end of a session, no more often than it really happens.
///
/// Draws centred in its bounds and may overflow them; it never intercepts touches. Fires every time `trigger` changes after the
/// first appearance. Plays the haptic with the burst; under Reduce Motion only the haptic remains.
struct CelebrationBurst: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let trigger: Int
    var radius: CGFloat = 72
    var haptic: QhHapticKind? = .success

    private static let count = 14
    private static let duration: Double = 1.1
    private static let fadeStart = 0.45

    @State private var start: Date?
    @State private var seed: UInt64 = 1

    var body: some View {
        TimelineView(.animation(paused: start == nil)) { context in
            Canvas { gc, size in
                guard let start else { return }
                let t = context.date.timeIntervalSince(start) / Self.duration
                guard t > 0, t < 1 else { return }
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let flight = Self.decelerate(t)
                let fade = 1 - min(max((t - Self.fadeStart) / (1 - Self.fadeStart), 0), 1)
                let alpha = fade * fade * (3 - 2 * fade)
                var ring = Path()
                let ringRadius = radius * 0.9 * flight
                ring.addEllipse(in: CGRect(x: center.x - ringRadius, y: center.y - ringRadius, width: ringRadius * 2, height: ringRadius * 2))
                gc.stroke(ring, with: .color(qh.colors.gold.opacity(0.5 * alpha)), lineWidth: 2 * (1 - 0.5 * t))
                var rng = SplitMix(seed: seed)
                for i in 0..<Self.count {
                    let angle = Double(i) / Double(Self.count) * 2 * .pi + rng.next() * 0.35
                    let distance = 0.6 + rng.next() * 0.4
                    let size = 3.5 + rng.next() * 3
                    let d = radius * distance * flight
                    let r = size * (1 - 0.4 * t)
                    let c = CGPoint(x: center.x + cos(angle) * d, y: center.y + sin(angle) * d)
                    let color = (i % 2 == 0 ? qh.colors.gold : qh.colors.learned).opacity(alpha)
                    gc.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(color))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onChange(of: trigger) { _, new in
            if let haptic { QhHaptics.shared.perform(haptic) }
            guard !reduced else { return }
            seed = UInt64(truncatingIfNeeded: new) &+ 1
            start = Date()
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.duration + 0.05) { start = nil }
        }
    }

    /// The `EmphasizedDecelerate` curve (0.05, 0.7, 0.1, 1) evaluated by bisection.
    private static func decelerate(_ x: Double) -> Double {
        let (x1, y1, x2, y2) = (0.05, 0.7, 0.1, 1.0)
        func bez(_ t: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
        }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if bez(mid, x1, x2) < x { lo = mid } else { hi = mid }
        }
        return bez((lo + hi) / 2, y1, y2)
    }
}

/// Small deterministic generator so one trigger always draws the same petals.
struct SplitMix {
    var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E3779B97F4A7C15 }
    mutating func next() -> Double {
        state = state &+ 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        z ^= z >> 31
        return Double(z >> 11) / Double(1 << 53)
    }
}

#Preview("Celebration") {
    struct Demo: View {
        @State var n = 0
        var body: some View {
            VStack(spacing: 40) {
                ZStack {
                    Medallion(size: 64, color: .green)
                    CelebrationBurst(trigger: n)
                }.frame(width: 160, height: 160)
                QhButton("Celebrate", size: .m) { n += 1 }
            }
        }
    }
    return Demo().qhPreviewTheme()
}
