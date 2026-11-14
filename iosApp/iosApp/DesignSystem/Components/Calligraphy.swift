import SwiftUI

// The motif is Arabic calligraphy in the bundled Amiri Quran font (the bismillah ligature, iqra, a word of praise), the mihrab arch,
// a 12-lobed medallion, an ogee lattice and a rhombus. Pointed radial figures are deliberately absent: neither drawn ones, nor icons.

/// Phrases the app writes as calligraphy; all of them are shaped by Amiri Quran.
enum Calligraphy {
    /// The bismillah: in Amiri Quran this single code point is one calligraphic ligature.
    static let bismillah = "\u{FDFD}"
    /// "Read!" (iqra), the first word revealed.
    static let iqra = "ٱقْرَأْ"
    /// "Praise be to God".
    static let alhamdulillah = "ٱلْحَمْدُ لِلَّهِ"
    /// "Memorization" (hifz).
    static let hifz = "حِفْظ"
}

/// Duration of the single glint over the letters.
private let glintDuration: Double = 0.9

/// Reveals content from right to left like ink being laid down: a soft edge sweeps leftward as `progress` goes 0 to 1.
private struct InkReveal: ViewModifier, Animatable {
    var progress: CGFloat
    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        if progress >= 1 {
            content
        } else {
            let feather: CGFloat = 0.18
            let edge = (1 + feather) + (-feather - (1 + feather)) * progress
            let a = min(max(edge, 0), 1)
            let b = min(max(edge + feather, 0), 1)
            content.mask(
                LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: .clear, location: a),
                                       .init(color: .black, location: b), .init(color: .black, location: 1)],
                               startPoint: .leading, endPoint: .trailing)
            )
        }
    }
}

/// A band of light running right to left, drawn only over the letters.
private struct Glint: ViewModifier, Animatable {
    var progress: CGFloat
    let color: Color
    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        content.overlay {
            if progress > 0 && progress < 1 {
                let band: CGFloat = 0.22
                let start = 1 + (-band - 1) * progress
                LinearGradient(stops: [.init(color: color.opacity(0), location: min(max(start, 0), 1)),
                                       .init(color: color, location: min(max(start + band / 2, 0), 1)),
                                       .init(color: color.opacity(0), location: min(max(start + band, 0), 1))],
                               startPoint: .leading, endPoint: .trailing)
                    .mask(content)
                    .allowsHitTesting(false)
            }
        }
    }
}

/// `text` written in Amiri Quran as an illustration: it is revealed from right to left like ink being laid down (a soft edge sweeps
/// leftward over `revealDuration` after `revealDelay`) and, if `shimmer`, a single glint then runs over the letters. `size` is in points,
/// so Dynamic Type does not apply; when the text is wider than the available width it shrinks to fit. Under Reduce Motion it is shown
/// complete, with no glint. Decorative: hidden from VoiceOver.
struct CalligraphyText: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let text: String
    var size: CGFloat = 40
    var color: Color?
    var revealDuration: Double = 1.4
    var revealDelay: Double = 0
    var shimmer = true
    @State private var reveal: CGFloat
    @State private var glint: CGFloat = 0

    init(_ text: String, size: CGFloat = 40, color: Color? = nil, revealDuration: Double = 1.4, revealDelay: Double = 0, shimmer: Bool = true) {
        self.text = text
        self.size = size
        self.color = color
        self.revealDuration = revealDuration
        self.revealDelay = revealDelay
        self.shimmer = shimmer
        _reveal = State(initialValue: revealDuration <= 0 ? 1 : 0)
    }

    var body: some View {
        let tint = color ?? qh.colors.gold
        let letters = Text(text)
            .font(.custom(QhQuranFace.amiri.postScriptName, fixedSize: size))
            .foregroundStyle(tint)
            .lineLimit(1)
            .minimumScaleFactor(0.2)
            .environment(\.layoutDirection, .rightToLeft)
            .multilineTextAlignment(.center)
        letters
            .modifier(Glint(progress: glint, color: tint.blended(with: .white, by: 0.55)))
            .modifier(InkReveal(progress: reveal))
            .accessibilityHidden(true)
            .task(id: reduced) {
                if reduced || revealDuration <= 0 {
                    reveal = 1
                    return
                }
                withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: revealDuration).delay(revealDelay)) { reveal = 1 }
                guard shimmer else { return }
                try? await Task.sleep(for: .seconds(revealDelay + revealDuration))
                guard !Task.isCancelled else { return }
                withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: glintDuration)) { glint = 1 }
            }
    }
}

#Preview("Calligraphy light") { CalligraphyPreview().qhPreviewTheme() }
#Preview("Calligraphy dark") { CalligraphyPreview().qhPreviewTheme(dark: true) }

private struct CalligraphyPreview: View {
    var body: some View {
        VStack(spacing: 8) {
            CalligraphyText(Calligraphy.bismillah, size: 64)
            CalligraphyText(Calligraphy.alhamdulillah, size: 44)
            CalligraphyText(Calligraphy.hifz, size: 40)
        }
        .padding()
    }
}
