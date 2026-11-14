import SwiftUI

private struct Shimmer: ViewModifier {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay {
                if !reduced {
                    GeometryReader { geo in
                        LinearGradient(colors: [.clear, qh.colors.surfaceMuted, .clear], startPoint: .leading, endPoint: .trailing)
                            .frame(width: geo.size.width)
                            .offset(x: phase * geo.size.width)
                    }
                    .allowsHitTesting(false)
                }
            }
            .clipped()
            .onAppear {
                guard !reduced else { return }
                withAnimation(.linear(duration: 1.15).repeatForever(autoreverses: false)) { phase = 1.6 }
            }
    }
}

extension View {
    /// Fills the element with a placeholder colour and sweeps a soft highlight across it every 1.15 s. Static under Reduce Motion.
    func qhShimmer() -> some View { modifier(Shimmer()) }
}

/// A shimmering placeholder block. Size it with a frame.
struct Skeleton<S: Shape>: View {
    @Environment(\.qh) private var qh
    let shape: S

    init(shape: S) { self.shape = shape }

    var body: some View {
        shape.fill(qh.colors.surfaceStrong).qhShimmer().clipShape(shape).accessibilityHidden(true)
    }
}

extension Skeleton where S == RoundedRectangle {
    init() { self.init(shape: RoundedRectangle(cornerRadius: 8, style: .continuous)) }
}

/// A placeholder for one list row: a round badge, two text lines and a short trailing block.
struct SkeletonRow: View {
    var body: some View {
        HStack(spacing: QhSpacing.x3) {
            Skeleton(shape: Circle()).frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: QhSpacing.x2) {
                GeometryReader { g in Skeleton().frame(width: g.size.width * 0.55, height: 14) }.frame(height: 14)
                GeometryReader { g in Skeleton().frame(width: g.size.width * 0.35, height: 11) }.frame(height: 11)
            }
            Skeleton().frame(width: 44, height: 18)
        }
        .padding(.vertical, QhSpacing.x3)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading")
    }
}

#Preview("Skeleton light") { SkeletonPreview().qhPreviewTheme() }
#Preview("Skeleton dark") { SkeletonPreview().qhPreviewTheme(dark: true) }

private struct SkeletonPreview: View {
    var body: some View {
        VStack { SkeletonRow(); SkeletonRow(); SkeletonRow() }.padding()
    }
}
