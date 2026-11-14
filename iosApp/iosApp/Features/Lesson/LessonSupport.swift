import ComposeApp
import SwiftUI

// Shared helpers of the S4 / S6 screens: copy accessors that are not in `Bridge/Copy.swift`, the reading face of the Quran text,
// and the conversion of the Kotlin ayah entity to the display model of `QuranText`.

/// Copies cached per language for the accessors below (`Copy` keeps its own cache private).
@MainActor
private enum ExtraCopyCache {
    static var store: [String: Any] = [:]
    static func get<T>(_ copy: Copy, _ key: String, _ make: () -> T) -> T {
        let full = "\(copy.language.name)/\(key)"
        if let hit = store[full] as? T { return hit }
        let value = make()
        store[full] = value
        return value
    }
}

extension Copy {
    var lessonCopy: LessonCopy { ExtraCopyCache.get(self, "lesson") { IosCopy.shared.lessonCopy() } }
    var lessonListenCopy: LessonListenCopy { ExtraCopyCache.get(self, "lessonListen") { IosCopy.shared.lessonListenCopy() } }
    var hifzKotlinCopy: HifzCopy { ExtraCopyCache.get(self, "hifz") { IosCopy.shared.hifzCopy() } }
    var summaryExtrasCopy: SummaryExtrasCopy { ExtraCopyCache.get(self, "summaryExtras") { IosCopy.shared.summaryExtrasCopy() } }
    var drillCopy: DrillCopy { ExtraCopyCache.get(self, "drill") { IosCopy.shared.drillCopy() } }
    var readerCopy: ReaderCopy { ExtraCopyCache.get(self, "reader") { IosCopy.shared.readerCopy() } }
    var reminderCopy: ReminderCopy { ExtraCopyCache.get(self, "reminder") { IosCopy.shared.reminderCopy() } }
    var analyticsCopy: AnalyticsCopy { ExtraCopyCache.get(self, "analytics") { IosCopy.shared.analyticsCopy() } }
    var reportCopy: ReportCopy { ExtraCopyCache.get(self, "report") { IosCopy.shared.reportCopy() } }
    var voiceCopy: VoiceCopy { ExtraCopyCache.get(self, "voice") { IosCopy.shared.voiceCopy() } }
    var hintsCopy: HintsCopy { ExtraCopyCache.get(self, "hints") { IosCopy.shared.hintsCopy() } }

    /// "14 March" in the language of the app.
    func day(_ epochDay: Int) -> String { FormatDayKt.formatDay(epochDay: Int32(epochDay), language: language) }
}

/// The face and size the Arabic text is read in: the person's choice of the reader (`ThemeStorage`, `UserSettingsStorage`).
enum LessonArabic {
    static var face: QhQuranFace {
        switch ThemeStorage.shared.getSavedReadingFontStyle() {
        case .madinahMushaf: return .madinah
        case .scheherazadeNew: return .scheherazade
        case .notoNaskhArabic: return .naskh
        case .lateef: return .lateef
        case .notoNastaliqUrdu: return .nastaliq
        default: return .amiri
        }
    }

    /// The reading size of the reader (default 28), clamped to the range of the settings.
    static var size: CGFloat {
        let saved = UserSettingsStorage.shared.getReadingFontSize()?.intValue ?? 28
        return CGFloat(min(max(saved, 22), 40))
    }

    /// Passages of the lesson are drawn a little larger than in the reader (`rememberLessonArabicStyle`: +2, at most 40).
    static var lessonSize: CGFloat { min(size + 2, 40) }
}

extension AyahEntity {
    /// The display model of the ayah (Bismillah cut, rub el hizb hidden, words) through the Swift ports of the Kotlin helpers.
    var display: QuranAyahDisplay {
        QuranAyahDisplay.make(surah: Int(surahNumber), ayah: Int(numberInSurah), databaseText: text)
    }
}
