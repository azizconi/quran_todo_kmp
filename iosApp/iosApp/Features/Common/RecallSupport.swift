import SwiftUI
import Observation

/// The calls the recall components make (`RecallHost` of `HifzControls.kt`), same names and meanings. The screen's host is an
/// `@Observable` class: the components read `session`, `listening` and `hintMode` in `body`, so SwiftUI tracks them.
///
/// ADAPTER(S0): the Kotlin `HifzViewModel` (through SKIE) sits behind an `@Observable` class that copies its state flows into
/// `session`, `listening` and `hintMode` and forwards the calls below one to one. `session` is built from the Kotlin `HifzSession`.
@MainActor
protocol RecallActions: AnyObject, Observable {
    var session: HifzSession? { get }
    var copy: RecallCopy { get }
    /// The audio of the ayah being recalled plays (or is being prepared).
    var listening: Bool { get }
    var hintMode: HifzHintMode { get }

    func setHintMode(_ mode: HifzHintMode)
    /// The words of the ayah `inSurah` as the screens draw them; nil for an ayah that is not in the passage.
    func words(inSurah: Int) -> [String]?
    func reveal()
    /// Answers the ayah at session position `position`; ignored when the session has moved on already.
    func mark(_ mark: AyahMark, position: Int)
    /// Answers without the reveal first (the progressive hint offers the answers while the ayah is partly shown).
    func markEarly(_ mark: AyahMark, position: Int)
    func hideMore(position: Int)
    /// A tap on the hidden word `word`: it opens and counts as one peek.
    func peekWord(position: Int, word: Int)
    /// Holding the whole ayah: counts as two peeks.
    func peekAyah(position: Int)
    func toggleListen()
}

/// The strings of the recall (`HifzCopy` of Kotlin). ADAPTER(S0): the five languages come from Kotlin `hifzCopy(language)`; the Swift
/// side keeps English and Russian for the demo and the previews.
struct RecallCopy: Equatable {
    var modeName: String
    var ayah: String
    var hiddenText: String
    var peekHint: String
    var reciteFromMemory: String
    var listen: String
    var listenPause: String
    var reveal: String
    var markTitle: String
    var remembered: String
    var hardMark: String
    var answeredFmt: String
    var forgot: String, hard: String, good: String, easy: String
    var gradeTitle: String
    var gradeSuggested: String
    var gradeChosen: String
    var hardAyahsTitle: String
    var hardAyahsHint: String
    var done: String
    var hintChipFmt: String
    var hintTitle: String
    var hintSubtitle: String
    var hintNames: [HifzHintMode: String]
    var hintInfos: [HifzHintMode: String]
    var hideMore: String
    var hiddenShareFmt: String
    var peekedFmt: String
    var resultFmt: String
    var change: String
    var returnsTomorrow: String
    var returnsInDaysFmt: String
    var recordShort: String
    var lessonGradeHint: String
    var dismiss: String

    func label(_ grade: ReviewGrade) -> String {
        switch grade {
        case .forgot: return forgot
        case .hard: return hard
        case .good: return good
        case .easy: return easy
        }
    }

    func label(_ mark: AyahMark) -> String { mark == .remembered ? remembered : hardMark }
    func answered(_ mark: AyahMark) -> String { answeredFmt.replacingOccurrences(of: "{mark}", with: label(mark)) }
    func hintName(_ mode: HifzHintMode) -> String { hintNames[mode] ?? "" }
    func hintInfo(_ mode: HifzHintMode) -> String { hintInfos[mode] ?? "" }
    func hintChip(_ mode: HifzHintMode) -> String { hintChipFmt.replacingOccurrences(of: "{level}", with: hintName(mode)) }
    func hiddenShare(_ percent: Int) -> String { hiddenShareFmt.replacingOccurrences(of: "{n}", with: "\(percent)") }
    func result(_ grade: ReviewGrade) -> String { resultFmt.replacingOccurrences(of: "{grade}", with: label(grade)) }
    func returns(days: Int) -> String { days <= 1 ? returnsTomorrow : returnsInDaysFmt.replacingOccurrences(of: "{n}", with: "\(days)") }
    func peeked(_ n: Int) -> String { peekedFmt.replacingOccurrences(of: "{n}", with: wordCount(n)) }

    // ADAPTER(S0): Kotlin `wordCountLabel(n, language)`; English/Russian plurals only here.
    var wordCount: (Int) -> String = { n in "\(n) \(n == 1 ? "word" : "words")" }

    static func == (a: RecallCopy, b: RecallCopy) -> Bool { a.modeName == b.modeName && a.reveal == b.reveal }

    static let en = RecallCopy(
        modeName: "Check yourself", ayah: "Ayah", hiddenText: "Ayah text is hidden",
        peekHint: "Tap the ayah to open it; hold it to peek", reciteFromMemory: "Recite from memory",
        listen: "Listen", listenPause: "Pause", reveal: "Reveal", markTitle: "How did this ayah go?",
        remembered: "I remember", hardMark: "Hard", answeredFmt: "Answer: {mark}",
        forgot: "Forgot", hard: "Hard", good: "Good", easy: "Easy",
        gradeTitle: "Grade the passage", gradeSuggested: "Suggested from your answers", gradeChosen: "Your own choice",
        hardAyahsTitle: "Hard ayahs", hardAyahsHint: "Tap an ayah to mark or unmark it", done: "Done",
        hintChipFmt: "Hint: {level}", hintTitle: "Hint", hintSubtitle: "How much of the ayah stays on screen while you recall",
        hintNames: [.none: "None", .firstWord: "First word", .firstLetters: "First letters", .everyOther: "Every other word",
                    .progressive: "Hide gradually"],
        hintInfos: [.none: "The whole ayah is hidden", .firstWord: "The first word stays visible",
                    .firstLetters: "Each word shows only its first letter",
                    .everyOther: "Words 1, 3, 5... stay visible, the rest is hidden",
                    .progressive: "The ayah starts whole and you hide it a quarter at a time"],
        hideMore: "Hide more", hiddenShareFmt: "{n}% hidden", peekedFmt: "Peeked: {n}",
        resultFmt: "Passage: {grade}", change: "Change", returnsTomorrow: "back tomorrow", returnsInDaysFmt: "back in {n} days",
        recordShort: "Record reading", lessonGradeHint: "The answer decides when the passage comes back", dismiss: "Dismiss"
    )

    static let ru: RecallCopy = {
        var c = RecallCopy(
            modeName: "Проверьте себя", ayah: "Аят", hiddenText: "Текст аята скрыт",
            peekHint: "Нажмите на аят, чтобы открыть его; удерживайте, чтобы подсмотреть", reciteFromMemory: "Прочитайте по памяти",
            listen: "Послушать", listenPause: "Пауза", reveal: "Показать", markTitle: "Как получилось с этим аятом?",
            remembered: "Помню", hardMark: "Трудно", answeredFmt: "Ответ: {mark}",
            forgot: "Забыл", hard: "Трудно", good: "Хорошо", easy: "Легко",
            gradeTitle: "Оцените отрывок", gradeSuggested: "Предложено по вашим ответам", gradeChosen: "Вы выбрали сами",
            hardAyahsTitle: "Трудные аяты", hardAyahsHint: "Нажмите на аят, чтобы поставить или снять отметку", done: "Готово",
            hintChipFmt: "Подсказка: {level}", hintTitle: "Подсказка",
            hintSubtitle: "Сколько аята остаётся на экране, пока вы вспоминаете",
            hintNames: [.none: "Нет", .firstWord: "Первое слово", .firstLetters: "Первые буквы", .everyOther: "Каждое второе слово",
                        .progressive: "Постепенно скрывать"],
            hintInfos: [.none: "Аят скрыт целиком", .firstWord: "Видно первое слово",
                        .firstLetters: "На месте каждого слова — его первая буква",
                        .everyOther: "Видны 1-е, 3-е, 5-е слова, остальные скрыты",
                        .progressive: "Аят виден целиком, вы скрываете его по четвертям"],
            hideMore: "Скрыть ещё", hiddenShareFmt: "Скрыто {n} %", peekedFmt: "Подсмотрено: {n}",
            resultFmt: "Отрывок: {grade}", change: "Изменить", returnsTomorrow: "вернётся завтра",
            returnsInDaysFmt: "вернётся через {n} дн.", recordShort: "Записать чтение",
            lessonGradeHint: "Ответ решает, когда отрывок вернётся", dismiss: "Скрыть"
        )
        c.wordCount = { n in
            let m10 = n % 10, m100 = n % 100
            let form = (m10 == 1 && m100 != 11) ? "слово" : ((2...4).contains(m10) && !(12...14).contains(m100)) ? "слова" : "слов"
            return "\(n) \(form)"
        }
        return c
    }()
}
