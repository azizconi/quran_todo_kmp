import ComposeApp
import Observation
import SwiftUI

@MainActor
@Observable
final class TafsirModel {
    @ObservationIgnored private let holder: VMHolder<TafsirViewModel>
    let ayah: FlowState<KotlinInt>
    let source: FlowState<TafsirSource>
    let load: FlowState<TafsirLoad>
    let page: FlowState<TafsirView?>
    let surahName: FlowState<String>
    var vm: TafsirViewModel { holder.vm }

    init(route: Route) {
        let dest = route.dest as! DestTafsir
        let holder = VMHolder { IosUi.shared.tafsir(route: dest) }
        self.holder = holder
        let vm = holder.vm
        ayah = FlowState(vm.ayah)
        source = FlowState(vm.source)
        load = FlowState(vm.load)
        page = FlowState(vm.page)
        surahName = FlowState(vm.surahName)
    }
}

/// The commentary of one ayah as a full screen (`Dest.Tafsir`, F4b): the ayah in our text with its translation on top, the chips of the
/// sources, the commentary under them, the arrows to the previous and the next ayah. The size of the text is the reading size of the
/// "Aa" sheet. Without network and without a copy: "An internet connection is needed to load the commentary" and "Retry".
struct TafsirScreen: View {
    let route: Route
    @State private var box = LazyModelBox<TafsirModel>()
    @State private var stagger = StaggerState(screen: "tafsir")
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy

    private var model: TafsirModel {
        if let made = box.value { return made }
        let made = TafsirModel(route: route)
        box.value = made
        return made
    }

    var body: some View {
        let model = model
        let t = copy.tafsir
        let prefs = ReadingPrefs.shared
        let ayah = Int(truncating: model.ayah.value)
        let source = model.source.value
        let page = model.page.value
        let size = CGFloat(prefs.size)
        let bodySize = min(max(size * 0.62, 15), 24)
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                HStack {
                    QhIconButton(icon: "chevron.backward", label: t.previousAyah) { model.vm.go(delta: -1) }.disabled(ayah <= 1)
                    Text("\(model.surahName.value) \u{00B7} \(t.ayahWord) \(ayah)").qhText({ $0.title2 }).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity).contentTransition(.numericText())
                    QhIconButton(icon: "chevron.forward", label: t.nextAyah) { model.vm.go(delta: 1) }.disabled(ayah >= Int(model.vm.ayahCount))
                }
                .staggeredEnter(index: 0, state: stagger)
                if let page {
                    QhCard {
                        let display = QuranAyahDisplay(surah: Int(model.vm.surah), ayah: ayah, displayText: page.arabic,
                                                       ranges: AyahWordsKt.wordRanges(text: page.arabic).map { Int($0.first)..<(Int($0.last) + 1) })
                        QuranText(ayah: display, face: prefs.face, readingSize: size)
                        if let tr = page.translation {
                            Text(tr).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x3)
                        }
                    }
                    .staggeredEnter(index: 1, state: stagger)
                    .id(ayah)
                    .transition(.opacity)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: QhSpacing.x2) {
                        ForEach(TafsirSource.companion.chipsFor(language: copy.language), id: \.key) { entry in
                            QhChip(label: t.chip(source: entry), selected: entry == source) { model.vm.select(next: entry) }
                        }
                    }
                }
                .staggeredEnter(index: 2, state: stagger)
                commentary(model, t, source, page, bodySize: bodySize)
                    .staggeredEnter(index: 3, state: stagger)
            }
            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
            .qhAnimation(QhMotion.gentle, value: ayah)
        }
        .background(qh.colors.bg)
        .navigationTitle(t.title)
        .navigationBarTitleDisplayMode(.inline)
        .qhNavigationStyle()
        .toolbar(.hidden, for: .tabBar)
        .task(id: copy.language.name) { model.vm.onLanguage(next: copy.language) }
        .onAppear { stagger.markPlayed() }
    }

    @ViewBuilder private func commentary(_ model: TafsirModel, _ t: TafsirCopy, _ source: TafsirSource, _ page: TafsirView?, bodySize: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            switch onEnum(of: model.load.value) {
            case .loading:
                HStack(spacing: QhSpacing.x3) {
                    ProgressView().controlSize(.small)
                    Text(t.loading).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            case .missing:
                Text(t.needInternet).qhText({ $0.body }, color: { $0.inkSecondary })
                QhButton(t.retry, variant: .tonal, size: .m) { model.vm.retry() }
            case .ready(let state):
                if let page, page.passage == nil {
                    Text(t.noCommentary).qhText({ $0.callout }, color: { $0.inkSecondary })
                } else if let passage = page?.passage {
                    notes(t, source, passage, surah: Int(model.vm.surah))
                    TafsirBlocks(text: passage.text, bodySize: bodySize, arabicSource: source == TafsirSource.muyassar)
                        .id("\(source.key)-\(passage.first)")
                    if source.provider == TafsirProvider.quranEnc {
                        Text(t.creditLine(version: state.chapter.version)).qhText({ $0.label }, color: { $0.inkTertiary })
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func notes(_ t: TafsirCopy, _ source: TafsirSource, _ passage: TafsirPassage, surah: Int) -> some View {
        var parts: [String] = []
        if !TafsirSource.companion.isInLanguageOf(source: source, language: copy.language) { parts.append(t.writtenIn(source: source)) }
        if source.cyrillic { parts.append(t.cyrillicNote) }
        if passage.isGroup { parts.append(t.group(surah: Int32(surah), first: passage.first, last: passage.last)) }
        return Group {
            if !parts.isEmpty { Text(parts.joined(separator: " \u{00B7} ")).qhText({ $0.callout }, color: { $0.inkSecondary }) }
        }
    }
}

// MARK: - The blocks of the commentary

/// The text of the commentary, block by block (a long text is not laid out as one piece). HTML of Ibn Kathir / Muyassar is read by a
/// small parser (bold, italic, line breaks, entities); Arabic inside a Russian or English commentary is drawn in the reading face.
struct TafsirBlocks: View {
    @Environment(\.qh) private var qh
    let text: String
    let bodySize: CGFloat
    let arabicSource: Bool
    @State private var blocks: [AttributedString]?

    var body: some View {
        let prefs = ReadingPrefs.shared
        Group {
            if let blocks {
                VStack(alignment: .leading, spacing: QhSpacing.x3) {
                    ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                        Text(block).foregroundStyle(qh.colors.ink).lineSpacing(bodySize * 0.45)
                            .frame(maxWidth: .infinity, alignment: arabicSource ? .trailing : .leading)
                            .environment(\.layoutDirection, arabicSource ? .rightToLeft : .leftToRight)
                    }
                }
            } else {
                ProgressView().controlSize(.small)
            }
        }
        .task(id: "\(text.hashValue)-\(prefs.size)-\(prefs.style.storageValue)") {
            let face = prefs.face.postScriptName
            let size = bodySize, arabic = arabicSource
            blocks = await Task.detached(priority: .userInitiated) { TafsirHTMLParser.blocks(text, bodySize: size, arabicFace: face, arabicOnly: arabic) }.value
        }
    }
}

enum TafsirHTMLParser {
    /// The blocks of `TafsirHtml.blocks` as attributed strings.
    static func blocks(_ text: String, bodySize: CGFloat, arabicFace: String, arabicOnly: Bool) -> [AttributedString] {
        let isHtml = TafsirHtml.shared.isHtml(text: text)
        return TafsirHtml.shared.blocks(text: text).map { block in
            var out = isHtml ? parse(block, size: bodySize) : plain(block, size: bodySize)
            applyArabic(&out, face: arabicFace, size: arabicOnly ? bodySize * 1.25 : bodySize * 1.1)
            return trimmed(out)
        }
    }

    private static func plain(_ s: String, size: CGFloat) -> AttributedString {
        var a = AttributedString(s)
        a.font = .custom("Manrope-Regular", size: size)
        return a
    }

    private static func parse(_ html: String, size: CGFloat) -> AttributedString {
        var result = AttributedString()
        var bold = 0, italic = 0
        var buffer = ""
        func flush() {
            guard !buffer.isEmpty else { return }
            var piece = AttributedString(decode(buffer))
            piece.font = font(size: size, bold: bold > 0, italic: italic > 0)
            result.append(piece)
            buffer = ""
        }
        var i = html.startIndex
        while i < html.endIndex {
            let c = html[i]
            if c == "<", let close = html[i...].firstIndex(of: ">") {
                let tag = html[html.index(after: i)..<close].lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
                let closing = html[html.index(after: i)] == "/"
                let name = String(tag.prefix { $0.isLetter || $0.isNumber })
                flush()
                switch name {
                case "b", "strong", "h1", "h2", "h3", "h4", "h5", "h6": bold = max(0, bold + (closing ? -1 : 1))
                case "i", "em": italic = max(0, italic + (closing ? -1 : 1))
                case "br": buffer = "\n"; flush()
                case "li": if !closing { buffer = "\u{2022} "; flush() }
                default: break
                }
                i = html.index(after: close)
            } else {
                buffer.append(c)
                i = html.index(after: i)
            }
        }
        flush()
        return result
    }

    private static func font(size: CGFloat, bold: Bool, italic: Bool) -> Font {
        let base = Font.custom(bold ? "Manrope-Bold" : "Manrope-Regular", size: size)
        return italic ? base.italic() : base
    }

    private static func decode(_ s: String) -> String {
        guard s.contains("&") else { return s }
        var out = s
        for (k, v) in ["&nbsp;": "\u{00A0}", "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'", "&apos;": "'", "&laquo;": "\u{00AB}", "&raquo;": "\u{00BB}"] {
            out = out.replacingOccurrences(of: k, with: v)
        }
        return out
    }

    /// Arabic runs are drawn in the reading face.
    private static func applyArabic(_ text: inout AttributedString, face: String, size: CGFloat) {
        let chars = text.characters
        var runStart: AttributedString.Index?
        var ranges: [Range<AttributedString.Index>] = []
        var index = chars.startIndex
        while index < chars.endIndex {
            let scalar = chars[index].unicodeScalars.first!.value
            let arabic = (0x0600...0x06FF).contains(scalar) || (0x0750...0x077F).contains(scalar) || (0xFB50...0xFDFF).contains(scalar) || (0xFE70...0xFEFF).contains(scalar)
            let space = chars[index].isWhitespace
            if arabic { if runStart == nil { runStart = index } } else if !space, let s = runStart { ranges.append(s..<index); runStart = nil }
            index = chars.index(after: index)
        }
        if let s = runStart { ranges.append(s..<chars.endIndex) }
        for r in ranges { text[r].font = .custom(face, size: size) }
    }

    private static func trimmed(_ s: AttributedString) -> AttributedString {
        var out = s
        while let f = out.characters.first, f.isWhitespace { out.characters.removeFirst() }
        while let l = out.characters.last, l.isWhitespace { out.characters.removeLast() }
        return out
    }
}
