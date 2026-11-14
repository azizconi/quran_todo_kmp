import ComposeApp
import Observation
import SwiftUI

/// The word-by-word meanings of a surah (task F4b), one compact file per surah under Application Support (`words_{surah}.json`, not
/// backed up), the last three in memory. Source: quran.com `verses/by_chapter/{surah}?words=true`, page after page, cut down to the
/// compact copy of the shared `WordMeaningsFormat` right away. Fetched only on the first tap on a word in a surah.
@MainActor
final class WordMeaningsService {
    static let shared = WordMeaningsService()

    private var memory: [Int: WordMeaningsChapter] = [:]
    private var order: [Int] = []
    private var failedAt: [Int: Date] = [:]
    private let directory: URL

    private init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        directory = base.appendingPathComponent("word_meanings", isDirectory: true)
    }

    private func file(_ surah: Int) -> URL { directory.appendingPathComponent("words_\(surah).json") }

    /// From memory or the disk; never the network.
    func local(_ surah: Int) -> WordMeaningsChapter? {
        if let hit = memory[surah] { return hit }
        guard let text = try? String(contentsOf: file(surah), encoding: .utf8),
              let chapter = WordMeaningsFormat.shared.decode(text: text, surah: Int32(surah)) else { return nil }
        remember(surah, chapter)
        return chapter
    }

    private func remember(_ surah: Int, _ chapter: WordMeaningsChapter) {
        memory[surah] = chapter
        order.removeAll { $0 == surah }
        order.append(surah)
        while order.count > 3 { memory[order.removeFirst()] = nil }
    }

    /// The meanings: from the device, else from the network (cached). Nil when neither works.
    func load(_ surah: Int, force: Bool = false) async -> WordMeaningsChapter? {
        if let hit = local(surah) { return hit }
        if !force, let at = failedAt[surah], Date().timeIntervalSince(at) < 30 { return nil }
        guard let chapter = await fetch(surah) else { failedAt[surah] = Date(); return nil }
        failedAt[surah] = nil
        remember(surah, chapter)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? WordMeaningsFormat.shared.encode(chapter: chapter).write(to: file(surah), atomically: true, encoding: .utf8)
        var dir = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dir.setResourceValues(values)
        return chapter
    }

    private func fetch(_ surah: Int) async -> WordMeaningsChapter? {
        var pages: [WordMeaningsPage] = []
        var next: Int? = 1
        while let page = next {
            if pages.count >= 12 { return nil }
            let url = URL(string: "\(Constants.shared.QURAN_COM_API_BASE_URL)verses/by_chapter/\(surah)?words=true&fields=verse_key&per_page=50&page=\(page)")!
            var request = URLRequest(url: url)
            request.timeoutInterval = 30
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  (response as? HTTPURLResponse)?.statusCode == 200,
                  let text = String(data: data, encoding: .utf8),
                  let parsed = WordMeaningsFormat.shared.parsePage(text: text, surah: Int32(surah)) else { return nil }
            pages.append(parsed)
            next = parsed.nextPage.map { Int(truncating: $0) }.flatMap { $0 > page ? $0 : nil }
        }
        return WordMeaningsFormat.shared.assemble(surah: Int32(surah), pages: pages)
    }
}

/// The popup over a tapped word: the meaning in English, the transliteration and "Word 3 of 7"; "Loading the meanings...", "An
/// internet connection is needed once for this surah" with "Retry", and "Word meanings are not available for this ayah". Placed
/// above the word (below when there is no room); a tap outside closes it and goes on to what is under it.
struct WordMeaningHost: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let surah: Int
    let ayah: Int
    let word: Int
    let wordCount: Int
    let anchor: Anchor<CGRect>

    private enum Load { case loading, ready(WordMeaningsChapter), missing }
    @State private var load = Load.loading
    @State private var attempt = 0
    @State private var cardSize = CGSize(width: 180, height: 70)

    var body: some View {
        GeometryReader { proxy in
            let rect = proxy[anchor]
            let t = copy.tafsir
            let above = rect.minY - cardSize.height - 6
            let y = above >= 8 ? above : rect.maxY + 6
            let x = min(max(rect.midX - cardSize.width / 2, 8), max(proxy.size.width - cardSize.width - 8, 8))
            card(t)
                .background(GeometryReader { g in Color.clear.onAppear { cardSize = g.size }.onChange(of: g.size) { _, s in cardSize = s } })
                .offset(x: x, y: y)
                .transition(.scale(scale: 0.92).combined(with: .opacity))
        }
        .task(id: "\(surah)-\(attempt)") {
            if let local = WordMeaningsService.shared.local(surah) { load = .ready(local); return }
            load = .loading
            if let chapter = await WordMeaningsService.shared.load(surah, force: attempt > 0) { load = .ready(chapter) } else { load = .missing }
        }
    }

    @ViewBuilder private func card(_ t: TafsirCopy) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            switch load {
            case .loading:
                HStack(spacing: QhSpacing.x2) {
                    ProgressView().controlSize(.small)
                    Text(t.wordsLoading).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            case .missing:
                Text(t.wordsNeedInternet).qhText({ $0.callout }, color: { $0.inkSecondary })
                Button(t.retry) { attempt += 1 }.qhText({ $0.callout }, color: { $0.primary }).frame(minHeight: QhSpacing.minTouch)
            case .ready(let chapter):
                switch onEnum(of: chapter.lookup(ayah: Int32(ayah), index: Int32(word), wordCount: Int32(wordCount))) {
                case .unavailable:
                    Text(t.wordsUnavailable).qhText({ $0.callout }, color: { $0.inkSecondary })
                case .found(let found):
                    Text(found.meaning.text).qhText({ $0.title2 })
                    if let tr = found.meaning.transliteration { Text(tr).qhText({ $0.callout }, color: { $0.inkSecondary }).italic() }
                    Text(t.word(number: found.number, total: found.total)).qhText({ $0.label }, color: { $0.inkTertiary }).padding(.top, 2)
                }
            }
        }
        .padding(.horizontal, QhSpacing.x4).padding(.vertical, QhSpacing.x3)
        .frame(minWidth: 120, maxWidth: 280, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .background(qh.colors.surface, in: qh.shapes.mdShape)
        .overlay { qh.shapes.mdShape.stroke(qh.colors.hairline, lineWidth: 1) }
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        .accessibilityElement(children: .combine)
    }
}
