import SwiftUI

// MARK: - Mihrab

/// The mihrab arch inset by `inset` from the outline, in a rect; it ends at the base line.
struct MihrabShape: Shape {
    var inset: CGFloat = 0

    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        let left = 0.12 * w + inset
        let right = 0.88 * w - inset
        let top = 0.04 * h + 1.2 * inset
        let shoulder = 0.45 * h
        let bottom = h - inset
        var p = Path()
        p.move(to: CGPoint(x: rect.minX + left, y: rect.minY + bottom))
        p.addLine(to: CGPoint(x: rect.minX + left, y: rect.minY + shoulder))
        p.addCurve(to: CGPoint(x: rect.minX + 0.5 * w, y: rect.minY + top),
                   control1: CGPoint(x: rect.minX + left, y: rect.minY + 0.22 * h),
                   control2: CGPoint(x: rect.minX + 0.30 * w, y: rect.minY + 0.12 * h + 0.6 * inset))
        p.addCurve(to: CGPoint(x: rect.minX + right, y: rect.minY + shoulder),
                   control1: CGPoint(x: rect.minX + 0.70 * w, y: rect.minY + 0.12 * h + 0.6 * inset),
                   control2: CGPoint(x: rect.minX + right, y: rect.minY + 0.22 * h))
        p.addLine(to: CGPoint(x: rect.minX + right, y: rect.minY + bottom))
        return p
    }
}

private struct BaseLine: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX + 0.04 * rect.width, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX + 0.96 * rect.width, y: rect.maxY))
        return p
    }
}

/// A mihrab arch (as on the app icon) that draws itself in gold, an inner arch following it, with `word` written in calligraphy
/// inside: the default illustration of empty states. The box is `width` wide and 7/6 of that tall. Complete under Reduce Motion.
struct MihrabCalligraphy: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    var width: CGFloat = 88
    var word: String = Calligraphy.iqra
    @State private var outer: CGFloat = 0
    @State private var inner: CGFloat = 0

    var body: some View {
        let height = width * 7 / 6
        let wordSize = width * 0.36
        ZStack {
            MihrabShape(inset: 0)
                .trim(from: 0, to: outer)
                .stroke(qh.colors.gold, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            MihrabShape(inset: 6)
                .trim(from: 0, to: inner)
                .stroke(qh.colors.gold.opacity(0.5), style: StrokeStyle(lineWidth: 1, lineCap: .round, lineJoin: .round))
            BaseLine()
                .trim(from: 0, to: outer)
                .stroke(qh.colors.gold, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
            // The baseline of the word sits at 0.74 of the height: the centre of its line box is 0.59 em above it.
            CalligraphyText(word, size: wordSize, revealDuration: 0.7, revealDelay: 0.45, shimmer: false)
                .frame(width: width * 0.8, height: wordSize * 1.7)
                .position(x: width / 2, y: height * 0.74 - wordSize * 0.59)
        }
        .frame(width: width, height: height)
        .accessibilityHidden(true)
        .task(id: reduced) {
            if reduced { outer = 1; inner = 1; return }
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.9)) { outer = 1 }
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.9).delay(0.15)) { inner = 1 }
        }
    }
}

// MARK: - Checkmark

private struct CheckmarkShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: rect.minX + w * 0.22, y: rect.minY + h * 0.54))
        p.addLine(to: CGPoint(x: rect.minX + w * 0.43, y: rect.minY + h * 0.74))
        p.addLine(to: CGPoint(x: rect.minX + w * 0.80, y: rect.minY + h * 0.30))
        return p
    }
}

/// A checkmark that draws itself.
struct AnimatedCheckmark: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    var size: CGFloat = 48
    var color: Color?
    var lineWidth: CGFloat = 3
    var duration: Double = 0.42
    @State private var progress: CGFloat = 0

    var body: some View {
        CheckmarkShape()
            .trim(from: 0, to: progress)
            .stroke(color ?? qh.colors.learned, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
            .task(id: reduced) {
                if reduced { progress = 1 } else { withAnimation(QhMotion.emphasized(duration)) { progress = 1 } }
            }
    }
}

// MARK: - Medallion

/// The outline of a medallion with `lobes` petal-shaped lobes: points on a circle of 0.90 radius, neighbours joined by a quadratic
/// curve bulging to 1.06 radius at the middle of their angle, so the outer edge of the curve reaches about 0.97 radius.
/// (`radius` of the path = half of the smaller side of the rect divided by 0.97, so the outline fits the rect.)
struct MedallionShape: Shape {
    var lobes = 12
    /// The edge of the curve relative to the nominal radius.
    static let extent: CGFloat = 0.97

    func path(in rect: CGRect) -> Path {
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2 / Self.extent
        let step = 2 * Double.pi / Double(lobes)
        func point(_ k: Int, _ r: CGFloat, offset: Double = 0) -> CGPoint {
            let angle = -Double.pi / 2 + Double(k) * step + offset
            return CGPoint(x: center.x + r * CGFloat(cos(angle)), y: center.y + r * CGFloat(sin(angle)))
        }
        var p = Path()
        p.move(to: point(0, radius * 0.90))
        for k in 0..<lobes {
            p.addQuadCurve(to: point(k + 1, radius * 0.90), control: point(k, radius * 1.06, offset: step / 2))
        }
        p.closeSubpath()
        return p
    }
}

/// A 12-lobed medallion in a `size` box, outlined with `lineWidth`. `fill` paints the silhouette behind the line.
struct Medallion: View {
    @Environment(\.qh) private var qh
    var size: CGFloat = 40
    var color: Color?
    var lineWidth: CGFloat = 1.5
    var fill: Color = .clear

    var body: some View {
        MedallionShape()
            .fill(fill)
            .overlay { MedallionShape().stroke(color ?? qh.colors.gold, style: StrokeStyle(lineWidth: lineWidth, lineJoin: .round)) }
            .padding(lineWidth)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

// MARK: - Surah number badge

/// A medallion with the surah number inside; its look carries the status without relying on colour alone: no status is a hairline
/// outline, `.learning` is a gold outline with a ring arc of `learnedFraction` (memorized / all ayahs) around a slightly smaller
/// medallion, `.learned` is a solid emerald medallion. The medallion "pops" (`bouncy`) when it becomes learned.
struct SurahNumberBadge: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let number: Int
    let status: QhStatus
    var learnedFraction: Double = 0
    var size: CGFloat = 40
    @State private var pop: CGFloat = 1

    var body: some View {
        let learned = status == .learned
        let learning = status == .learning
        let c = qh.colors
        let medallionScale: CGFloat = learning ? 0.38 / 0.44 : 1
        let outline = learned ? c.learned : (learning ? c.learning : c.hairline)
        ZStack {
            Circle().stroke(c.learning.opacity(0.22), lineWidth: size * 0.05)
                .padding(size * 0.025)
                .opacity(learning ? 1 : 0)
            Circle().trim(from: 0, to: learning ? min(max(learnedFraction, 0), 1) : 0)
                .stroke(c.learning, style: StrokeStyle(lineWidth: size * 0.05, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(size * 0.025)
                .opacity(learning ? 1 : 0)
            MedallionShape()
                .fill(learned ? c.learned : .clear)
                .overlay { MedallionShape().stroke(outline, style: StrokeStyle(lineWidth: size * 0.034, lineJoin: .round)) }
                .frame(width: size * 0.88, height: size * 0.88)
                .scaleEffect(medallionScale)
            Text("\(number)")
                .font(.custom("Manrope-Bold", fixedSize: size * (number >= 100 ? 0.27 : 0.32)))
                .monospacedDigit()
                .foregroundStyle(learned ? c.onLearned : c.ink)
        }
        .frame(width: size, height: size)
        .scaleEffect(pop)
        .animation(reduced ? nil : QhMotion.snappy, value: status)
        .animation(reduced ? nil : QhMotion.gentle, value: learnedFraction)
        .onChange(of: learned) { old, new in
            guard new, !old, !reduced else { return }
            pop = 0.78
            withAnimation(QhMotion.bouncy) { pop = 1 }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(number)")
    }
}

// MARK: - Divider, cartouche, rosette

/// Hairline, a small rhombus between two dots, hairline, in gold at 60% fading out toward the edges.
struct OrnamentDivider: View {
    @Environment(\.qh) private var qh
    var color: Color?

    var body: some View {
        let line = (color ?? qh.colors.gold).opacity(0.6)
        HStack(spacing: 10) {
            LinearGradient(colors: [.clear, line], startPoint: .leading, endPoint: .trailing).frame(height: 1)
            Canvas { gc, size in
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                for dx in [-11.0, 11.0] {
                    gc.fill(Path(ellipseIn: CGRect(x: c.x + dx - 1.4, y: c.y - 1.4, width: 2.8, height: 2.8)), with: .color(line))
                }
                var rhombus = Path()
                rhombus.move(to: CGPoint(x: c.x, y: c.y - 4.5))
                rhombus.addLine(to: CGPoint(x: c.x + 4.5, y: c.y))
                rhombus.addLine(to: CGPoint(x: c.x, y: c.y + 4.5))
                rhombus.addLine(to: CGPoint(x: c.x - 4.5, y: c.y))
                rhombus.closeSubpath()
                gc.fill(rhombus, with: .color(line))
            }
            .frame(width: 36, height: 10)
            LinearGradient(colors: [line, .clear], startPoint: .leading, endPoint: .trailing).frame(height: 1)
        }
        .accessibilityHidden(true)
    }
}

/// The frame of the reader header: a rounded rectangle with a double gold line and a round "ear" on each side (a ring with a dot in
/// its middle). `background` is the colour behind the cartouche (the reader paper); it masks the frame line behind the ears.
/// `content` is centred (the Arabic surah name).
struct SurahCartouche<Content: View>: View {
    @Environment(\.qh) private var qh
    var background: Color?
    @ViewBuilder var content: () -> Content

    var body: some View {
        let gold = qh.colors.gold
        let paper = background ?? qh.colors.readerBg
        let ear: CGFloat = 10
        content()
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background {
                Canvas { gc, size in
                    let outer: CGFloat = 1.5, inner: CGFloat = 1, corner: CGFloat = 22, gap: CGFloat = 5
                    let r1 = CGRect(x: outer / 2, y: outer / 2, width: size.width - outer, height: size.height - outer)
                    gc.stroke(Path(roundedRect: r1, cornerRadius: corner, style: .continuous), with: .color(gold), lineWidth: outer)
                    let r2 = CGRect(x: gap, y: gap, width: size.width - 2 * gap, height: size.height - 2 * gap)
                    gc.stroke(Path(roundedRect: r2, cornerRadius: max(corner - gap + outer, 0), style: .continuous),
                              with: .color(gold.opacity(0.55)), lineWidth: inner)
                    for x in [CGFloat(0), size.width] {
                        let c = CGPoint(x: x, y: size.height / 2)
                        func circle(_ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }
                        gc.fill(circle(ear), with: .color(paper))
                        gc.stroke(circle(ear), with: .color(gold), lineWidth: outer)
                        gc.stroke(circle(5.2), with: .color(gold.opacity(0.55)), lineWidth: inner)
                        gc.fill(circle(1.8), with: .color(gold))
                    }
                }
            }
            .padding(.horizontal, ear)
    }
}

/// An ayah-number rosette: a circle with eight round petals and the number inside, ringed by the ayah's status (gold: memorizing,
/// emerald: memorized). A terracotta dot marks a `weak` ayah.
struct AyahRosette: View {
    @Environment(\.qh) private var qh
    let number: Int
    var status: QhStatus?
    var weak = false
    var size: CGFloat = 34

    var body: some View {
        let c = qh.colors
        let ring: Color = {
            switch status {
            case .learning: return c.learning
            case .learned: return c.learned
            default: return .clear
            }
        }()
        ZStack {
            Canvas { gc, sz in
                let radius = min(sz.width, sz.height) / 2
                let center = CGPoint(x: sz.width / 2, y: sz.height / 2)
                let petalFill = c.goldContainer
                let petalLine = c.gold.opacity(0.7)
                func circle(_ at: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: at.x - r, y: at.y - r, width: r * 2, height: r * 2)) }
                for i in 0..<8 {
                    let angle = -Double.pi / 2 + Double(i) * Double.pi / 4
                    let p = CGPoint(x: center.x + radius * 0.62 * CGFloat(cos(angle)), y: center.y + radius * 0.62 * CGFloat(sin(angle)))
                    gc.fill(circle(p, radius * 0.2), with: .color(petalFill))
                    gc.stroke(circle(p, radius * 0.2), with: .color(petalLine), lineWidth: 1)
                }
                gc.fill(circle(center, radius * 0.62), with: .color(petalFill))
                gc.stroke(circle(center, radius * 0.62), with: .color(petalLine), lineWidth: 1)
                if weak {
                    gc.fill(circle(CGPoint(x: center.x + radius * 0.74, y: center.y - radius * 0.74), radius * 0.2), with: .color(c.due))
                }
            }
            Circle().strokeBorder(ring, lineWidth: 2)
            Text("\(number)")
                .font(.custom("Manrope-Bold", fixedSize: size * (number >= 100 ? 0.27 : 0.32)))
                .monospacedDigit()
                .foregroundStyle(c.ink)
        }
        .frame(width: size, height: size)
        .animation(QhMotion.snappy, value: status)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(number)")
    }
}

#Preview("Ornaments light") { OrnamentsPreview().qhPreviewTheme() }
#Preview("Ornaments dark") { OrnamentsPreview().qhPreviewTheme(dark: true) }

private struct OrnamentsPreview: View {
    var body: some View {
        VStack(spacing: 20) {
            HStack(spacing: 14) {
                SurahNumberBadge(number: 1, status: .notStarted)
                SurahNumberBadge(number: 36, status: .learning, learnedFraction: 0.6)
                SurahNumberBadge(number: 114, status: .learned)
                Medallion(size: 40)
                AnimatedCheckmark(size: 32)
            }
            HStack(spacing: 14) {
                AyahRosette(number: 1)
                AyahRosette(number: 12, status: .learning)
                AyahRosette(number: 255, status: .learned, weak: true)
            }
            OrnamentDivider()
            SurahCartouche { Text("سُورَةُ البَقَرَة").qhArabic(QhTypography().quranHeader(.amiri)) }
            MihrabCalligraphy()
        }
        .padding()
    }
}
