import SwiftUI

/// A calm empty or error state: an illustration (by default the mihrab arch drawing itself in 900 ms with a calligraphy word inside),
/// a title, an optional message and an optional action button.
struct QhEmptyState<Illustration: View>: View {
    @Environment(\.qh) private var qh
    let title: String
    var message: String?
    var actionLabel: String?
    var onAction: (() -> Void)?
    @ViewBuilder var illustration: () -> Illustration

    var body: some View {
        VStack(spacing: 0) {
            illustration()
            Text(title)
                .qhText({ $0.title1 })
                .multilineTextAlignment(.center)
                .padding(.top, QhSpacing.x5)
                .accessibilityAddTraits(.isHeader)
            if let message {
                Text(message)
                    .qhText({ $0.body }, color: { $0.inkSecondary })
                    .multilineTextAlignment(.center)
                    .padding(.top, QhSpacing.x2)
            }
            if let actionLabel, let onAction {
                QhButton(actionLabel, variant: .tonal, size: .m, action: onAction).padding(.top, QhSpacing.x5)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(QhSpacing.x8)
    }
}

extension QhEmptyState where Illustration == MihrabCalligraphy {
    init(title: String, message: String? = nil, actionLabel: String? = nil, onAction: (() -> Void)? = nil) {
        self.init(title: title, message: message, actionLabel: actionLabel, onAction: onAction) { MihrabCalligraphy(width: 88) }
    }
}

#Preview("Empty light") { EmptyPreview().qhPreviewTheme() }
#Preview("Empty dark") { EmptyPreview().qhPreviewTheme(dark: true) }

private struct EmptyPreview: View {
    var body: some View {
        QhEmptyState(title: "Nothing here yet", message: "Start a lesson and your progress will appear here.", actionLabel: "Start", onAction: {})
    }
}
