import SwiftUI

/// A number whose digits roll up (when it grows) or down (when it shrinks) with the system numeric text transition.
/// Plain text under Reduce Motion. Announced to VoiceOver as one number.
struct AnimatedCounter: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let value: Int
    var style: ((QhTypography) -> QhTextStyle) = { $0.title1 }
    var color: Color?

    var body: some View {
        Text("\(value)")
            .qhText(style, color: color.map { c in { _ in c } })
            .qhTabular()
            .contentTransition(reduced ? .identity : .numericText(value: Double(value)))
            .animation(reduced ? nil : QhMotion.gentle, value: value)
            .accessibilityLabel("\(value)")
    }
}

#Preview("Counter") {
    struct Demo: View {
        @State var n = 7
        var body: some View {
            VStack(spacing: 24) {
                AnimatedCounter(value: n)
                QhButton("+1", size: .m) { n += 1 }
            }.padding()
        }
    }
    return Demo().qhPreviewTheme()
}
