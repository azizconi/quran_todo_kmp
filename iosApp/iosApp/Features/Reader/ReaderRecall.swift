import ComposeApp
import Observation
import SwiftUI

/// Memorize mode of the reader ("Check yourself", `HifzMode` of Android): the adapter between the Kotlin `HifzViewModel` and the Swift
/// recall components of `Features/Common`. It is the `RecallActions` host of the rows: it copies the session, the hint level, the
/// listening state and the leave / saving flags of the Kotlin view model into observable values and forwards every call. The session is
/// written only at the end (`finish`), through the shared `CheckRouting` / `HifzRepository`.
@MainActor
@Observable
final class ReaderRecall: RecallActions {
    @ObservationIgnored private let holder: VMHolder<HifzViewModel>
    @ObservationIgnored private var tasks: [Task<Void, Never>] = []
    @ObservationIgnored private var savedEvents: FlowEvents<CheckSaved>?
    let model: ReaderModel
    let copy: RecallCopy

    private(set) var session: HifzSession?
    private(set) var listening = false
    private(set) var hintMode: HifzHintMode
    private(set) var exitPrompt = false
    private(set) var saving = false
    /// The session ran and is over (left or written): the reader drops this object.
    private(set) var ended = false
    /// When the mode started, so rows composed right after melt into their veil.
    private(set) var startedAt = Date.distantPast
    @ObservationIgnored private var seen = false

    var vm: HifzViewModel { holder.vm }
    var active: Bool { session != nil }

    init(model: ReaderModel, toaster: Toaster, copy appCopy: Copy) {
        self.model = model
        let hifz = appCopy.hifz
        copy = ReaderRecall.makeCopy(hifz, language: appCopy.language)
        let route = model.route
        holder = VMHolder { IosUi.shared.hifz(route: route) }
        hintMode = ReaderRecall.swiftHint(IosUi.shared.uiPrefs.hifzHintMode.value)
        let vm = holder.vm
        if let surah = model.surah { vm.enter(surah: surah, saved: "") }
        session = ReaderRecall.convert(vm.session.value)
        tasks.append(Task { [weak self] in
            do { for try await s in vm.session { self?.apply(s) } } catch {}
        })
        tasks.append(Task { [weak self] in do { for try await v in vm.listening { self?.listening = v.boolValue } } catch {} })
        tasks.append(Task { [weak self] in do { for try await v in vm.hintMode { self?.hintMode = ReaderRecall.swiftHint(v) } } catch {} })
        tasks.append(Task { [weak self] in do { for try await v in vm.exitPrompt { self?.exitPrompt = v.boolValue } } catch {} })
        tasks.append(Task { [weak self] in do { for try await v in vm.saving { self?.saving = v.boolValue } } catch {} })
        savedEvents = FlowEvents(vm.saved) { saved in
            toaster.show(saved == CheckSaved.review ? hifz.savedReview : hifz.savedCheck, kind: .success)
        }
    }

    private func apply(_ s: ComposeApp.HifzSession?) {
        let next = ReaderRecall.convert(s)
        if next != nil, session == nil { startedAt = Date(); seen = true }
        if next == nil, seen { ended = true }
        session = next
    }

    func start(at index: Int, rangeEnd: Int?) {
        guard let surah = model.surah else { return }
        vm.startFromAyah(surah: surah, startIndex: Int32(index), rangeEndIndex: rangeEnd.map { KotlinInt(int: Int32($0)) })
    }

    // MARK: RecallActions

    func setHintMode(_ mode: HifzHintMode) { vm.setHintMode(mode: ReaderRecall.kotlinHint(mode)) }

    func words(inSurah: Int) -> [String]? {
        model.displays.indices.contains(inSurah - 1) ? model.displays[inSurah - 1].wordTexts : nil
    }

    func reveal() { vm.reveal() }
    func mark(_ mark: AyahMark, position: Int) { vm.mark(mark: ReaderRecall.kotlinMark(mark), position: Int32(position)) }
    func markEarly(_ mark: AyahMark, position: Int) { vm.markEarly(mark: ReaderRecall.kotlinMark(mark), position: Int32(position)) }
    func hideMore(position: Int) {
        guard let item = session?.items[safe: position], let count = words(inSurah: item.inSurah)?.count else { return }
        vm.hideMore(position: Int32(position), wordCount: Int32(count))
    }
    func peekWord(position: Int, word: Int) { vm.peekWord(position: Int32(position), word: Int32(word)) }
    func peekAyah(position: Int) { vm.peekAyah(position: Int32(position)) }
    func toggleListen() { if let surah = model.surah { vm.toggleListen(surah: surah) } }
    func listenPassage() { if let surah = model.surah { vm.listenPassage(surah: surah) } }

    func requestExit() { vm.requestExit() }
    func dismissExitPrompt() { vm.dismissExitPrompt() }
    func exit() { vm.exit() }
    func finish(_ grade: ReviewGrade, hard: Set<Int>) {
        vm.finish(grade: ReaderRecall.kotlinGrade(grade), hard: Set(hard.map { KotlinInt(int: Int32($0)) }))
    }

    var currentIndex: Int? {
        guard let s = session, !s.finished, let c = s.current else { return nil }
        return c.inSurah - 1
    }

    // MARK: Mapping

    private static func convert(_ s: ComposeApp.HifzSession?) -> HifzSession? {
        guard let s else { return nil }
        let items = s.items.map { item in
            HifzItem(number: Int(item.number), inSurah: Int(item.inSurah), mark: item.mark.map(swiftMark), peeks: Int(item.peeks),
                     peeked: Set(item.peeked.map { Int(truncating: $0) }), hideStep: Int(item.hideStep))
        }
        var out = HifzSession(surah: Int(s.surah), items: items)
        out.index = Int(s.index)
        out.revealed = s.revealed
        return out
    }

    static func swiftMark(_ m: ComposeApp.AyahMark) -> AyahMark { m.name == "HARD" ? .hard : .remembered }
    static func kotlinMark(_ m: AyahMark) -> ComposeApp.AyahMark { ComposeApp.AyahMark.allCases.first { swiftMark($0) == m }! }
    static func swiftGrade(_ g: ComposeApp.ReviewGrade) -> ReviewGrade {
        switch g.name { case "FORGOT": return .forgot; case "HARD": return .hard; case "EASY": return .easy; default: return .good }
    }
    static func kotlinGrade(_ g: ReviewGrade) -> ComposeApp.ReviewGrade { ComposeApp.ReviewGrade.allCases.first { swiftGrade($0) == g }! }
    static func swiftHint(_ h: ComposeApp.HifzHintMode) -> HifzHintMode {
        switch h.name {
        case "FIRST_WORD": return .firstWord
        case "FIRST_LETTERS": return .firstLetters
        case "EVERY_OTHER": return .everyOther
        case "PROGRESSIVE": return .progressive
        default: return .none
        }
    }
    static func kotlinHint(_ h: HifzHintMode) -> ComposeApp.HifzHintMode { ComposeApp.HifzHintMode.allCases.first { swiftHint($0) == h }! }

    /// The strings of the recall from the Kotlin `HifzCopy` (the five languages).
    static func makeCopy(_ k: HifzCopy, language: AppLanguage) -> RecallCopy {
        var hintNames: [HifzHintMode: String] = [:], hintInfos: [HifzHintMode: String] = [:]
        for mode in HifzHintMode.allCases {
            let kotlin = kotlinHint(mode)
            hintNames[mode] = k.hintName(mode: kotlin)
            hintInfos[mode] = k.hintDescription(mode: kotlin)
        }
        var c = RecallCopy(
            modeName: k.modeName, ayah: k.ayah, hiddenText: k.hiddenText, peekHint: k.peekHint, reciteFromMemory: k.reciteFromMemory,
            listen: k.listen, listenPause: k.listenPause, reveal: k.reveal, markTitle: k.markTitle, remembered: k.remembered,
            hardMark: k.hardMark, answeredFmt: k.answeredFmt, forgot: k.forgot, hard: k.hard, good: k.good, easy: k.easy,
            gradeTitle: k.gradeTitle, gradeSuggested: k.gradeSuggested, gradeChosen: k.gradeChosen, hardAyahsTitle: k.hardAyahsTitle,
            hardAyahsHint: k.hardAyahsHint, done: k.done, hintChipFmt: k.hintChipFmt, hintTitle: k.hintTitle, hintSubtitle: k.hintSubtitle,
            hintNames: hintNames, hintInfos: hintInfos, hideMore: k.hideMore, hiddenShareFmt: k.hiddenShareFmt, peekedFmt: k.peekedFmt,
            resultFmt: k.resultFmt, change: k.change, returnsTomorrow: k.returnsTomorrow, returnsInDaysFmt: k.returnsInDaysFmt,
            recordShort: k.recordShort, lessonGradeHint: "", dismiss: k.keepGoing
        )
        c.wordCount = { n in HifzCopyKt.wordCountLabel(n: Int32(n), language: language) }
        return c
    }
}

// MARK: - A row of the reader in the mode

/// One ayah of the reader while the mode runs: the rosette with the answer mark, the text (hidden or not by phase and hint level) and,
/// under the ayah being recalled, the controls. An ayah outside the session is dimmed.
struct RecallRowView: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let recall: ReaderRecall
    let ayah: QuranAyahDisplay
    let status: QhStatus?
    let weak: Bool
    let translation: String?
    let face: QhQuranFace
    let size: CGFloat
    let tajweed: Bool

    private func rowState(_ session: HifzSession) -> HifzRowState {
        var state = HifzRowState.of(session, inSurah: ayah.ayah, hintMode: recall.hintMode)
        state.animateIn = Date().timeIntervalSince(recall.startedAt) < 0.7
        return state
    }

    var body: some View {
        if let session = recall.session {
            let state = rowState(session)
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    HifzRosette(number: ayah.ayah, status: status, weak: weak, state: state, copy: recall.copy)
                        .frame(height: QhSpacing.minTouch)
                    HifzAyahContent(state: state, ayah: ayah, face: face, readingSize: size, showTajweed: tajweed, translation: translation,
                                    hiddenLabel: recall.copy.hiddenText,
                                    onReveal: { recall.reveal() }, onPeekAyah: { recall.peekAyah(position: state.position) })
                    RecallControlsHost(host: recall, inSurah: ayah.ayah)
                }
                .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x1).padding(.bottom, QhSpacing.x4)
                Rectangle().fill(qh.colors.hairline).frame(height: 1).padding(.horizontal, QhSpacing.screen)
            }
            .hifzRow(state)
        }
    }
}

/// "Do you want to leave?" with "Keep going" and "Leave".
struct RecallLeaveSheet: View {
    @Environment(Copy.self) private var copy
    let recall: ReaderRecall
    var body: some View {
        let hifz = copy.hifz
        let session = recall.session
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            Text(hifz.exitTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
            Text(hifz.exitBody(done: Int32(session?.markedCount ?? 0), total: Int32(session?.size ?? 0))).qhText({ $0.body }, color: { $0.inkSecondary })
            QhButton(hifz.keepGoing, fullWidth: true) { recall.dismissExitPrompt() }.padding(.top, QhSpacing.x2)
            QhButton(hifz.leave, variant: .tonal, size: .m, fullWidth: true) { recall.exit() }
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .qhSheetStyle([.height(280)])
    }
}
