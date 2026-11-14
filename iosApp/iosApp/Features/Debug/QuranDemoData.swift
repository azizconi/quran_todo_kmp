#if DEBUG
import Foundation

/// Demo text for the Quran components (`QuranDemo.json`: Al-Fatiha and Al-Baqarah from the bundled `quran-uthmani.tsv`, rub el hizb
/// stripped). Debug only.
enum QuranDemoData {
    private struct File: Decodable {
        struct Surah: Decodable { let surah: Int; let ayahs: [Ayah] }
        struct Ayah: Decodable { let n: Int; let text: String }
        let surahs: [Surah]
    }

    /// The display models of a surah (`splitBismillah` applied), with the demo tajweed when `tajweed` is on.
    static func ayahs(surah: Int, range: ClosedRange<Int>? = nil) -> [QuranAyahDisplay] {
        guard let url = Bundle.main.url(forResource: "QuranDemo", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(File.self, from: data),
              let entry = file.surahs.first(where: { $0.surah == surah }) else { return [] }
        return entry.ayahs.filter { range?.contains($0.n) ?? true }.map { a in
            let shown = QuranDisplay.splitBismillah(surah: surah, ayah: a.n, text: a.text)
            return QuranAyahDisplay(surah: surah, ayah: a.n, displayText: shown, ranges: QuranDisplay.wordRanges(shown),
                                    tajweed: DemoTajweed.runs(in: shown))
        }
    }

    static let fatiha = ayahs(surah: 1)
    static let baqarah1to20 = ayahs(surah: 2, range: 1...20)
    static let baqarah = ayahs(surah: 2)
}

/// A rough, rule-of-thumb tajweed for the demo only (the real runs come from the shared `TajweedAlignment`, which needs the downloaded
/// markup): hamzat al-wasl, lam shamsiyah, dagger-alif madd, maddah, ghunna on a doubled nun or meem, qalqalah letters with a sukun.
enum DemoTajweed {
    static func runs(in text: String) -> [TajweedRun] {
        let u = Array(text.utf16)
        var out: [TajweedRun] = []
        func isMark(_ c: UInt16) -> Bool { QuranDisplay.isAttached(c) }
        var i = 0
        while i < u.count {
            let c = u[i]
            // The letter and the marks after it.
            var end = i + 1
            while end < u.count, isMark(u[end]) { end += 1 }
            let marks = Set(u[(i + 1)..<end])
            if c == 0x0671 {
                out.append(TajweedRun(rule: .hamzatWasl, start: i, end: end))
                // alif wasla + lam (no vowel, followed by a shadda letter) = sun letter lam
                if end < u.count, u[end] == 0x0644 {
                    var lamEnd = end + 1
                    while lamEnd < u.count, isMark(u[lamEnd]) { lamEnd += 1 }
                    var nextEnd = lamEnd + 1
                    while nextEnd < u.count, isMark(u[nextEnd]) { nextEnd += 1 }
                    if lamEnd < u.count, u[lamEnd..<nextEnd].contains(0x0651) {
                        out.append(TajweedRun(rule: .lamShamsiyah, start: end, end: lamEnd))
                    }
                }
            } else if marks.contains(0x0653) {
                out.append(TajweedRun(rule: .maddNecessary, start: i, end: end))
            } else if marks.contains(0x0670) {
                out.append(TajweedRun(rule: .maddNormal, start: i, end: end))
            } else if (c == 0x0646 || c == 0x0645) && marks.contains(0x0651) {
                out.append(TajweedRun(rule: .ghunna, start: i, end: end))
            } else if [0x0642, 0x0637, 0x0628, 0x062C, 0x062F].contains(c) && (marks.contains(0x0652) || marks.contains(0x06E1)) {
                out.append(TajweedRun(rule: .qalqalah, start: i, end: end))
            } else if c == 0x0646 && (marks.contains(0x0652) || marks.contains(0x06E1)) && end < u.count {
                // noon sakin before ba: iqlab
                var n = end
                while n < u.count, u[n] == 0x0020 { n += 1 }
                if n < u.count, u[n] == 0x0628 { out.append(TajweedRun(rule: .iqlab, start: i, end: end)) }
            }
            i = end
        }
        return out
    }
}
#endif
