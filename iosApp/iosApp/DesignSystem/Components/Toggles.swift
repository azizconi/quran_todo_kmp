import SwiftUI

/// A 32pt rounded tile with an SF Symbol in `tint` on a 14% wash of the same colour: the leading icon of settings rows.
struct QhIconTile: View {
    @Environment(\.qh) private var qh
    let icon: String
    var tint: Color?

    var body: some View {
        let color = tint ?? qh.colors.primary
        Image(systemName: icon)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: 32, height: 32)
            .background(color.opacity(0.14), in: qh.shapes.smShape)
            .accessibilityHidden(true)
    }
}

/// Hairline between rows of one group. `startIndent` aligns it with the text after the icon tile.
struct QhRowDivider: View {
    @Environment(\.qh) private var qh
    var startIndent: CGFloat = 60

    var body: some View {
        Rectangle().fill(qh.colors.hairline).frame(height: 1).padding(.leading, startIndent)
    }
}

/// Shared layout of settings rows.
private struct SettingsRow<Trailing: View>: View {
    @Environment(\.qh) private var qh
    @Environment(\.isEnabled) private var isEnabled
    let title: String
    var description: String?
    var icon: String?
    var iconTint: Color?
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(spacing: QhSpacing.x3) {
            if let icon { QhIconTile(icon: icon, tint: isEnabled ? iconTint : qh.colors.inkTertiary) }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).qhText({ $0.title2 }, color: { isEnabled ? $0.ink : $0.inkTertiary })
                if let description {
                    Text(description).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            }
            Spacer(minLength: QhSpacing.x2)
            HStack(spacing: QhSpacing.x1) { trailing() }
        }
        .padding(.horizontal, QhSpacing.card)
        .padding(.vertical, QhSpacing.x3)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }
}

/// A settings row with a switch on the right. The whole row toggles, with the segment haptic.
/// Put several rows in a `QhCard(padding: 0)` separated by `QhRowDivider`.
struct QhSwitchRow: View {
    @Environment(\.qh) private var qh
    let title: String
    @Binding var isOn: Bool
    var description: String?
    var icon: String?
    var iconTint: Color?

    var body: some View {
        SettingsRow(title: title, description: description, icon: icon, iconTint: iconTint) {
            Toggle("", isOn: $isOn).labelsHidden().tint(qh.colors.primary).allowsHitTesting(false)
        }
        .pressable(haptic: .segment, scale: 0.99) { withQhAnimation(QhMotion.snappy) { isOn.toggle() } }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(description ?? "")
        .accessibilityAddTraits(.isToggle)
        .accessibilityAction(named: Text(title)) { isOn.toggle() }
    }
}

/// A settings row that opens something: a chevron on the right.
struct QhNavRow: View {
    @Environment(\.qh) private var qh
    let title: String
    var description: String?
    var icon: String?
    var iconTint: Color?
    let action: () -> Void

    var body: some View {
        SettingsRow(title: title, description: description, icon: icon, iconTint: iconTint) {
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
        }
        .pressable(scale: 0.99, action: action)
        .accessibilityLabel(title)
    }
}

/// A settings row that shows a `value` on the right; with `action` it is pressable and gets a chevron.
struct QhValueRow: View {
    @Environment(\.qh) private var qh
    let title: String
    let value: String
    var description: String?
    var icon: String?
    var iconTint: Color?
    var action: (() -> Void)?

    var body: some View {
        let row = SettingsRow(title: title, description: description, icon: icon, iconTint: iconTint) {
            Text(value).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
            if action != nil {
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
            }
        }
        if let action {
            row.pressable(scale: 0.99, action: action)
        } else {
            row.accessibilityElement(children: .combine)
        }
    }
}

/// The system slider in our colours that plays the step haptic whenever the value crosses a step.
/// With `step` > 0 every discrete value is a step; a continuous slider ticks every 5% of its range.
struct QhSlider: View {
    @Environment(\.qh) private var qh
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var step: Double?
    var onEditingChanged: (Bool) -> Void = { _ in }
    @State private var lastStep = Int.min

    private func stepOf(_ v: Double) -> Int {
        let span = range.upperBound - range.lowerBound
        guard span > 0 else { return 0 }
        let divisions = step.map { span / $0 } ?? 20
        return Int((((v - range.lowerBound) / span) * divisions).rounded())
    }

    var body: some View {
        Group {
            if let step {
                Slider(value: $value, in: range, step: step, onEditingChanged: onEditingChanged)
            } else {
                Slider(value: $value, in: range, onEditingChanged: onEditingChanged)
            }
        }
        .tint(qh.colors.primary)
        .onChange(of: value) { _, new in
            let s = stepOf(new)
            if lastStep != Int.min && s != lastStep { QhHaptics.shared.perform(.step) }
            lastStep = s
        }
        .onAppear { lastStep = stepOf(value) }
    }
}

#Preview("Toggles light") { TogglesPreview().qhPreviewTheme() }
#Preview("Toggles dark") { TogglesPreview().qhPreviewTheme(dark: true) }

private struct TogglesPreview: View {
    @State private var on = true
    @State private var v = 28.0
    var body: some View {
        VStack(spacing: 16) {
            QhCard(padding: 0) {
                QhSwitchRow(title: "Reminders", isOn: $on, description: "Every day at 08:00", icon: "bell")
                QhRowDivider()
                QhNavRow(title: "Theme", icon: "paintpalette") {}
                QhRowDivider()
                QhValueRow(title: "Language", value: "English", icon: "globe") {}
            }
            QhSlider(value: $v, range: 22...40, step: 1)
        }
        .padding()
    }
}
