import ComposeApp
import Observation
import SwiftUI

// MARK: - Copy for the reader screens

/// The `*Copy` objects of the reader family (S5), cached per language. `Copy` (Bridge) keeps its own private cache; this one is S5's, so
/// the shared file does not grow for every screen. Reading `copy.language` first registers the dependency, so views redraw on a change.
@MainActor
enum ReaderCopies {
    private static var cache: [String: Any] = [:]

    fileprivate static func cached<T>(_ copy: Copy, _ key: String, _ make: () -> T) -> T {
        let full = "\(copy.language.name)/\(key)"
        if let hit = cache[full] as? T { return hit }
        let value = make()
        cache[full] = value
        return value
    }
}

extension Copy {
    var reader: ReaderCopy { ReaderCopies.cached(self, "reader") { IosCopy.shared.readerCopy() } }
    var goTo: GoToCopy { ReaderCopies.cached(self, "goTo") { IosCopy.shared.goToCopy() } }
    var ayahActions: AyahActionsCopy { ReaderCopies.cached(self, "ayahActions") { IosCopy.shared.ayahActionsCopy() } }
    var playerRepeat: PlayerRepeatCopy { ReaderCopies.cached(self, "playerRepeat") { IosCopy.shared.playerRepeatCopy() } }
    var tafsir: TafsirCopy { ReaderCopies.cached(self, "tafsir") { IosCopy.shared.tafsirCopy() } }
    var tajweed: TajweedCopy { ReaderCopies.cached(self, "tajweed") { IosCopy.shared.tajweedCopy() } }
    var translation: TranslationCopy { ReaderCopies.cached(self, "translation") { IosCopy.shared.translationCopy() } }
    var similar: SimilarCopy { ReaderCopies.cached(self, "similar") { IosCopy.shared.similarCopy() } }
    var voice: VoiceCopy { ReaderCopies.cached(self, "voice") { IosCopy.shared.voiceCopy() } }
    var pages: PagesCopy { ReaderCopies.cached(self, "pages") { IosCopy.shared.pagesCopy() } }
    var hifz: HifzCopy { ReaderCopies.cached(self, "hifz") { IosCopy.shared.hifzCopy() } }
    var audio: AudioCopy { ReaderCopies.cached(self, "audio") { IosCopy.shared.audioCopy() } }
    var analytics: AnalyticsCopy { ReaderCopies.cached(self, "analytics") { IosCopy.shared.analyticsCopy() } }
    var settingsCopy: SettingsCopy { ReaderCopies.cached(self, "settings") { IosCopy.shared.settingsCopy() } }
    var listenCopy: ListenCopy { ReaderCopies.cached(self, "listenCopy") { IosCopy.shared.listenCopy() } }
}

// MARK: - Reading prefs (font, size, switches of the shared UiPrefs)

/// The reading face and size (Android `LocalReadingFontStyle` / `LocalAppSettings.readingFontSize`) and the reader switches of `UiPrefs`
/// as one `@Observable`. Setters write to the same storage as Android (`ThemeStorage`, `UserSettingsStorage`, `UiPrefs`).
@MainActor
@Observable
final class ReadingPrefs {
    static let shared = ReadingPrefs()

    static let minSize = 22
    static let maxSize = 40

    private(set) var style: ReadingFontStyle
    private(set) var size: Int
    private(set) var tajweed: Bool
    private(set) var wordMeanings: Bool
    private(set) var similarMarks: Bool
    private(set) var highlightWords: Bool
    private(set) var keepScreenOn: Bool

    @ObservationIgnored private var tasks: [Task<Void, Never>] = []

    private init() {
        let prefs = IosUi.shared.uiPrefs
        style = ThemeStorage.shared.getSavedReadingFontStyle() ?? ReadingFontStyle.madinahMushaf
        size = Self.clamp(Int(truncating: UserSettingsStorage.shared.getReadingFontSize() ?? 28))
        tajweed = prefs.tajweed.value.boolValue
        wordMeanings = prefs.wordMeanings.value.boolValue
        similarMarks = prefs.similarMarks.value.boolValue
        highlightWords = prefs.highlightWords.value.boolValue
        keepScreenOn = prefs.keepScreenOn.value.boolValue
        follow(prefs.tajweed) { [weak self] in self?.tajweed = $0.boolValue }
        follow(prefs.wordMeanings) { [weak self] in self?.wordMeanings = $0.boolValue }
        follow(prefs.similarMarks) { [weak self] in self?.similarMarks = $0.boolValue }
        follow(prefs.highlightWords) { [weak self] in self?.highlightWords = $0.boolValue }
        follow(prefs.keepScreenOn) { [weak self] in self?.keepScreenOn = $0.boolValue }
    }

    private func follow<S: AsyncSequence>(_ flow: S, _ apply: @escaping @MainActor (S.Element) -> Void) {
        tasks.append(Task { do { for try await v in flow { apply(v) } } catch {} })
    }

    private static func clamp(_ n: Int) -> Int { min(max(n, minSize), maxSize) }

    /// The face the Swift components draw .
    var face: QhQuranFace { Self.face(of: style) }

    static func face(of style: ReadingFontStyle) -> QhQuranFace {
        switch style {
        case ReadingFontStyle.madinahMushaf: return .madinah
        case ReadingFontStyle.amiriQuran: return .amiri
        case ReadingFontStyle.scheherazadeNew: return .scheherazade
        case ReadingFontStyle.notoNaskhArabic: return .naskh
        case ReadingFontStyle.lateef: return .lateef
        case ReadingFontStyle.notoNastaliqUrdu: return .nastaliq
        case ReadingFontStyle.arefRuqaaInk: return .ruqaa
        default: return .amiri
        }
    }

    /// The saved choice changed behind our back (a backup import): read it again.
    func reload() {
        style = ThemeStorage.shared.getSavedReadingFontStyle() ?? ReadingFontStyle.madinahMushaf
        size = Self.clamp(Int(truncating: UserSettingsStorage.shared.getReadingFontSize() ?? 28))
    }

    var styleStorage: String { style.storageValue }

    func setStyle(storage: String) {
        setStyle(ReadingFontStyle.companion.fromStorage(value: storage))
    }

    func setStyle(_ next: ReadingFontStyle) {
        style = next
        ThemeStorage.shared.saveReadingFontStyle(style: next)
    }

    func setSize(_ next: Int) {
        let value = Self.clamp(next)
        guard value != size else { return }
        size = value
        UserSettingsStorage.shared.saveReadingFontSize(value: Int32(value))
    }

    func setTajweed(_ on: Bool) { IosUi.shared.uiPrefs.setTajweed(value: on) }
    func setWordMeanings(_ on: Bool) { IosUi.shared.uiPrefs.setWordMeanings(value: on) }
    func setSimilarMarks(_ on: Bool) { IosUi.shared.uiPrefs.setSimilarMarks(value: on) }
    func setHighlightWords(_ on: Bool) { IosUi.shared.uiPrefs.setHighlightWords(value: on) }
    func setKeepScreenOn(_ on: Bool) { IosUi.shared.uiPrefs.setKeepScreenOn(value: on) }
}

// MARK: - Small helpers

extension Int32 { var int: Int { Int(self) } }
extension Int { var i32: Int32 { Int32(self) } }

/// `AyahTodoStatus` of Kotlin as the Swift status the rosette draws.
func rdStatus(_ status: AyahTodoStatus?) -> QhStatus? {
    switch status {
    case .some(.learned): return .learned
    case .some(.learning): return .learning
    default: return nil
    }
}

func rdStatus(_ status: SurahTodoStatus?) -> QhStatus {
    switch status {
    case .some(.learned): return .learned
    case .some(.learning): return .learning
    default: return .notStarted
    }
}

/// "0:12" style time (`formatAudioTime`).
func audioTimeText(_ seconds: Int) -> String {
    let s = max(seconds, 0)
    return "\(s / 60):\(String(format: "%02d", s % 60))"
}

func speedText(_ speed: Float) -> String {
    let text = speed == speed.rounded() ? String(Int(speed)) : String(speed)
    return "\(text)\u{00D7}"
}

func repeatText(_ count: Int) -> String { count == -1 ? "\u{221E}" : "\(count)\u{00D7}" }

/// Keeps the screen awake while at least one reader (or the player) asks for it: a reference count over `isIdleTimerDisabled`.
@MainActor
enum IdleLock {
    private static var holders = Set<ObjectIdentifier>()
    static func set(_ on: Bool, owner: AnyObject) {
        let id = ObjectIdentifier(owner)
        if on { holders.insert(id) } else { holders.remove(id) }
        UIApplication.shared.isIdleTimerDisabled = !holders.isEmpty
    }
}
