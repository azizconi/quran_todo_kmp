import SwiftUI

/// The statuses a surah or an ayah can show.
enum QhStatus: Equatable {
    case notStarted
    case learning
    case learned
    /// Time to review, weak ayah.
    case due

    /// SF Symbol: the status is carried by the icon's shape as well as by colour.
    var icon: String {
        switch self {
        case .notStarted: return "circle"
        case .learning: return "circle.lefthalf.filled"
        case .learned: return "checkmark.circle.fill"
        case .due: return "arrow.clockwise"
        }
    }
}

/// Status of a surah or an ayah: an icon and a `label` (pass the localised text). A change of status morphs the pill's width and
/// colours. With `onTap` it is pressable and shows a drop-down chevron.
struct StatusPill: View {
    @Environment(\.qh) private var qh
    let status: QhStatus
    let label: String
    var onTap: (() -> Void)?

    private var colors: (tint: Color, container: Color) {
        let c = qh.colors
        switch status {
        case .notStarted: return (c.inkSecondary, c.surfaceMuted)
        case .learning: return (c.learning, c.learningContainer)
        case .learned: return (c.learned, c.learnedContainer)
        case .due: return (c.due, c.dueContainer)
        }
    }

    var body: some View {
        let look = HStack(spacing: 6) {
            Image(systemName: status.icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(colors.tint)
                .contentTransition(.symbolEffect(.replace))
            // Text stays `ink`: the status colours are too light for 13pt text on their containers.
            Text(label).qhText({ $0.label }).lineLimit(1)
            if onTap != nil {
                Image(systemName: "chevron.down").font(.system(size: 10, weight: .bold)).foregroundStyle(qh.colors.inkSecondary)
            }
        }
        .padding(.leading, 10)
        .padding(.trailing, onTap != nil ? 8 : 12)
        .frame(minHeight: 28)
        .background(colors.container, in: qh.shapes.full)
        .qhAnimation(QhMotion.snappy, value: status)

        if let onTap {
            look.pressable(shape: Capsule(), highlight: false, action: onTap)
                .accessibilityLabel(label)
        } else {
            look.accessibilityElement(children: .combine)
        }
    }
}

/// A selectable chip (filter, choice). When `selected` it is tinted `primaryContainer` and a check slides in. Plays the segment
/// haptic. Visually 36pt high, 48pt to touch.
struct QhChip: View {
    @Environment(\.qh) private var qh
    @Environment(\.isEnabled) private var isEnabled
    let label: String
    let selected: Bool
    var leadingIcon: String?
    let action: () -> Void

    var body: some View {
        let content: Color = !isEnabled ? qh.colors.inkTertiary : (selected ? qh.colors.onPrimaryContainer : qh.colors.inkSecondary)
        HStack(spacing: 6) {
            if selected {
                Image(systemName: "checkmark").font(.system(size: 11, weight: .bold))
                    .transition(.scale(scale: 0.4).combined(with: .opacity))
            } else if let leadingIcon {
                Image(systemName: leadingIcon).font(.system(size: 12, weight: .semibold))
            }
            Text(label).qhText({ $0.label }, color: { _ in content }).lineLimit(1)
        }
        .foregroundStyle(content)
        .padding(.horizontal, 14)
        .frame(minHeight: 36)
        .background(selected ? qh.colors.primaryContainer : qh.colors.surfaceMuted, in: qh.shapes.full)
        .padding(.vertical, 6)
        .pressable(haptic: .segment, shape: Capsule(), highlight: false, action: action)
        .qhAnimation(QhMotion.snappy, value: selected)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

#Preview("Chips light") { ChipsPreview().qhPreviewTheme() }
#Preview("Chips dark") { ChipsPreview().qhPreviewTheme(dark: true) }

private struct ChipsPreview: View {
    @State private var selected = 1
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                StatusPill(status: .notStarted, label: "Not started")
                StatusPill(status: .learning, label: "Learning")
            }
            HStack {
                StatusPill(status: .learned, label: "Memorized", onTap: {})
                StatusPill(status: .due, label: "Review")
            }
            HStack {
                ForEach(0..<3) { i in
                    QhChip(label: ["All", "Learning", "Memorized"][i], selected: selected == i) { selected = i }
                }
            }
        }
        .padding()
    }
}
