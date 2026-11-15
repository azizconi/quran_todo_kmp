import ComposeApp
import SwiftUI

/// "Recall" of a card, new or repeated (`LessonRecall.kt`): the hidden passage, ayah by ayah, with its controls in a bar pinned to the
/// bottom. There is one grading: after the last ayah the grade of the passage is the one the answers give and the bar shows it with
/// "Change" (the grade sheet, for a manual choice) and "Next", which writes it. Nothing opens by itself.
struct RecallStage: View {
    let model: LessonModel
    let work: LessonUiWorking
    @Environment(Copy.self) private var copy
    @State private var host = RecallHostBridge(copy: .en)
    @State private var gradeOpen = false
    @State private var override: RecallResult?

    var body: some View {
        let c = copy.lessonCopy
        let key = work.card.key
        let kSession = work.progress.recall
        let session = kSession.map { HifzSession($0) }
        let hint = HifzHintMode(model.hintMode.value)
        let listening = model.listeningCurrent.value.boolValue
        let saving = model.isSaving.value.boolValue
        let result: RecallResult? = session.flatMap { s in
            s.finished ? (override ?? RecallResult(grade: s.suggestedGrade, hard: s.hardAyahs)) : nil
        }
        let review = work.card as? LessonCardReview
        let stage = review?.item.stage
        let returnDays: Int? = result.flatMap { ComposeApp.RecallGrading.shared.returnDays(stage: stage, grade: $0.grade.kotlin) }.map { Int(truncating: $0) }
        let range = work.card.range
        let title = "\(model.surahTitle(Int(range.surah), language: copy.language)) · \(c.range(start: range.startAyah, end: range.endAyah))"
        let hard = Set((review?.item.hardAyahs ?? []).map { Int(truncating: $0) })
        let displays = model.displays(for: work)
        Group {
            if session != nil {
                RecallPassage(host: host, ayahs: displays, face: LessonArabic.face, readingSize: LessonArabic.lessonSize,
                              hard: hard, compact: true) { Color.clear.frame(height: QhSpacing.x2) }
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        RecallBottomBar(
                            host: host, result: result, returnDays: returnDays,
                            onChange: { gradeOpen = true },
                            nextLabel: c.next,
                            onNext: { if let result { model.vm.submit(card: work.card, grade: result.grade.kotlin, hard: Set(result.hard.map { KotlinInt(int: Int32($0)) })) } },
                            saving: saving
                        )
                    }
            } else {
                PassageLoading()
            }
        }
        .onAppear { configure(host, key: key) }
        .onChange(of: copy.language, initial: true) { _, _ in host.copy = RecallCopy(copy.hifzKotlinCopy, hints: copy.hintsCopy) }
        .onChange(of: session, initial: true) { _, next in host.apply(session: next) }
        .onChange(of: hint, initial: true) { _, next in host.hintMode = next }
        .onChange(of: listening, initial: true) { _, next in host.listening = next }
        .recallGradeSheet(isPresented: Binding(get: { gradeOpen && (session?.finished ?? false) }, set: { gradeOpen = $0 }),
                          session: session ?? HifzSession(surah: Int(range.surah), items: []), title: title,
                          copy: host.copy, busy: saving, initial: result) { grade, hardAyahs in
            override = RecallResult(grade: grade, hard: hardAyahs)
        }
    }

    @MainActor
    private func configure(_ host: RecallHostBridge, key: String) {
        let model = model
        host.onSetHint = { model.vm.setHintMode(mode: $0.kotlin) }
        host.wordsOf = { inSurah in
            model.currentWork?.ayahs.first { Int($0.numberInSurah) == inSurah }?.display.wordTexts
        }
        host.onReveal = { model.vm.reveal(key: key) }
        host.onMark = { mark, position in model.vm.mark(key: key, mark: mark.kotlin, position: Int32(position)) }
        host.onMarkEarly = { mark, position in model.vm.markEarly(key: key, mark: mark.kotlin, position: Int32(position)) }
        host.onHideMore = { position in
            guard let work = model.currentWork, let item = work.progress.recall?.items[safe: position],
                  let count = work.ayahs.first(where: { Int($0.numberInSurah) == Int(item.inSurah) })?.display.wordCount else { return }
            model.vm.hideMore(key: key, position: Int32(position), wordCount: Int32(count))
        }
        host.onPeekWord = { position, word in model.vm.peekWord(key: key, position: Int32(position), word: Int32(word)) }
        host.onPeekAyah = { position in model.vm.peekAyah(key: key, position: Int32(position)) }
        host.onToggleListen = {
            guard let work = model.currentWork, let current = work.progress.recall?.current else { return }
            model.vm.toggleListenAyah(work: work, inSurah: current.inSurah)
        }
    }
}

extension LessonModel {
    /// The working card now (the closures of a host outlive the render that made them).
    var currentWork: LessonUiWorking? {
        if case let .working(work) = onEnum(of: ui.value) { return work }
        return nil
    }
}
