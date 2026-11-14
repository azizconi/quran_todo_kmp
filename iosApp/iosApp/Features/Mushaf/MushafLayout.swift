import ComposeApp
import CoreText
import SwiftUI
import UIKit

/// An ayah by surah and number (Kotlin `AyahKey`, as a Swift value).
struct AyahRef: Hashable {
    let surah: Int
    let ayah: Int
    init(_ surah: Int, _ ayah: Int) { self.surah = surah; self.ayah = ayah }
    init(_ key: AyahKey) { surah = Int(key.surah); ayah = Int(key.ayah) }
    var kotlin: AyahKey { AyahKey(surah: Int32(surah), ayah: Int32(ayah)) }
}

/// How an ayah is drawn: as it is, dimmed (outside "Check yourself"), marked hard, or hidden (the one being recalled, the ones waiting).
enum AyahLook: Equatable { case normal, dimmed, hard, hidden, waiting }

struct PlacedWordS {
    let key: AyahRef
    let line: Int
    let x: CGFloat
    let width: CGFloat
    let text: String
    let fontName: String
    let size: CGFloat
    let isEnd: Bool
}

struct AyahSpanS { let key: AyahRef; let line: Int; let left: CGFloat; let right: CGFloat }

/// Everything horizontal about a page, worked out once for a page, a block width and a glyph size (port of Kotlin `PageLayout` and
/// `buildPageLayout`): the measured glyph of every word and its x, the headers, the Bismillahs. The lines are placed by the screen when
/// it draws, so a changing height never measures a text again.
final class MushafLayout {
    let page: Int
    let lineCount: Int
    let blockWidth: CGFloat
    let glyphSize: CGFloat
    private(set) var words: [PlacedWordS] = []
    private(set) var headers: [(line: Int, surah: Int)] = []
    private(set) var bismillahs: [Int] = []
    private(set) var spans: [AyahSpanS] = []
    private var byLine: [Int: [PlacedWordS]] = [:]
    let hafsFont: String
    let headerSize: CGFloat

    static let maxLineEm: CGFloat = 15.85, fitMargin: CGFloat = 1.015, naturalGapEm: CGFloat = 0.045, centerGapEm: CGFloat = 0.2
    static let fullLineShare: CGFloat = 0.9, minSlot: CGFloat = 1.5, maxSlot: CGFloat = 2.3, sideMargin: CGFloat = 14, maxBlock: CGFloat = 520

    static func glyphSize(block: CGFloat) -> CGFloat { block / (maxLineEm * fitMargin) }
    static func blockWidth(screen: CGFloat) -> CGFloat { min(max(screen - 2 * sideMargin, 1), maxBlock) }
    static func slotHeight(body: CGFloat, glyph: CGFloat, lineCount: Int = 15) -> CGFloat {
        min(max(body / CGFloat(lineCount), minSlot * glyph), maxSlot * glyph)
    }

    /// Measures `data` at `glyphSize` in a block `blockWidth` wide. With `textFace` (any face but the Madinah one, whose design the page
    /// fonts are) a word is the text `wordTexts` gives for it, drawn in that face; the size is then fitted to the widest line.
    static func build(data: MushafPageData, glyphSize: CGFloat, blockWidth: CGFloat, arabicName: (Int) -> String,
                      textFace: QhQuranFace?, wordTexts: [AyahRef: [String]], fitted: Bool = false) -> MushafLayout {
        MushafLayout(data: data, glyphSize: glyphSize, blockWidth: blockWidth, arabicName: arabicName, textFace: textFace, wordTexts: wordTexts, fitted: fitted)
    }

    private init(data: MushafPageData, glyphSize inputGlyph: CGFloat, blockWidth: CGFloat, arabicName: (Int) -> String,
                 textFace: QhQuranFace?, wordTexts: [AyahRef: [String]], fitted: Bool) {
        let page = data.page
        self.page = Int(page.number)
        self.lineCount = Int(page.lineCount)
        self.blockWidth = blockWidth
        self.hafsFont = QhQuranFace.madinah.postScriptName
        var glyphSize = inputGlyph
        let isText = textFace != nil

        func uifont(_ name: String, _ size: CGFloat) -> UIFont { UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size) }
        func width(_ text: String, _ font: UIFont) -> CGFloat {
            let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
            return CTLineGetTypographicBounds(line, nil, nil, nil).rounded(.up)
        }

        // Words as drawn: the glyph of the page font, or the text of the reading face.
        struct Raw { let word: MushafWord; let text: String; let font: String; let isText: Bool }
        var lines: [(number: Int, raws: [Raw])] = []
        for line in page.lines {
            if case .words(let w) = onEnum(of: line) {
                let raws: [Raw] = w.words.map { word in
                    let key = AyahRef(Int(word.surah), Int(word.ayah))
                    if let face = textFace, !word.isAyahEnd, let texts = wordTexts[key], texts.indices.contains(Int(word.position) - 1) {
                        return Raw(word: word, text: texts[Int(word.position) - 1], font: face.postScriptName, isText: true)
                    }
                    return Raw(word: word, text: word.glyph, font: data.fontName, isText: false)
                }
                lines.append((Int(w.number), raws))
            }
        }

        func measure(_ glyph: CGFloat) -> (widths: [[CGFloat]], naturals: [CGFloat], max: CGFloat) {
            let gap = (isText ? 0.22 : Self.naturalGapEm) * glyph
            var cache: [String: CGFloat] = [:]
            var widths: [[CGFloat]] = [], naturals: [CGFloat] = []
            for line in lines {
                let ws = line.raws.map { raw -> CGFloat in
                    let size = raw.isText ? glyph * 0.8 : glyph
                    let key = "\(raw.font)|\(size)|\(raw.text)"
                    if let c = cache[key] { return c }
                    let w = width(raw.text, uifont(raw.font, size)); cache[key] = w; return w
                }
                widths.append(ws)
                naturals.append(ws.reduce(0, +) + gap * CGFloat(max(ws.count - 1, 0)))
            }
            return (widths, naturals, naturals.max() ?? 0)
        }

        var measured = measure(glyphSize)
        if isText, !fitted, measured.max > 0 {
            let fit = min(max(blockWidth / measured.max * 0.995, 0.6), 1.3)
            glyphSize *= fit
            measured = measure(glyphSize)
        } else if measured.max > blockWidth, measured.max > 0, glyphSize > 1 {
            glyphSize *= blockWidth / measured.max * 0.995
            measured = measure(glyphSize)
        }
        self.glyphSize = glyphSize
        self.headerSize = glyphSize * 0.95

        let naturalGap = (isText ? 0.22 : Self.naturalGapEm) * glyphSize
        func isFull(_ natural: CGFloat) -> Bool {
            self.page > 2 && natural >= measured.max * (isText ? 0.7 : Self.fullLineShare)
        }
        let fullGaps: [CGFloat] = zip(measured.naturals, measured.widths).filter { isFull($0.0) && $0.1.count > 1 }
            .map { (blockWidth - $0.1.reduce(0, +)) / CGFloat($0.1.count - 1) }
        let centerGap = fullGaps.isEmpty ? Self.centerGapEm * glyphSize
            : min(max(fullGaps.reduce(0, +) / CGFloat(fullGaps.count), naturalGap), Self.centerGapEm * glyphSize * 2)

        var placed: [PlacedWordS] = []
        for (index, line) in lines.enumerated() {
            let ws = measured.widths[index]
            let xs = Self.place(ws, block: blockWidth, justify: isFull(measured.naturals[index]), centerGap: centerGap)
            for (i, raw) in line.raws.enumerated() {
                placed.append(PlacedWordS(key: AyahRef(Int(raw.word.surah), Int(raw.word.ayah)), line: line.number, x: xs[i], width: ws[i],
                                          text: raw.text, fontName: raw.font, size: raw.isText ? glyphSize * 0.8 : glyphSize, isEnd: raw.word.isAyahEnd))
            }
        }
        words = placed
        byLine = Dictionary(grouping: placed, by: \.line)
        for line in page.lines {
            switch onEnum(of: line) {
            case .surahHeader(let h): headers.append((Int(h.number), Int(h.surah)))
            case .bismillah(let b): bismillahs.append(Int(b.number))
            case .words: break
            }
        }
        headerNames = headers.reduce(into: [:]) { $0[$1.surah] = arabicName($1.surah) }
        for number in byLine.keys.sorted() {
            var current: AyahRef?; var left: CGFloat = 0, right: CGFloat = 0
            for w in byLine[number]! {
                if w.key != current {
                    if let c = current { spans.append(AyahSpanS(key: c, line: number, left: left, right: right)) }
                    current = w.key; left = w.x; right = w.x + w.width
                } else { left = min(left, w.x); right = max(right, w.x + w.width) }
            }
            if let c = current { spans.append(AyahSpanS(key: c, line: number, left: left, right: right)) }
        }
    }

    private(set) var headerNames: [Int: String] = [:]

    /// `PageGeometry.place`: the left edges of the words of one line, in reading order (the first word is at the right).
    static func place(_ widths: [CGFloat], block: CGFloat, justify: Bool, centerGap: CGFloat) -> [CGFloat] {
        let n = widths.count
        guard n > 0 else { return [] }
        let sum = widths.reduce(0, +)
        let gap: CGFloat
        var right: CGFloat
        if justify, n > 1 { gap = max((block - sum) / CGFloat(n - 1), 0); right = block }
        else { gap = centerGap; let total = sum + gap * CGFloat(n - 1); right = block - (block - total) / 2 }
        var xs = [CGFloat](repeating: 0, count: n)
        for i in 0..<n { xs[i] = right - widths[i]; right = xs[i] - gap }
        return xs
    }

    func spans(of key: AyahRef) -> [AyahSpanS] { spans.filter { $0.key == key } }

    /// The ayah under the point (`x` in block coordinates) on line `line`; a tap between two words takes the nearer one.
    func ayah(atX x: CGFloat, line: Int) -> AyahRef? {
        guard let ws = byLine[line] else { return nil }
        var best: PlacedWordS?; var bestD = CGFloat.greatestFiniteMagnitude
        for w in ws {
            let d: CGFloat = x < w.x ? w.x - x : (x > w.x + w.width ? x - (w.x + w.width) : 0)
            if d < bestD { best = w; bestD = d }
        }
        return bestD <= glyphSize * 0.6 ? best?.key : nil
    }

    static func pageTop(height: CGFloat, slot: CGFloat, lineCount: Int) -> CGFloat { max((height - CGFloat(lineCount) * slot) / 2, 0) }
}

// MARK: - Drawing

struct PageInk {
    let ink: UIColor, primary: UIColor, due: UIColor, gold: UIColor
    init(_ c: QhColors) { ink = UIColor(c.ink); primary = UIColor(c.primary); due = UIColor(c.due); gold = UIColor(c.gold) }
}

enum MushafRenderer {
    /// The words of the page (those whose look is hidden/waiting are drawn only when `hidden`), headers and Bismillahs, in an image
    /// `size` big. Pure CoreText/UIKit drawing, safe on any thread.
    static func render(_ layout: MushafLayout, size: CGSize, slot: CGFloat, looks: [AyahRef: AyahLook], hidden: Bool, ink: PageInk, scale: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let left = (size.width - layout.blockWidth) / 2
            let top = MushafLayout.pageTop(height: size.height, slot: slot, lineCount: layout.lineCount)
            func center(_ line: Int) -> CGFloat { top + CGFloat(line - 1) * slot + slot * 0.5 }
            if !hidden {
                for h in layout.headers { drawHeader(h, layout: layout, left: left, top: top, slot: slot, ink: ink, ctx: ctx.cgContext) }
                for line in layout.bismillahs {
                    let font = UIFont(name: layout.hafsFont, size: layout.headerSize) ?? .systemFont(ofSize: layout.headerSize)
                    let text = NSAttributedString(string: QuranDisplay.bismillahText, attributes: [.font: font, .foregroundColor: ink.ink])
                    let s = text.size()
                    text.draw(at: CGPoint(x: left + (layout.blockWidth - s.width) / 2, y: center(line) - s.height / 2))
                }
            }
            for w in layout.words {
                let look = looks[w.key] ?? .normal
                let isHidden = look == .hidden || look == .waiting
                if isHidden != hidden { continue }
                var color = w.isEnd ? ink.primary : (look == .hard ? ink.due : ink.ink)
                let alpha: CGFloat = look == .dimmed ? 0.4 : (look == .hidden ? 0.5 : (look == .waiting ? 0.4 : 1))
                color = color.withAlphaComponent(alpha)
                let font = UIFont(name: w.fontName, size: w.size) ?? .systemFont(ofSize: w.size)
                let text = NSAttributedString(string: w.text, attributes: [.font: font, .foregroundColor: color])
                text.draw(at: CGPoint(x: left + w.x, y: center(w.line) - font.lineHeight / 2))
            }
        }
    }

    private static func drawHeader(_ h: (line: Int, surah: Int), layout: MushafLayout, left: CGFloat, top: CGFloat, slot: CGFloat, ink: PageInk, ctx: CGContext) {
        let height = min(slot * 0.84, layout.glyphSize * 1.9)
        let y = top + CGFloat(h.line - 1) * slot + (slot - height) / 2
        let width = layout.blockWidth, corner = height * 0.42
        ctx.setStrokeColor(ink.gold.cgColor); ctx.setLineWidth(1.5)
        ctx.addPath(UIBezierPath(roundedRect: CGRect(x: left + 0.75, y: y + 0.75, width: width - 1.5, height: height - 1.5), cornerRadius: corner).cgPath); ctx.strokePath()
        let gap: CGFloat = 4
        ctx.setStrokeColor(ink.gold.withAlphaComponent(0.55).cgColor); ctx.setLineWidth(1)
        ctx.addPath(UIBezierPath(roundedRect: CGRect(x: left + gap, y: y + gap, width: width - 2 * gap, height: height - 2 * gap), cornerRadius: max(corner - gap, 0)).cgPath); ctx.strokePath()
        let cy = y + height / 2, half = height * 0.17, inset = corner * 0.95 + half
        for side in [-1.0, 1.0] {
            let cx = side < 0 ? left + inset : left + width - inset
            ctx.setFillColor(ink.gold.cgColor)
            ctx.move(to: CGPoint(x: cx, y: cy - half)); ctx.addLine(to: CGPoint(x: cx + half, y: cy))
            ctx.addLine(to: CGPoint(x: cx, y: cy + half)); ctx.addLine(to: CGPoint(x: cx - half, y: cy)); ctx.closePath(); ctx.fillPath()
            ctx.setFillColor(ink.gold.withAlphaComponent(0.8).cgColor)
            for dx in [-half * 2.2, half * 2.2] { ctx.fillEllipse(in: CGRect(x: cx + dx - 1.6, y: cy - 1.6, width: 3.2, height: 3.2)) }
        }
        let name = layout.headerNames[h.surah] ?? ""
        let font = UIFont(name: layout.hafsFont, size: layout.headerSize) ?? .systemFont(ofSize: layout.headerSize)
        let text = NSAttributedString(string: name, attributes: [.font: font, .foregroundColor: ink.ink])
        let s = text.size()
        text.draw(at: CGPoint(x: left + (width - s.width) / 2, y: cy - s.height / 2))
    }
}
