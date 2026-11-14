import SwiftUI

/// Looks of `QhButton`.
enum QhButtonVariant {
    /// Solid `primary`.
    case primary
    /// Solid `primaryContainer`.
    case tonal
    /// Text only.
    case text
    /// Solid `danger`, for destructive actions.
    case danger
}

/// Heights of `QhButton`. The touch target is always at least 48pt.
enum QhButtonSize {
    case l, m, s

    var height: CGFloat {
        switch self {
        case .l: return 56
        case .m: return 48
        case .s: return 36
        }
    }
    var horizontalPadding: CGFloat {
        switch self {
        case .l: return 28
        case .m: return 22
        case .s: return 16
        }
    }
}

/// A pill button. `icon` (an SF Symbol name) goes on the left; `loading` swaps the content for a three-dot spinner
/// (the button keeps its width and ignores presses). `primary` + `.l` carries the soft coloured shadow.
struct QhButton: View {
    @Environment(\.qh) private var qh
    @Environment(\.isEnabled) private var isEnabled

    let title: String
    var variant: QhButtonVariant = .primary
    var size: QhButtonSize = .l
    var icon: String?
    var loading = false
    /// Fills the width it is offered (Android `fillMaxWidth`) instead of hugging the content.
    var fullWidth = false
    let action: () -> Void

    init(_ title: String, variant: QhButtonVariant = .primary, size: QhButtonSize = .l, icon: String? = nil,
         loading: Bool = false, fullWidth: Bool = false, action: @escaping () -> Void) {
        self.fullWidth = fullWidth
        self.title = title
        self.variant = variant
        self.size = size
        self.icon = icon
        self.loading = loading
        self.action = action
    }

    private var container: Color {
        let c = qh.colors
        if !isEnabled { return variant == .text ? .clear : c.surfaceMuted }
        switch variant {
        case .primary: return c.primary
        case .tonal: return c.primaryContainer
        case .text: return .clear
        case .danger: return c.danger
        }
    }

    private var content: Color {
        let c = qh.colors
        if !isEnabled { return c.inkTertiary }
        switch variant {
        case .primary: return c.onPrimary
        case .tonal: return c.onPrimaryContainer
        case .text: return c.primary
        case .danger: return c.onDanger
        }
    }

    private var textStyle: QhTextStyle {
        switch size {
        case .l: return qh.type.title2
        case .m: return qh.type.callout.weight("SemiBold")
        case .s: return qh.type.label
        }
    }

    var body: some View {
        let withShadow = variant == .primary && size == .l && isEnabled
        ZStack {
            HStack(spacing: QhSpacing.x2) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 15, weight: .semibold))
                        .accessibilityHidden(true)
                }
                Text(title)
                    .qhText({ _ in textStyle }, color: { _ in content })
                    .lineLimit(1)
            }
            .foregroundStyle(content)
            .opacity(loading ? 0 : 1)
            if loading { DotSpinner(color: content) }
        }
        .padding(.horizontal, size.horizontalPadding)
        .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: size.height)
        .frame(minHeight: QhSpacing.minTouch)
        .background(container, in: qh.shapes.full)
        .qhSoftShadow(enabled: withShadow)
        .pressable(shape: Capsule(), highlight: variant == .text, action: action)
        .disabled(loading)
        .qhAnimation(QhMotion.snappy, value: isEnabled)
        .accessibilityLabel(title)
    }
}

/// A 48pt touch target with a 40pt round `surfaceMuted` background (`filled` = false: no background). Pass an accessibility label.
struct QhIconButton: View {
    @Environment(\.qh) private var qh
    @Environment(\.isEnabled) private var isEnabled

    let icon: String
    let label: String
    var filled = true
    var tint: Color?
    var iconSize: CGFloat = QhSpacing.iconBar - 4
    let action: () -> Void

    var body: some View {
        ZStack {
            Circle().fill(filled ? qh.colors.surfaceMuted : .clear).frame(width: 40, height: 40)
            Image(systemName: icon)
                .font(.system(size: iconSize, weight: .semibold))
                .foregroundStyle(isEnabled ? (tint ?? qh.colors.ink) : qh.colors.inkTertiary)
        }
        .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
        .pressable(shape: Circle(), highlight: !filled, action: action)
        .accessibilityLabel(label)
    }
}

/// A toolbar action with a caption under the icon (the labelled icons of the Android top bars): easier to understand than a bare glyph.
struct QhIconLabelButton: View {
    @Environment(\.qh) private var qh
    let icon: String
    let label: String
    var tint: Color?
    let action: () -> Void

    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: icon).font(.system(size: 18, weight: .semibold))
            Text(label).qhText({ $0.label.sized(10.5) }, color: { c in tint ?? c.inkSecondary }).lineLimit(1)
        }
        .foregroundStyle(tint ?? qh.colors.ink)
        .frame(minWidth: QhSpacing.minTouch, minHeight: QhSpacing.minTouch)
        .pressable(shape: RoundedRectangle(cornerRadius: 12, style: .continuous), action: action)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
    }
}

/// Three pulsing dots; static under Reduce Motion.
struct DotSpinner: View {
    @Environment(\.qhReduceMotion) private var reduced
    let color: Color
    @State private var on = false

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { i in
                Circle().fill(color).frame(width: 6, height: 6)
                    .opacity(reduced ? 0.7 : (on ? 1 : 0.3))
                    .scaleEffect(reduced ? 0.85 : (on ? 1 : 0.7))
                    .animation(reduced ? nil : .linear(duration: 0.45).repeatForever(autoreverses: true).delay(Double(i) * 0.15), value: on)
            }
        }
        .onAppear { on = true }
        .accessibilityHidden(true)
    }
}

#Preview("Buttons light") { ButtonsPreview().qhPreviewTheme() }
#Preview("Buttons dark") { ButtonsPreview().qhPreviewTheme(dark: true) }

private struct ButtonsPreview: View {
    var body: some View {
        VStack(spacing: 12) {
            QhButton("Continue", icon: "play.fill") {}
            QhButton("Tonal", variant: .tonal, size: .m) {}
            QhButton("Text", variant: .text, size: .s) {}
            QhButton("Delete", variant: .danger, size: .m) {}
            QhButton("Loading", loading: true) {}
            QhButton("Disabled", size: .m) {}.disabled(true)
            HStack {
                QhIconButton(icon: "gearshape", label: "Settings") {}
                QhIconLabelButton(icon: "magnifyingglass", label: "Search") {}
            }
        }
        .padding()
    }
}
