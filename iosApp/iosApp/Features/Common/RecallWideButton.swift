import SwiftUI

/// A `QhButton` look that fills the width it is given (the design-system button hugs its content; Android uses `fillMaxWidth`).
struct RecallWideButton: View {
    @Environment(\.qh) private var qh
    let title: String
    var variant: QhButtonVariant = .primary
    var size: QhButtonSize = .l
    var icon: String?
    var loading = false
    let action: () -> Void

    init(_ title: String, variant: QhButtonVariant = .primary, size: QhButtonSize = .l, icon: String? = nil, loading: Bool = false,
         action: @escaping () -> Void) {
        self.title = title; self.variant = variant; self.size = size; self.icon = icon; self.loading = loading; self.action = action
    }

    var body: some View {
        let c = qh.colors
        let content = variant == .tonal ? c.onPrimaryContainer : c.onPrimary
        let container = variant == .tonal ? c.primaryContainer : c.primary
        let style = size == .l ? qh.type.title2 : qh.type.callout.weight("SemiBold")
        ZStack {
            HStack(spacing: QhSpacing.x2) {
                if let icon { Image(systemName: icon).font(.system(size: 15, weight: .semibold)) }
                Text(title).qhText({ _ in style }, color: { _ in content }).lineLimit(1)
            }
            .opacity(loading ? 0 : 1)
            if loading { DotSpinner(color: content) }
        }
        .foregroundStyle(content)
        .frame(maxWidth: .infinity, minHeight: max(size.height, QhSpacing.minTouch))
        .background(container, in: qh.shapes.full)
        .qhSoftShadow(enabled: variant == .primary && size == .l)
        .pressable(shape: Capsule(), action: action)
        .disabled(loading)
        .accessibilityLabel(title)
    }
}
