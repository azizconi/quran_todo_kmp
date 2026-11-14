import SwiftUI

/// A 52pt search pill for inline use. (Where a screen allows it, prefer the system `.searchable`.) The magnifier warms up to `primary`
/// and a 1.5pt `primary` outline fades in on focus; the clear button scales in once there is text.
struct QhSearchField: View {
    @Environment(\.qh) private var qh
    @Binding var text: String
    let placeholder: String
    var clearLabel: String = "Clear"
    var onSubmit: (String) -> Void = { _ in }
    var onFocusChange: (Bool) -> Void = { _ in }
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(focused ? qh.colors.primary : qh.colors.inkSecondary)
            TextField("", text: $text, prompt: Text(placeholder).foregroundColor(qh.colors.inkTertiary))
                .qhText({ $0.body })
                .focused($focused)
                .submitLabel(.search)
                .onSubmit { onSubmit(text) }
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button {
                    QhHaptics.shared.perform(.tick)
                    text = ""
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(qh.colors.inkSecondary)
                        .frame(width: 24, height: 24)
                        .background(qh.colors.surfaceStrong, in: Circle())
                        .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
                }
                .buttonStyle(.plain)
                .transition(.scale(scale: 0.5).combined(with: .opacity))
                .accessibilityLabel(clearLabel)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, text.isEmpty ? 16 : 4)
        .frame(minHeight: 52)
        .background(qh.colors.surfaceMuted, in: qh.shapes.full)
        .overlay { qh.shapes.full.strokeBorder(focused ? qh.colors.primary : .clear, lineWidth: 1.5) }
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
        .qhAnimation(QhMotion.snappy, value: focused)
        .qhAnimation(QhMotion.snappy, value: text.isEmpty)
        .onChange(of: focused) { _, new in onFocusChange(new) }
    }
}

#Preview("Search light") { SearchPreview().qhPreviewTheme() }
#Preview("Search dark") { SearchPreview().qhPreviewTheme(dark: true) }

private struct SearchPreview: View {
    @State private var empty = ""
    @State private var filled = "Al-Baqarah"
    var body: some View {
        VStack(spacing: 16) {
            QhSearchField(text: $empty, placeholder: "Search surah")
            QhSearchField(text: $filled, placeholder: "Search surah")
        }
        .padding()
    }
}
