import SwiftUI

/// A progress value that grows from 0 on first show (immediately set under Reduce Motion or with `startFull`) and animates later
/// changes with `gentle`.
private struct GrowsOnAppear: ViewModifier {
    @Environment(\.qhReduceMotion) private var reduced
    let target: Double
    let startFull: Bool
    @Binding var shown: Double

    func body(content: Content) -> some View {
        content
            .onAppear {
                if reduced || startFull {
                    shown = target
                } else {
                    withAnimation(QhMotion.gentle) { shown = target }
                }
            }
            .onChange(of: target) { _, new in
                withQhAnimation(QhMotion.gentle, reduce: reduced) { shown = new }
            }
    }
}

/// A circular progress indicator with round caps (the ring of Home and Progress). `progress` (0...1) is animated and grows from 0
/// on first show (unless `startFull`). `content` is centred inside (a number, an icon).
struct QhProgressRing<Content: View>: View {
    @Environment(\.qh) private var qh
    let progress: Double
    var size: CGFloat = 48
    var lineWidth: CGFloat = 4
    var color: Color?
    var trackColor: Color?
    var startFull = false
    @ViewBuilder var content: () -> Content
    @State private var shown: Double

    init(progress: Double, size: CGFloat = 48, lineWidth: CGFloat = 4, color: Color? = nil, trackColor: Color? = nil,
         startFull: Bool = false, @ViewBuilder content: @escaping () -> Content = { EmptyView() }) {
        self.progress = progress
        self.size = size
        self.lineWidth = lineWidth
        self.color = color
        self.trackColor = trackColor
        self.startFull = startFull
        self.content = content
        _shown = State(initialValue: startFull ? progress : 0)
    }

    var body: some View {
        ZStack {
            Circle().stroke(trackColor ?? qh.colors.surfaceMuted, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(max(shown, 0), 1))
                .stroke(color ?? qh.colors.primary, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            content()
        }
        .padding(lineWidth / 2)
        .frame(width: size, height: size)
        .modifier(GrowsOnAppear(target: min(max(progress, 0), 1), startFull: startFull, shown: $shown))
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text("\(Int((progress * 100).rounded())) %"))
    }
}

/// A horizontal progress bar with rounded ends. Use `height: 2` for the reader's reading progress.
struct QhLinearProgress: View {
    @Environment(\.qh) private var qh
    let progress: Double
    var height: CGFloat = 6
    var color: Color?
    var trackColor: Color?
    @State private var shown: Double = 0

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(trackColor ?? qh.colors.surfaceMuted)
                if shown > 0 {
                    Capsule().fill(color ?? qh.colors.primary)
                        .frame(width: max(height, geo.size.width * min(max(shown, 0), 1)))
                }
            }
        }
        .frame(height: height)
        .modifier(GrowsOnAppear(target: min(max(progress, 0), 1), startFull: false, shown: $shown))
        .accessibilityElement(children: .ignore)
        .accessibilityValue(Text("\(Int((progress * 100).rounded())) %"))
    }
}

#Preview("Progress light") { ProgressPreview().qhPreviewTheme() }
#Preview("Progress dark") { ProgressPreview().qhPreviewTheme(dark: true) }

private struct ProgressPreview: View {
    var body: some View {
        VStack(spacing: 20) {
            HStack(spacing: 20) {
                QhProgressRing(progress: 0.64, size: 96, lineWidth: 8) {
                    Text("64%").qhText({ $0.title2 })
                }
                QhProgressRing(progress: 0.3)
            }
            QhLinearProgress(progress: 0.45)
            QhLinearProgress(progress: 0.8, height: 2)
        }
        .padding()
    }
}
