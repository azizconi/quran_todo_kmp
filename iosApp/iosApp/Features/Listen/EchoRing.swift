import SwiftUI

/// The thin ring of the echo silence ("Your turn"): full when the silence starts, empty when it ends. `remaining` and `total` are whole
/// seconds (a state of the audio session). It fills its parent and draws nothing while `active` is false. The sweep is linear over one
/// second per step; under Reduce Motion the ring steps once a second and the seconds in `YourTurnLine` carry the information.
struct EchoRing: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let remaining: Int
    let total: Int
    let active: Bool
    @State private var sweep: Double = 0

    private var visible: Bool { active && total > 0 && remaining > 0 }

    var body: some View {
        ZStack {
            if visible || sweep > 0 {
                Circle().stroke(qh.colors.primary.opacity(0.18), lineWidth: 2)
                Circle()
                    .trim(from: 0, to: sweep)
                    .stroke(qh.colors.primary, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
        }
        .padding(1)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onChange(of: Step(remaining: remaining, total: total, visible: visible), initial: true) { _, step in
            guard step.visible else { sweep = 0; return }
            if step.remaining >= step.total { sweep = 1 }
            let target = Double(max(step.remaining - 1, 0)) / Double(step.total)
            if reduced { sweep = target } else { withAnimation(.linear(duration: 1)) { sweep = target } }
        }
    }

    private struct Step: Equatable {
        let remaining: Int
        let total: Int
        let visible: Bool
    }
}

/// "Your turn · 4 s": the line that says it is the person's turn to repeat. It always takes its place (a fixed height), so the
/// controls under it do not jump when the silence starts and ends.
struct YourTurnLine: View {
    @Environment(\.qh) private var qh
    let yourTurn: String
    let secondsShort: String
    let remaining: Int
    let active: Bool

    var body: some View {
        ZStack {
            if active && remaining > 0 {
                Text("\(yourTurn) · \(remaining) \(secondsShort)")
                    .qhText({ $0.callout }, color: { $0.primary })
                    .lineLimit(1)
                    .transition(.opacity)
            }
        }
        .frame(height: 24)
        .qhAnimation(QhMotion.snappy, value: active && remaining > 0)
        .accessibilityElement(children: .combine)
    }
}
