import SwiftUI

/// A pill-shaped segmented control: a `surfaceMuted` track with a `surface` indicator that travels between the equally wide
/// segments on a `snappy` spring. `icons` (SF Symbols), when given, must match `options` in size. Plays the segment haptic.
struct QhSegmentedControl: View {
    @Environment(\.qh) private var qh
    let options: [String]
    @Binding var selection: Int
    var icons: [String?]?

    private let inset: CGFloat = 4

    var body: some View {
        GeometryReader { geo in
            let segment = (geo.size.width - inset * 2) / CGFloat(max(options.count, 1))
            ZStack(alignment: .leading) {
                qh.shapes.full
                    .fill(qh.colors.surface)
                    .shadow(color: .black.opacity(qh.isDark ? 0.4 : 0.10), radius: 1.5, y: 1)
                    .frame(width: segment, height: geo.size.height - inset * 2)
                    .offset(x: CGFloat(selection) * segment)
                    .qhAnimation(QhMotion.snappy, value: selection)
                HStack(spacing: 0) {
                    ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                        let selected = index == selection
                        HStack(spacing: 6) {
                            if let icon = icons?[safe: index] ?? nil {
                                Image(systemName: icon).font(.system(size: 14, weight: .semibold))
                            }
                            Text(label).qhText({ $0.label }, color: { selected ? $0.ink : $0.inkSecondary }).lineLimit(1)
                        }
                        .foregroundStyle(selected ? qh.colors.ink : qh.colors.inkSecondary)
                        .padding(.horizontal, 8)
                        .frame(width: segment, height: geo.size.height - inset * 2)
                        .contentShape(Rectangle())
                        .pressable(haptic: selected ? nil : .segment, scale: 0.98, shape: Capsule(), highlight: false) {
                            if !selected { selection = index }
                        }
                        .qhAnimation(QhMotion.snappy, value: selection)
                        .accessibilityLabel(label)
                        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                    }
                }
            }
            .padding(inset)
            .background(qh.colors.surfaceMuted, in: qh.shapes.full)
        }
        .frame(height: QhSpacing.minTouch + inset * 2 - inset * 2)
        .accessibilityElement(children: .contain)
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}

#Preview("Segmented light") { SegmentedPreview().qhPreviewTheme() }
#Preview("Segmented dark") { SegmentedPreview().qhPreviewTheme(dark: true) }

private struct SegmentedPreview: View {
    @State private var a = 0
    @State private var b = 1
    var body: some View {
        VStack(spacing: 20) {
            QhSegmentedControl(options: ["System", "Light", "Dark"], selection: $a)
            QhSegmentedControl(options: ["Day", "Week", "Month"], selection: $b, icons: ["sun.max", "calendar", "calendar.badge.clock"])
        }
        .padding()
    }
}
