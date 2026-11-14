import Foundation

// Swift-side value models of the Quran text components (task IOS-S2b). They mirror the Kotlin types one to one so the Kotlin mapping
// (task S0, SKIE) can be written as a plain conversion:
//   Kotlin `AyahWords(text, ranges)` + `splitBismillah` + `TajweedSpan`  ->  `QuranAyahDisplay`
//   Kotlin `HifzHintMode`                                                 ->  `HifzHintMode`
//   Kotlin `HifzSession` / `HifzItem` / `AyahMark` / `ReviewGrade`        ->  the types below
//   Kotlin `RecallHost` (HifzControls.kt)                                 ->  `RecallActions`
// All offsets are UTF-16 code units, like Kotlin `String` indices, so ranges can be copied without conversion.

/// The five levels of the hint of a hidden ayah (`ui/state/HifzHintMode`, names as in Kotlin: NONE, FIRST_WORD, FIRST_LETTERS,
/// EVERY_OTHER, PROGRESSIVE). ADAPTER(S0): map by name.
enum HifzHintMode: String, CaseIterable, Identifiable, Sendable {
    case none, firstWord, firstLetters, everyOther, progressive
    var id: String { rawValue }

    /// The order the sheet lists the levels in.
    static let sheetOrder: [HifzHintMode] = [.none, .firstWord, .firstLetters, .everyOther, .progressive]
}

// MARK: - Tajweed

/// The 17 rules of the markup (`common/tajweed/TajweedRule`, code letters as in Kotlin). ADAPTER(S0): map by `code`.
enum TajweedRule: Character, CaseIterable, Sendable {
    case hamzatWasl = "h", lamShamsiyah = "l", silentLetter = "s"
    case maddNormal = "n", maddPermissible = "p", maddObligatory = "o", maddNecessary = "m"
    case ghunna = "g", idghamWithGhunna = "a", ikhfa = "f", ikhfaShafawi = "c"
    case iqlab = "i"
    case qalqalah = "q"
    case idghamWithoutGhunna = "u", idghamMutajanisayn = "d", idghamMutaqaribayn = "b", idghamShafawi = "w"
}

/// A coloured piece of the displayed ayah text: `start..<end` in UTF-16 units of `QuranAyahDisplay.text` (Kotlin `TajweedSpan.range`
/// is inclusive: `end = range.last + 1`). Spans do not overlap and are in reading order.
struct TajweedRun: Hashable, Sendable {
    let rule: TajweedRule
    let start: Int
    let end: Int
}

// MARK: - Display model

/// One word of an ayah as the screens draw it. `text` is what the word owns (from its first character to the first character of the
/// next word, so pause marks that stand between two words follow the word before them), without trailing spaces.
struct QuranWordDisplay: Hashable, Sendable {
    let index: Int
    let text: String
    /// Where the owned text starts in `QuranAyahDisplay.text` (UTF-16).
    let offset: Int
    /// The first letter alone, in its isolated form, for the "first letters" level (`RecallHints.firstLetter`); nil for no letter.
    let firstLetter: String?
    /// Tajweed pieces inside this word, relative to `text` (UTF-16), already widened so a colour never starts or ends inside a letter
    /// with its sign (`tajweedAnnotated` of Kotlin).
    let tajweed: [TajweedRun]
}

/// An ayah ready to draw: the display text (`splitBismillah`: Bismillah of the first ayah cut, rub el hizb hidden), its words and the
/// tajweed pieces. Build it once per ayah and keep it: all layout caches key on `id`.
///
/// ADAPTER(S0): the Kotlin side hands over `splitBismillah(surah, ayah, dbText)` text, `wordRanges(text)` and the aligned
/// `[TajweedSpan]`; call `QuranAyahDisplay(surah:ayah:displayText:ranges:tajweed:)` with them. Until then `make(...)` below runs the
/// same helpers in Swift (ports of `Bismillah.kt` and `AyahWords.kt`).
struct QuranAyahDisplay: Identifiable, Hashable, Sendable {
    let surah: Int
    let ayah: Int
    let text: String
    let words: [QuranWordDisplay]
    let tajweed: [TajweedRun]

    var id: Int { surah * 1000 + ayah }
    var wordCount: Int { words.count }
    var wordTexts: [String] { words.map(\.text) }

    /// From what Kotlin gives: the display text, the word ranges (half-open, UTF-16) and the tajweed spans (half-open, UTF-16).
    init(surah: Int, ayah: Int, displayText: String, ranges: [Range<Int>], tajweed spans: [TajweedRun] = []) {
        self.surah = surah
        self.ayah = ayah
        self.text = displayText
        let units = Array(displayText.utf16)
        let runs = QuranDisplay.widen(spans, in: units)
        self.tajweed = runs
        self.words = ranges.indices.map { word in
            let start = word == 0 ? 0 : ranges[word].lowerBound
            var end = word == ranges.count - 1 ? units.count : ranges[word + 1].lowerBound
            while end > start, QuranDisplay.isSpace(units[end - 1]) { end -= 1 }
            let slice = Array(units[start..<end])
            let own = runs.compactMap { run -> TajweedRun? in
                let lo = max(run.start, start), hi = min(run.end, end)
                return hi > lo ? TajweedRun(rule: run.rule, start: lo - start, end: hi - start) : nil
            }
            return QuranWordDisplay(
                index: word,
                text: String(decoding: slice, as: UTF16.self),
                offset: start,
                firstLetter: RecallHints.firstLetter(of: slice),
                tajweed: own
            )
        }
    }

    /// From the database text of the ayah, with the Swift ports of the display helpers.
    static func make(surah: Int, ayah: Int, databaseText: String, tajweed: [TajweedRun] = []) -> QuranAyahDisplay {
        let shown = QuranDisplay.splitBismillah(surah: surah, ayah: ayah, text: databaseText)
        return QuranAyahDisplay(surah: surah, ayah: ayah, displayText: shown, ranges: QuranDisplay.wordRanges(shown), tajweed: tajweed)
    }
}

// MARK: - Display helpers (ports of Bismillah.kt, AyahWords.kt, TajweedText.kt)

enum QuranDisplay {
    static let bismillahText = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
    static let rubElHizb: UInt16 = 0x06DE

    /// Every surah except Al-Fatiha (its Bismillah is ayah 1) and At-Tawbah (none) shows the Bismillah on its own line.
    static func hasBismillahLine(_ surah: Int) -> Bool { (2...114).contains(surah) && surah != 9 }

    /// What the screens draw for an ayah: Bismillah cut off, rub el hizb hidden. Mirrors Kotlin `splitBismillah`.
    static func splitBismillah(surah: Int, ayah: Int, text: String) -> String {
        hideRubSign(cutBismillah(surah: surah, ayah: ayah, text: text))
    }

    /// Mirrors Kotlin `hideRubSign`: the sign and the spaces after it are removed, the result is trimmed.
    static func hideRubSign(_ text: String) -> String {
        let units = Array(text.utf16)
        guard units.contains(rubElHizb) else { return text }
        var out: [UInt16] = []
        var i = 0
        while i < units.count {
            let c = units[i]
            i += 1
            if c == rubElHizb {
                while i < units.count, isSpace(units[i]) { i += 1 }
            } else {
                out.append(c)
            }
        }
        return String(decoding: trimmed(out), as: UTF16.self)
    }

    /// Mirrors Kotlin `cutBismillah`: the first four words go when they spell the Bismillah.
    static func cutBismillah(surah: Int, ayah: Int, text: String) -> String {
        guard ayah == 1, hasBismillahLine(surah) else { return text }
        let units = Array(text.utf16)
        var cursor = 0, wordEnd = 0
        var first: [UInt16] = []
        for word in 0..<4 {
            while cursor < units.count, isSpace(units[cursor]) { cursor += 1 }
            if cursor >= units.count { return text }
            let start = cursor
            while cursor < units.count, !isSpace(units[cursor]) { cursor += 1 }
            wordEnd = cursor
            if word == 0 { first = Array(units[start..<cursor]) }
        }
        let bare = first.filter { !isMark($0) }
        guard String(decoding: bare, as: UTF16.self) == "بسم" else { return text }
        var rest = Array(units[wordEnd...])
        while let f = rest.first, isSpace(f) { rest.removeFirst() }
        return rest.isEmpty ? text : String(decoding: rest, as: UTF16.self)
    }

    /// Mirrors Kotlin `wordRanges`: tokens split at spaces that hold a letter (a lone pause mark is not a word). Half-open UTF-16.
    static func wordRanges(_ text: String) -> [Range<Int>] {
        let units = Array(text.utf16)
        var ranges: [Range<Int>] = []
        var i = 0
        while i < units.count {
            while i < units.count, isSpace(units[i]) { i += 1 }
            if i >= units.count { break }
            let start = i
            var hasLetter = false
            while i < units.count, !isSpace(units[i]) {
                if isLetter(units[i]) { hasLetter = true }
                i += 1
            }
            if hasLetter { ranges.append(start..<i) }
        }
        return ranges
    }

    static func isSpace(_ c: UInt16) -> Bool {
        c <= 0x20 || c == 0x200B || c == 0xFEFF || c == 0x1680 || (0x2000...0x200A).contains(c) && c != 0x2007
            || c == 0x2028 || c == 0x2029 || c == 0x205F || c == 0x3000
    }

    static func isLetter(_ c: UInt16) -> Bool {
        guard let scalar = Unicode.Scalar(c) else { return false }
        switch scalar.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter: return true
        default: return false
        }
    }

    /// Harakat (064B-065F), dagger alef, tatweel, Quranic annotation signs (06D6-06ED), joiners.
    private static func isMark(_ c: UInt16) -> Bool {
        (0x064B...0x065F).contains(c) || c == 0x0670 || c == 0x0640 || (0x06D6...0x06ED).contains(c) || c == 0x200C || c == 0x200D
    }

    private static func trimmed(_ units: [UInt16]) -> [UInt16] {
        var lo = 0, hi = units.count
        while lo < hi, isSpace(units[lo]) { lo += 1 }
        while hi > lo, isSpace(units[hi - 1]) { hi -= 1 }
        return Array(units[lo..<hi])
    }

    /// What is drawn on or after the letter before it (`isAttached` of `TajweedText.kt`).
    static func isAttached(_ c: UInt16) -> Bool {
        (0x064B...0x065F).contains(c) || (0x0610...0x061A).contains(c)
            || ((0x06D6...0x06ED).contains(c) && c != 0x06DD && c != 0x06DE && c != 0x06E9) || c == 0x0670
    }

    /// A colour must not start or end inside a letter with its sign (`tajweedAnnotated`): the range takes the letter under a sign it
    /// starts with, and the signs that follow its last letter.
    static func widen(_ spans: [TajweedRun], in units: [UInt16]) -> [TajweedRun] {
        spans.compactMap { span in
            var start = min(max(span.start, 0), units.count)
            var end = min(max(span.end, start), units.count)
            while start > 0, start < units.count, isAttached(units[start]) { start -= 1 }            while end < units.count, isAttached(units[end]) { end += 1 }
            return end > start ? TajweedRun(rule: span.rule, start: start, end: end) : nil
        }
    }
}

// MARK: - Hints (port of RecallHints.kt)

/// What a hidden ayah of the "Recall" step leaves on screen, as pure functions. Words are indices in reading order.
enum RecallHints {
    /// "Hide more" goes 25 % -> 50 % -> 75 % -> 100 %.
    static let progressiveSteps = 4
    /// An ayah peeked at this many times is offered "Hard".
    static let peeksSuggestHard = 2

    static func hiddenCount(wordCount: Int, step: Int) -> Int {
        if wordCount <= 0 { return 0 }
        let s = min(max(step, 0), progressiveSteps)
        if s >= progressiveSteps { return wordCount }
        let quarters = (wordCount * s + progressiveSteps - 1) / progressiveSteps
        return max(min(quarters, wordCount - 1), 0)
    }

    static func nextStep(wordCount: Int, step: Int) -> Int {
        let now = hiddenCount(wordCount: wordCount, step: step)
        var next = step + 1
        while next < progressiveSteps {
            if hiddenCount(wordCount: wordCount, step: next) > now { return next }
            next += 1
        }
        return progressiveSteps
    }

    static func canHideMore(wordCount: Int, step: Int) -> Bool { hiddenCount(wordCount: wordCount, step: step) < wordCount }

    static func percent(at step: Int) -> Int { min(max(step, 0), progressiveSteps) * 100 / progressiveSteps }

    /// The words that stay sharp in a hidden ayah of `wordCount` words.
    static func shownWords(_ mode: HifzHintMode, wordCount: Int, step: Int = 0) -> Set<Int> {
        if wordCount <= 0 { return [] }
        switch mode {
        case .none, .firstLetters: return []
        case .firstWord: return wordCount >= 2 ? [0] : []
        case .everyOther: return wordCount >= 2 ? Set(stride(from: 0, to: wordCount, by: 2)) : []
        case .progressive: return Set(0..<(wordCount - hiddenCount(wordCount: wordCount, step: step)))
        }
    }

    static func showsLetters(_ mode: HifzHintMode) -> Bool { mode == .firstLetters }

    /// The first letter of a word alone (alif wasla becomes a plain alif).
    static func firstLetter(of word: [UInt16]) -> String? {
        for c in word {
            guard isBaseLetter(c) else { continue }
            return c == 0x0671 ? "\u{0627}" : String(decoding: [c], as: UTF16.self)
        }
        return nil
    }

    /// One line that shows what `mode` looks like on an ayah, for the sheet of the levels.
    static func preview(_ mode: HifzHintMode, words: [String], limit: Int = 5) -> String {
        let cut = Array(words.prefix(limit))
        if cut.isEmpty { return "" }
        let shown = shownWords(mode, wordCount: cut.count, step: 2)
        let dots = "···"
        let line = cut.indices.map { i -> String in
            if shown.contains(i) { return cut[i] }
            if mode == .firstLetters { return firstLetter(of: Array(cut[i].utf16)) ?? dots }
            return dots
        }.joined(separator: " ")
        return line + (words.count > cut.count ? " \(dots)" : "")
    }

    private static func isBaseLetter(_ c: UInt16) -> Bool {
        QuranDisplay.isLetter(c) && c != 0x0640 && !(0x06D6...0x06ED).contains(c) && c != 0x0670
    }
}

// MARK: - Session (port of HifzSession.kt)

enum HifzPhase: Sendable { case outside, done, current, upcoming }

enum AyahMark: Sendable { case remembered, hard }

/// `ReviewGrade` of the scheduler. ADAPTER(S0): map by name (FORGOT, HARD, GOOD, EASY).
enum ReviewGrade: CaseIterable, Sendable { case forgot, hard, good, easy }

struct HifzItem: Equatable, Sendable {
    let number: Int
    let inSurah: Int
    var mark: AyahMark?
    var peeks = 0
    var peeked: Set<Int> = []
    var hideStep = 0

    init(number: Int, inSurah: Int, mark: AyahMark? = nil, peeks: Int = 0, peeked: Set<Int> = [], hideStep: Int = 0) {
        self.number = number; self.inSurah = inSurah; self.mark = mark; self.peeks = peeks; self.peeked = peeked; self.hideStep = hideStep
    }

    var suggestsHard: Bool { peeks >= RecallHints.peeksSuggestHard }
}

let wholeAyahPeeks = 2

/// A recall session: ordered ayahs of one surah, the position in it and what each ayah was answered. Pure value with pure transitions.
/// ADAPTER(S0): Kotlin owns the real session (`HifzViewModel`); the adapter builds this struct from its state on every change.
struct HifzSession: Equatable, Sendable {
    let surah: Int
    var items: [HifzItem]
    var index = 0
    var revealed = false

    var size: Int { items.count }
    var finished: Bool { index >= items.count }
    var current: HifzItem? { items.indices.contains(index) ? items[index] : nil }
    var markedCount: Int { items.filter { $0.mark != nil }.count }
    var hardAyahs: Set<Int> { Set(items.filter { $0.mark == .hard }.map(\.inSurah)) }
    var suggestedGrade: ReviewGrade { RecallGrading.suggest(total: size, hard: hardAyahs.count) }

    func positionOf(_ inSurah: Int) -> Int? { items.firstIndex { $0.inSurah == inSurah } }

    func phaseOf(_ inSurah: Int) -> HifzPhase {
        guard let position = positionOf(inSurah) else { return .outside }
        if position < index { return .done }
        return position == index ? .current : .upcoming
    }

    func itemOf(_ inSurah: Int) -> HifzItem? { positionOf(inSurah).map { items[$0] } }

    func revealedSession() -> HifzSession {
        var s = self
        if !finished && !revealed { s.revealed = true }
        return s
    }

    func marked(_ mark: AyahMark, requireRevealed: Bool = true) -> HifzSession {
        if finished || (requireRevealed && !revealed) { return self }
        var s = self
        s.items[index].mark = mark
        s.index += 1
        s.revealed = false
        return s
    }

    func peekingWord(position: Int, word: Int) -> HifzSession {
        guard items.indices.contains(position), !finished, position == index, !revealed, word >= 0,
              !items[position].peeked.contains(word) else { return self }
        var s = self
        s.items[position].peeks += 1
        s.items[position].peeked.insert(word)
        return s
    }

    func peekingAyah(position: Int) -> HifzSession {
        guard items.indices.contains(position), !finished, position == index, !revealed else { return self }
        var s = self
        s.items[position].peeks += wholeAyahPeeks
        return s
    }

    func hidingMore(position: Int, wordCount: Int) -> HifzSession {
        guard items.indices.contains(position), !finished, position == index, !revealed else { return self }
        let next = RecallHints.nextStep(wordCount: wordCount, step: items[position].hideStep)
        guard next != items[position].hideStep else { return self }
        var s = self
        s.items[position].hideStep = next
        return s
    }

    /// A session over `ayahs` of one surah (`HifzSessions.range`).
    static func range(surah: Int, ayahs: [QuranAyahDisplay]) -> HifzSession {
        HifzSession(surah: surah, items: ayahs.map { HifzItem(number: $0.id, inSurah: $0.ayah) })
    }
}

enum RecallGrading {
    /// Nothing hard: good; some hard: hard; half or more hard: forgot.
    static func suggest(total: Int, hard: Int) -> ReviewGrade {
        if total <= 0 || hard <= 0 { return .good }
        return hard * 2 >= total ? .forgot : .hard
    }
}

/// The grade of a passage and its difficult ayahs (numbers in the surah).
struct RecallResult: Equatable, Sendable {
    let grade: ReviewGrade
    let hard: Set<Int>
}

/// The state of one row of the mode (`HifzRowState`), without the host: the row calls closures instead.
struct HifzRowState: Equatable {
    var phase: HifzPhase
    var revealed: Bool
    var mark: AyahMark?
    var justMarked = false
    var hintMode: HifzHintMode
    var hideStep = 0
    var peeked: Set<Int> = []
    var animateIn = false
    var position = -1

    /// The row state of the ayah `inSurah` of `session` (`rowStateOf`).
    static func of(_ session: HifzSession, inSurah: Int, hintMode: HifzHintMode) -> HifzRowState {
        let phase = session.phaseOf(inSurah)
        let item = session.itemOf(inSurah)
        let before = session.index - 1
        return HifzRowState(
            phase: phase,
            revealed: phase == .current && session.revealed,
            mark: phase == .done ? item?.mark : nil,
            justMarked: phase == .done && session.items.indices.contains(before) && session.items[before].inSurah == inSurah,
            hintMode: hintMode,
            hideStep: item?.hideStep ?? 0,
            peeked: phase == .current ? (item?.peeked ?? []) : [],
            animateIn: false,
            position: session.positionOf(inSurah) ?? -1
        )
    }
}
