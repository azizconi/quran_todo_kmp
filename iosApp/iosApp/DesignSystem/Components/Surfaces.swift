import SwiftUI

extension View {
    /// The soft coloured shadow (`primary` @ 18%, blur ~24, offset y 8). Reserved for the hero card, the primary large button
    /// and the floating mini player; everything else is flat.
    func qhSoftShadow(enabled: Bool = true, color: Color? = nil) -> some View {
        modifier(SoftShadow(enabled: enabled, color: color))
    }
}

private struct SoftShadow: ViewModifier {
    @Environment(\.qh) private var qh
    let enabled: Bool
    let color: Color?

    func body(content: Content) -> some View {
        content.shadow(color: enabled ? (color ?? qh.colors.primary.opacity(0.18)) : .clear, radius: 12, x: 0, y: 8)
    }
}

/// Card looks.
enum QhCardStyle {
    /// `surface` with a hairline outline; flat.
    case standard
    /// Gradient `primaryContainer` to `surface`, an ogee lattice, a gold hairline and the soft shadow.
    case hero
}

/// The card surface. With `onTap` it becomes pressable (spring squeeze and haptic); with `onLongPress` too.
struct QhCard<Content: View>: View {
    @Environment(\.qh) private var qh
    var style: QhCardStyle = .standard
    var padding: CGFloat = QhSpacing.card
    var onTap: (() -> Void)?
    var onLongPress: (() -> Void)?
    @ViewBuilder var content: () -> Content

    var body: some View {
        let shape = qh.shapes.lgShape
        let hero = style == .hero
        let card = VStack(alignment: .leading, spacing: 0, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background {
                ZStack {
                    if hero {
                        LinearGradient(colors: [qh.colors.primaryContainer, qh.colors.surface], startPoint: .topLeading, endPoint: .bottomTrailing)
                        ArabesqueLattice()
                    } else {
                        qh.colors.surface
                    }
                }
                .clipShape(shape)
            }
            .overlay { shape.strokeBorder(hero ? qh.colors.gold.opacity(0.3) : qh.colors.hairline, lineWidth: 1) }
            .qhSoftShadow(enabled: hero)

        if let onTap {
            card.pressable(shape: shape, highlight: !hero, onLongPress: onLongPress, action: onTap)
        } else {
            card
        }
    }
}

/// A section heading: an uppercase overline with an optional trailing slot (a count, a link). Announced as a heading.
struct QhSectionHeader<Trailing: View>: View {
    @Environment(\.qh) private var qh
    let title: String
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: String, @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }) {
        self.title = title
        self.trailing = trailing
    }

    var body: some View {
        HStack {
            Text(title.uppercased())
                .qhText({ $0.overline }, color: { $0.inkSecondary })
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            trailing()
        }
    }
}

#Preview("Surfaces light") { SurfacesPreview().qhPreviewTheme() }
#Preview("Surfaces dark") { SurfacesPreview().qhPreviewTheme(dark: true) }

private struct SurfacesPreview: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            QhSectionHeader("Today") { Text("3").qhText({ $0.callout }, color: { $0.inkSecondary }) }
            QhCard(style: .hero) { Text("Hero card").qhText({ $0.title1 }) }
            QhCard(onTap: {}) { Text("Standard card").qhText({ $0.title2 }) }
        }
        .padding()
    }
}
