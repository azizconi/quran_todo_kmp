import ComposeApp
import Observation
import SwiftUI

// ADAPTER(S0) of the recall core (Features/Common): the Swift value types of the recall components are built from the Kotlin
// `HifzSession` / `HifzCopy`, and a `RecallHostBridge` forwards the calls of the components to the Kotlin view model of the screen
// (the lesson, the drill). The session, the listening state and the hint level are copied into the bridge on every Kotlin change.

// MARK: - Types

extension HifzHintMode {
    init(_ kotlin: ComposeApp.HifzHintMode) {
        switch kotlin {
        case .firstWord: self = .firstWord
        case .firstLetters: self = .firstLetters
        case .everyOther: self = .everyOther
        case .progressive: self = .progressive
        default: self = .none
        }
    }

    var kotlin: ComposeApp.HifzHintMode {
        switch self {
        case .none: return .none
        case .firstWord: return .firstWord
        case .firstLetters: return .firstLetters
        case .everyOther: return .everyOther
        case .progressive: return .progressive
        }
    }
}

extension AyahMark {
    init(_ kotlin: ComposeApp.AyahMark) { self = kotlin == .hard ? .hard : .remembered }
    var kotlin: ComposeApp.AyahMark { self == .hard ? .hard : .remembered }
}

extension ReviewGrade {
    init(_ kotlin: ComposeApp.ReviewGrade) {
        switch kotlin {
        case .forgot: self = .forgot
        case .hard: self = .hard
        case .easy: self = .easy
        default: self = .good
        }
    }

    var kotlin: ComposeApp.ReviewGrade {
        switch self {
        case .forgot: return .forgot
        case .hard: return .hard
        case .good: return .good
        case .easy: return .easy
        }
    }
}

extension HifzSession {
    /// The pure Swift session from the Kotlin one (`LessonViewModel` keeps the real one inside the card progress).
    init(_ kotlin: ComposeApp.HifzSession) {
        self.init(
            surah: Int(kotlin.surah),
            items: kotlin.items.map { item in
                HifzItem(number: Int(item.number), inSurah: Int(item.inSurah), mark: item.mark.map { AyahMark($0) },
                         peeks: Int(item.peeks), peeked: Set(item.peeked.map { Int(truncating: $0) }), hideStep: Int(item.hideStep))
            },
            index: Int(kotlin.index),
            revealed: kotlin.revealed
        )
    }
}

extension RecallCopy {
    /// The strings of the recall in the app language from the Kotlin `HifzCopy` (+ `HintsCopy`), all five languages.
    init(_ k: HifzCopy, hints: HintsCopy) {
        var copy = RecallCopy.en
        copy.modeName = k.modeName
        copy.ayah = k.ayah
        copy.hiddenText = k.hiddenText
        copy.peekHint = k.peekHint
        copy.reciteFromMemory = k.reciteFromMemory
        copy.listen = k.listen
        copy.listenPause = k.listenPause
        copy.reveal = k.reveal
        copy.markTitle = k.markTitle
        copy.remembered = k.remembered
        copy.hardMark = k.hardMark
        copy.answeredFmt = k.answeredFmt
        copy.forgot = k.forgot
        copy.hard = k.hard
        copy.good = k.good
        copy.easy = k.easy
        copy.gradeTitle = k.gradeTitle
        copy.gradeSuggested = k.gradeSuggested
        copy.gradeChosen = k.gradeChosen
        copy.hardAyahsTitle = k.hardAyahsTitle
        copy.hardAyahsHint = k.hardAyahsHint
        copy.done = k.done
        copy.hintChipFmt = k.hintChipFmt
        copy.hintTitle = k.hintTitle
        copy.hintSubtitle = k.hintSubtitle
        copy.hintNames = [.none: k.hintNone, .firstWord: k.hintFirstWord, .firstLetters: k.hintFirstLetters,
                          .everyOther: k.hintEveryOther, .progressive: k.hintProgressive]
        copy.hintInfos = [.none: k.hintNoneInfo, .firstWord: k.hintFirstWordInfo, .firstLetters: k.hintFirstLettersInfo,
                          .everyOther: k.hintEveryOtherInfo, .progressive: k.hintProgressiveInfo]
        copy.hideMore = k.hideMore
        copy.hiddenShareFmt = k.hiddenShareFmt
        copy.peekedFmt = k.peekedFmt
        copy.resultFmt = k.resultFmt
        copy.change = k.change
        copy.returnsTomorrow = k.returnsTomorrow
        copy.returnsInDaysFmt = k.returnsInDaysFmt
        copy.lessonGradeHint = hints.lessonGrade
        copy.dismiss = hints.dismiss
        let language = k.language
        copy.wordCount = { n in HifzCopyKt.wordCountLabel(n: Int32(n), language: language) }
        self = copy
    }
}

// MARK: - The host

/// A `RecallActions` host whose calls go to closures: the owner (the lesson, the drill) copies the Kotlin session / audio / hint
/// state into the properties and turns the calls of the components into view model calls.
@MainActor
@Observable
final class RecallHostBridge: RecallActions {
    var session: HifzSession?
    var copy: RecallCopy
    var listening = false
    var hintMode: HifzHintMode = .none

    @ObservationIgnored var onSetHint: (HifzHintMode) -> Void = { _ in }
    @ObservationIgnored var wordsOf: (Int) -> [String]? = { _ in nil }
    @ObservationIgnored var onReveal: () -> Void = {}
    @ObservationIgnored var onMark: (AyahMark, Int) -> Void = { _, _ in }
    @ObservationIgnored var onMarkEarly: (AyahMark, Int) -> Void = { _, _ in }
    @ObservationIgnored var onHideMore: (Int) -> Void = { _ in }
    @ObservationIgnored var onPeekWord: (Int, Int) -> Void = { _, _ in }
    @ObservationIgnored var onPeekAyah: (Int) -> Void = { _ in }
    @ObservationIgnored var onToggleListen: () -> Void = {}

    init(copy: RecallCopy) { self.copy = copy }

    func setHintMode(_ mode: HifzHintMode) { onSetHint(mode) }
    func words(inSurah: Int) -> [String]? { wordsOf(inSurah) }
    func reveal() { onReveal() }
    func mark(_ mark: AyahMark, position: Int) { onMark(mark, position) }
    func markEarly(_ mark: AyahMark, position: Int) { onMarkEarly(mark, position) }
    func hideMore(position: Int) { onHideMore(position) }
    func peekWord(position: Int, word: Int) { onPeekWord(position, word) }
    func peekAyah(position: Int) { onPeekAyah(position) }
    func toggleListen() { onToggleListen() }

    /// Updates the session with the gentle motion of the recall (rows fold, the controls come and go).
    func apply(session next: HifzSession?) {
        guard session != next else { return }
        withQhAnimation(QhMotion.gentle) { session = next }
    }
}
