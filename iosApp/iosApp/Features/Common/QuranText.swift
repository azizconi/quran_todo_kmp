import SwiftUI

/// How a hidden ayah is veiled (the "Recall" step). `hidden` is the amount of the veil (1 = hidden, 0 = shown) and is meant to be
/// animated by the owner with `withAnimation`; `shown` are the words that stay sharp whatever the amount.
struct QuranVeil: Equatable {
    var hidden: CGFloat
    var shown: Set<Int>
    /// The "first letters" level: the first letter of every veiled word is drawn in its place.
    var letters = false
    /// Words a tap opened: a thin gold line under them.
    var peeked: Set<Int> = []
    /// Opacity of the veiled text at full veil (0.35 for the ayah being recalled, 0.6 for one that waits).
    var coveredAlpha: CGFloat = 0.35
}

/// The anchor of one word, for a popover over it (word meanings): set `anchoredWord` and read the preference.
struct QuranWordAnchorKey: PreferenceKey {
    static var defaultValue: Anchor<CGRect>?
    static func reduce(value: inout Anchor<CGRect>?, nextValue: () -> Anchor<CGRect>?) { value = value ?? nextValue() }
}

/// One ayah, word by word, right to left, in the chosen reading face and size.
///
/// - tajweed colours from `ayah.tajweed` when `showTajweed` (only the colour changes);
/// - `currentWord`: the word being recited gets a wash (the owner animates it with `withAnimation`/`.animation`);
/// - `accentWords`: words drawn in the accent colour with a faint wash (the differing words of a similar verse);
/// - `veil`: hidden words of the recall (blur + opacity per word, first letters, peek underline);
/// - `onWordTap`: a tap on a word (seek, meaning popover, peek); without it the ayah is a single static accessibility element;
/// - `endMarker`: the ayah number in a round marker after the last word.
///
/// Performance: the model is a cached value (`QuranAyahDisplay`); a word is one small `Text`; attributed strings of coloured words are
/// cached per ayah, word and theme; the layout measures each word once per pass. A row is `Equatable`, so a parent that re-renders for
/// another reason does not re-lay-out an ayah that did not change.
struct QuranText: View, Equatable {
    @Environment(\.qh) private var qh
    @ScaledMetric(relativeTo: .body) private var fontSize: CGFloat = 28

    let ayah: QuranAyahDisplay
    let face: QhQuranFace
    let readingSize: CGFloat
    var showTajweed = false
    var currentWord: Int?
    var accentWords: Set<Int> = []
    var anchoredWord: Int?
    var veil: QuranVeil?
    var endMarker = false
    var color: Color?
    var onWordTap: ((Int) -> Void)?

    init(ayah: QuranAyahDisplay, face: QhQuranFace, readingSize: CGFloat = 28, showTajweed: Bool = false, currentWord: Int? = nil,
         accentWords: Set<Int> = [], anchoredWord: Int? = nil, veil: QuranVeil? = nil, endMarker: Bool = false, color: Color? = nil,
         onWordTap: ((Int) -> Void)? = nil) {
        self.ayah = ayah
        self.face = face
        self.readingSize = readingSize
        self.showTajweed = showTajweed
        self.currentWord = currentWord
        self.accentWords = accentWords
        self.anchoredWord = anchoredWord
        self.veil = veil
        self.endMarker = endMarker
        self.color = color
        self.onWordTap = onWordTap
        _fontSize = ScaledMetric(wrappedValue: readingSize, relativeTo: .body)
    }

    static func == (a: QuranText, b: QuranText) -> Bool {
        a.ayah.id == b.ayah.id && a.ayah.text == b.ayah.text && a.face == b.face && a.readingSize == b.readingSize
            && a.showTajweed == b.showTajweed && a.currentWord == b.currentWord && a.accentWords == b.accentWords
            && a.anchoredWord == b.anchoredWord && a.veil == b.veil && a.endMarker == b.endMarker && a.color == b.color
            && (a.onWordTap == nil) == (b.onWordTap == nil)
    }

    var body: some View {
        let c = qh.colors
        let ink = color ?? c.ink
        let lineHeight = fontSize * 1.85
        let font = Font.custom(face.postScriptName, fixedSize: fontSize)
        let tapping = onWordTap != nil
        WordFlowLayout(lineHeight: lineHeight, spacing: fontSize * 0.28) {
            ForEach(ayah.words, id: \.index) { word in
                let sharp = veil == nil || veil!.shown.contains(word.index)
                QuranWordView(
                    word: word,
                    ayahID: ayah.id,
                    font: font,
                    fontSize: fontSize,
                    ink: ink,
                    inkSecondary: c.inkSecondary,
                    dark: qh.isDark,
                    tajweed: showTajweed,
                    accent: accentWords.contains(word.index) ? c.primary : nil,
                    wash: word.index == currentWord ? c.goldContainer : (accentWords.contains(word.index) ? c.primaryContainer.opacity(0.5) : nil),
                    veilAmount: veil.map { sharp ? 0 : $0.hidden } ?? 0,
                    veiled: veil != nil,
                    coveredAlpha: veil?.coveredAlpha ?? 1,
                    letters: veil?.letters == true,
                    underline: veil?.peeked.contains(word.index) == true ? c.gold : nil,
                    anchored: anchoredWord == word.index,
                    tap: tapping ? { onWordTap?(word.index) } : nil
                )
            }
            if endMarker {
                AyahEndMarker(number: ayah.ayah, size: fontSize * 0.9)
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .environment(\.layoutDirection, .leftToRight)
        .modifier(StaticAccessibility(enabled: !tapping, label: ayah.text))
    }
}

private struct StaticAccessibility: ViewModifier {
    let enabled: Bool
    let label: String
    func body(content: Content) -> some View {
        if enabled {
            content.accessibilityElement(children: .ignore).accessibilityLabel(Text(label)).accessibilityAddTraits(.isStaticText)
        } else {
            content
        }
    }
}

// MARK: - A word

private final class AttributedBox {
    let value: AttributedString
    init(_ value: AttributedString) { self.value = value }
}

private enum AttributedCache {
    static let cache: NSCache<NSString, AttributedBox> = {
        let c = NSCache<NSString, AttributedBox>()
        c.countLimit = 6000
        return c
    }()
}

private struct QuranWordView: View, Equatable {
    let word: QuranWordDisplay
    let ayahID: Int
    let font: Font
    let fontSize: CGFloat
    let ink: Color
    let inkSecondary: Color
    let dark: Bool
    let tajweed: Bool
    let accent: Color?
    let wash: Color?
    let veilAmount: CGFloat
    let veiled: Bool
    let coveredAlpha: CGFloat
    let letters: Bool
    let underline: Color?
    let anchored: Bool
    let tap: (() -> Void)?

    static func == (a: QuranWordView, b: QuranWordView) -> Bool {
        a.ayahID == b.ayahID && a.word == b.word && a.fontSize == b.fontSize && a.ink == b.ink && a.dark == b.dark
            && a.tajweed == b.tajweed && a.accent == b.accent && a.wash == b.wash && a.veilAmount == b.veilAmount
            && a.veiled == b.veiled && a.coveredAlpha == b.coveredAlpha && a.letters == b.letters && a.underline == b.underline
            && a.anchored == b.anchored && (a.tap == nil) == (b.tap == nil)
    }

    private var content: Text {
        guard tajweed, !word.tajweed.isEmpty else { return Text(verbatim: word.text) }
        let key = "\(ayahID)-\(word.index)-\(dark ? 1 : 0)" as NSString
        if let hit = AttributedCache.cache.object(forKey: key) { return Text(hit.value) }
        var attributed = AttributedString(word.text)
        for run in word.tajweed {
            let range = NSRange(location: run.start, length: run.end - run.start)
            if let r = Range(range, in: attributed) {
                attributed[r].foregroundColor = TajweedPalette.color(run.rule, dark: dark)
            }
        }
        AttributedCache.cache.setObject(AttributedBox(attributed), forKey: key)
        return Text(attributed)
    }

    var body: some View {
        let base = content
            .font(font)
            .foregroundStyle(accent ?? ink)
            .padding(.horizontal, 1)
            .background {
                if let wash { RoundedRectangle(cornerRadius: 6, style: .continuous).fill(wash).padding(.vertical, 2) }
            }
        Group {
            if veiled {
                base
                    .opacity(1 - (1 - coveredAlpha) * veilAmount)
                    .blur(radius: 14 * veilAmount)
                    .overlay(alignment: .trailing) {
                        if letters, let letter = word.firstLetter {
                            Text(verbatim: letter).font(font).foregroundStyle(inkSecondary).opacity(veilAmount)
                                .padding(.horizontal, 1).allowsHitTesting(false)
                        }
                    }
                    .overlay(alignment: .bottom) {
                        if let underline {
                            Capsule().fill(underline).frame(height: 1.5).padding(.horizontal, 2).offset(y: -fontSize * 0.28)
                        }
                    }
            } else {
                base
            }
        }
        .anchorPreference(key: QuranWordAnchorKey.self, value: .bounds) { anchored ? $0 : nil }
        .modifier(WordTap(tap: tap))
    }
}

private struct WordTap: ViewModifier {
    let tap: (() -> Void)?
    func body(content: Content) -> some View {
        if let tap {
            content.contentShape(Rectangle().inset(by: -4)).onTapGesture(perform: tap).accessibilityAddTraits(.isButton)
        } else {
            content
        }
    }
}

/// The ayah number in a round marker (gold ring), at the end of the last line.
struct AyahEndMarker: View {
    @Environment(\.qh) private var qh
    let number: Int
    var size: CGFloat = 26

    var body: some View {
        ZStack {
            Circle().strokeBorder(qh.colors.gold.opacity(0.8), lineWidth: 1.2)
            Circle().strokeBorder(qh.colors.gold.opacity(0.35), lineWidth: 0.8).padding(2.5)
            Text("\(number)")
                .font(.custom("Manrope-Bold", fixedSize: size * (number >= 100 ? 0.32 : 0.4)))
                .monospacedDigit()
                .foregroundStyle(qh.colors.inkSecondary)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
