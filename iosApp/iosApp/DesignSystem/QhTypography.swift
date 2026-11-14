import SwiftUI

/// One step of the type scale: Manrope (variable font, instanced as Manrope-Regular ... ExtraBold), scaled with Dynamic Type.
struct QhTextStyle: Equatable {
    let size: CGFloat
    let lineHeight: CGFloat
    let postScriptName: String
    let tracking: CGFloat
    /// The system text style the size scales relative to.
    let relativeTo: Font.TextStyle

    var font: Font { .custom(postScriptName, size: size, relativeTo: relativeTo) }
    /// Extra leading to reach `lineHeight` at the base size.
    var leading: CGFloat { max(0, lineHeight - size * 1.2) }

    /// The same step in another Manrope weight ("Regular", "Medium", "SemiBold", "Bold", "ExtraBold").
    func weight(_ name: String) -> QhTextStyle {
        QhTextStyle(size: size, lineHeight: lineHeight, postScriptName: "Manrope-\(name)", tracking: tracking, relativeTo: relativeTo)
    }
    /// The same step at another size (keeps the line height ratio).
    func sized(_ newSize: CGFloat) -> QhTextStyle {
        QhTextStyle(size: newSize, lineHeight: lineHeight * newSize / size, postScriptName: postScriptName, tracking: tracking, relativeTo: relativeTo)
    }
}

/// The Android text scale (section 2.2) mapped to SwiftUI fonts. Arabic faces are factories because they depend on the chosen Quran font.
struct QhTypography: Equatable {
    /// 34/40, 800: large screen title.
    var display = QhTypography.ui(34, 40, "ExtraBold", -0.6, .largeTitle)
    /// 26/32, 700: hero heading, session summary.
    var headline = QhTypography.ui(26, 32, "Bold", -0.3, .title)
    /// 21/28, 700: surah name on a card, sheet titles.
    var title1 = QhTypography.ui(21, 28, "Bold", -0.2, .title2)
    /// 17/24, 600: surah name in a row, settings items.
    var title2 = QhTypography.ui(17, 24, "SemiBold", 0, .headline)
    /// 16/24, 400: translation, descriptions.
    var body = QhTypography.ui(16, 24, "Regular", 0, .body)
    /// 14/20, 500: captions, metadata.
    var callout = QhTypography.ui(14, 20, "Medium", 0, .callout)
    /// 13/16, 600: chips, segments, small buttons.
    var label = QhTypography.ui(13, 16, "SemiBold", 0.1, .footnote)
    /// 11/14, 700, tracking 1.0: section overlines (pass the text through `.uppercased()`).
    var overline = QhTypography.ui(11, 14, "Bold", 1.0, .caption2)

    private static func ui(_ size: CGFloat, _ lh: CGFloat, _ weight: String, _ tracking: CGFloat, _ rel: Font.TextStyle) -> QhTextStyle {
        QhTextStyle(size: size, lineHeight: lh, postScriptName: "Manrope-\(weight)", tracking: tracking, relativeTo: rel)
    }

    /// Arabic surah name in the reader header: 30-34pt, line height 1.6x.
    func quranHeader(_ face: QhQuranFace, size: CGFloat = 32) -> QhArabicStyle { QhArabicStyle(face: face, size: size, lineFactor: 1.6) }
    /// Ayah text: `size` is `readingFontSize` (22-40), line height 1.85x.
    func quranBody(_ face: QhQuranFace, size: CGFloat = 28) -> QhArabicStyle { QhArabicStyle(face: face, size: size, lineFactor: 1.85) }
    /// Arabic surah name in lists: 19pt, line height 1.4x.
    func quranInline(_ face: QhQuranFace, size: CGFloat = 19) -> QhArabicStyle { QhArabicStyle(face: face, size: size, lineFactor: 1.4) }
}

/// Quran faces bundled with the app (UIAppFonts).
enum QhQuranFace: String, CaseIterable, Identifiable {
    case amiri, madinah, scheherazade, lateef, naskh, nastaliq
    var id: String { rawValue }

    var postScriptName: String {
        switch self {
        case .amiri: return "AmiriQuran-Regular"
        case .madinah: return "KFGQPCHafsEx1UthmanicScript-Reg"
        case .scheherazade: return "ScheherazadeNew-Regular"
        case .lateef: return "Lateef-Regular"
        case .naskh: return "NotoNaskhArabic-Regular"
        case .nastaliq: return "NotoNastaliqUrdu-Regular"
        }
    }
}

/// An Arabic text style: face, size and the line height factor (the Arabic faces have tall ascenders and vowel marks).
struct QhArabicStyle: Equatable {
    let face: QhQuranFace
    let size: CGFloat
    let lineFactor: CGFloat

    var font: Font { .custom(face.postScriptName, size: size, relativeTo: .body) }
    var leading: CGFloat { size * (lineFactor - 1.2) }
}

/// The reading size at which the fixed Arabic sizes of the sheets and cards were drawn.
let qhArabicBaseReadingSize = 28

/// `base` (the size drawn for the default reading size of 28) scaled by the reader's Arabic size setting, factor kept between 0.85 and 1.3.
func scaledArabicSize(_ base: CGFloat, readingFontSize: Int) -> CGFloat {
    let factor = min(max(CGFloat(readingFontSize) / CGFloat(qhArabicBaseReadingSize), 0.85), 1.3)
    return base * factor
}

// MARK: - View modifiers

private struct QhTextModifier: ViewModifier {
    let style: QhTextStyle
    let color: Color?
    @ScaledMetric private var leading: CGFloat

    init(style: QhTextStyle, color: Color?) {
        self.style = style
        self.color = color
        _leading = ScaledMetric(wrappedValue: style.leading, relativeTo: style.relativeTo)
    }

    func body(content: Content) -> some View {
        content
            .font(style.font)
            .tracking(style.tracking)
            .lineSpacing(leading)
            .foregroundStyle(color ?? Color.primary)
    }
}

private struct QhThemedTextModifier: ViewModifier {
    @Environment(\.qh) private var qh
    let pick: (QhTypography) -> QhTextStyle
    let color: ((QhColors) -> Color)?

    func body(content: Content) -> some View {
        content.modifier(QhTextModifier(style: pick(qh.type), color: color?(qh.colors) ?? qh.colors.ink))
    }
}

private struct QhArabicModifier: ViewModifier {
    @Environment(\.qh) private var qh
    let style: QhArabicStyle
    let color: Color?
    @ScaledMetric private var leading: CGFloat

    init(style: QhArabicStyle, color: Color?) {
        self.style = style
        self.color = color
        _leading = ScaledMetric(wrappedValue: style.leading, relativeTo: .body)
    }

    func body(content: Content) -> some View {
        content
            .font(style.font)
            .lineSpacing(leading)
            .foregroundStyle(color ?? qh.colors.ink)
    }
}

extension View {
    /// Applies a step of the scale (font, tracking, leading, Dynamic Type) and a colour from the theme (`ink` by default).
    func qhText(_ pick: @escaping (QhTypography) -> QhTextStyle, color: ((QhColors) -> Color)? = nil) -> some View {
        modifier(QhThemedTextModifier(pick: pick, color: color))
    }

    /// Arabic text with a Quran face, its leading and the `ink` colour.
    func qhArabic(_ style: QhArabicStyle, color: Color? = nil) -> some View {
        modifier(QhArabicModifier(style: style, color: color))
    }

    /// Tabular digits: counters, timers and ayah numbers do not jitter.
    func qhTabular() -> some View { monospacedDigit() }
}
