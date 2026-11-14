import SwiftUI

/// Placeholder for a screen that a later stage builds. `Route.screen()` in `App/Router.swift` points here until the real view exists.
struct ComingSoon: View {
    @Environment(\.qh) private var qh
    let route: Route
    let stage: String

    var body: some View {
        ComingSoonBody(title: (String(describing: route).split(separator: "(").first.map(String.init) ?? "").capitalized, stage: stage)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .qhNavigationStyle()
    }
}

struct ComingSoonBody: View {
    @Environment(\.qh) private var qh
    let title: String
    let stage: String

    var body: some View {
        VStack(spacing: QhSpacing.x4) {
            MihrabCalligraphy(width: 88)
            Text(title).qhText({ $0.title1 }).multilineTextAlignment(.center)
            Text("Coming in \(stage)").qhText({ $0.callout }, color: { $0.inkSecondary })
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(qh.colors.bg)
        .accessibilityElement(children: .combine)
    }
}
