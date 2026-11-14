import SwiftUI

extension View {
    /// A bottom sheet in our style: system detents, a visible grabber, `xl` (28pt) corners, `surface` background, the scrim token behind
    /// it. Content is laid out like a screen section (20pt inset); use `QhSheetHeader` for a title row.
    func qhSheet<SheetContent: View>(
        isPresented: Binding<Bool>,
        detents: Set<PresentationDetent> = [.medium, .large],
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            QhSheetBody(content: content())
                .presentationDetents(detents)
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
                .presentationBackground(Color.clear)
        }
    }
}

private struct QhSheetBody<Content: View>: View {
    @Environment(\.qh) private var qh
    let content: Content

    var body: some View {
        content
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(qh.colors.surface.ignoresSafeArea())
            .foregroundStyle(qh.colors.ink)
    }
}

/// Title row of a sheet: a `title1` title, an optional subtitle and an optional close button.
struct QhSheetHeader: View {
    @Environment(\.qh) private var qh
    let title: String
    var subtitle: String?
    var closeLabel: String?
    var onClose: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: QhSpacing.x3) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                if let subtitle {
                    Text(subtitle).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            }
            Spacer(minLength: 0)
            if let onClose {
                QhIconButton(icon: "xmark", label: closeLabel ?? "Close", action: onClose)
            }
        }
        .padding(.bottom, QhSpacing.x3)
    }
}

#Preview("Sheet light") { SheetPreview().qhPreviewTheme() }
#Preview("Sheet dark") { SheetPreview().qhPreviewTheme(dark: true) }

private struct SheetPreview: View {
    @State private var show = true
    var body: some View {
        Color.clear.qhSheet(isPresented: $show, detents: [.medium]) {
            VStack(alignment: .leading) {
                QhSheetHeader(title: "Go to ayah", subtitle: "Al-Baqarah", closeLabel: "Close") { show = false }
                QhButton("Open", size: .m) {}
            }
        }
    }
}
