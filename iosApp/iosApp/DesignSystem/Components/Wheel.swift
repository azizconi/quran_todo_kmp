import SwiftUI

private let wheelItemHeight: CGFloat = 48
private let wheelVisibleRows = 5

/// A wheel for picking an ayah number in `minimum...maximum`: five 48pt rows, the chosen number large in the middle band, the others
/// shrinking and fading toward the edges. Snaps to a number, plays the step haptic on every change and accepts taps on a row.
/// Change `value` (or the bounds) from outside and the wheel scrolls to it.
struct AyahWheel: View {
    @Environment(\.qh) private var qh
    let label: String
    let minimum: Int
    let maximum: Int
    @Binding var value: Int
    @State private var position: Int?

    private var upper: Int { max(maximum, minimum) }

    var body: some View {
        VStack(spacing: 4) {
            Text(label).qhText({ $0.callout }, color: { $0.inkSecondary })
            ZStack {
                qh.shapes.smShape.fill(qh.colors.surfaceMuted).frame(height: wheelItemHeight)
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 0) {
                        ForEach(minimum...upper, id: \.self) { number in
                            row(number)
                                .id(number)
                        }
                    }
                    .scrollTargetLayout()
                }
                .contentMargins(.vertical, wheelItemHeight * CGFloat(wheelVisibleRows / 2), for: .scrollContent)
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: $position, anchor: .center)
                .mask(
                    LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .black, location: 0.3),
                                           .init(color: .black, location: 0.7), .init(color: .clear, location: 1)],
                                   startPoint: .top, endPoint: .bottom)
                )
            }
            .frame(height: wheelItemHeight * CGFloat(wheelVisibleRows))
        }
        .onAppear { position = clamped(value) }
        .onChange(of: position) { _, new in
            guard let new, new != value else { return }
            QhHaptics.shared.perform(.step)
            value = new
        }
        .onChange(of: value) { _, new in
            if position != clamped(new) { withQhAnimation(QhMotion.gentle) { position = clamped(new) } }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityValue("\(value)")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: value = min(value + 1, upper)
            case .decrement: value = max(value - 1, minimum)
            @unknown default: break
            }
        }
    }

    private func clamped(_ v: Int) -> Int { min(max(v, minimum), upper) }

    private func row(_ number: Int) -> some View {
        Text("\(number)")
            .qhText({ $0.headline }, color: { _ in qh.colors.primary })
            .qhTabular()
            .frame(maxWidth: .infinity)
            .frame(height: wheelItemHeight)
            .contentShape(Rectangle())
            .onTapGesture { withQhAnimation(QhMotion.gentle) { position = number } }
            .scrollTransition(axis: .vertical) { content, phase in
                let d = min(abs(phase.value) * 2.5, 2.5)
                let scale = 1 - 0.2 * min(d, 1) - 0.1 * (min(d, 2) - min(d, 1))
                return content
                    .scaleEffect(scale)
                    .opacity(1 - 0.3 * d)
                    .saturation(1 - min(d, 1))
            }
    }
}

#Preview("Wheel light") { WheelPreview().qhPreviewTheme() }
#Preview("Wheel dark") { WheelPreview().qhPreviewTheme(dark: true) }

private struct WheelPreview: View {
    @State private var v = 12
    var body: some View {
        AyahWheel(label: "From ayah", minimum: 1, maximum: 286, value: $v).frame(width: 140).padding()
    }
}
