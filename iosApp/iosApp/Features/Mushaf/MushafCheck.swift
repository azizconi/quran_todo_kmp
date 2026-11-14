import ComposeApp
import SwiftUI

/// "Check yourself" on a page: the panel (Show, then Hard / I remember), the leave question, the grade, and the ayah sheet of the pages.
struct CheckPanel: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: MushafModel
    let check: PageCheck

    var body: some View {
        let h = copy.hifz
        VStack(spacing: QhSpacing.x3) {
            HStack {
                Button { QhHaptics.shared.perform(.tick); model.vm.requestExit() } label: {
                    Image(systemName: "xmark").font(.system(size: 15, weight: .semibold)).foregroundStyle(qh.colors.inkSecondary).frame(width: 40, height: 40)
                }
                .accessibilityLabel(h.exit)
                Text(h.modeName).qhText({ $0.title2 })
                Spacer()
                Text("\(min(Int(check.markedCount) + 1, Int(check.size))) / \(check.size)").qhText({ $0.title2 }, color: { $0.primary }).monospacedDigit()
            }
            if check.revealed {
                HStack(spacing: QhSpacing.x3) {
                    QhButton(h.hardMark, variant: .tonal, size: .m, fullWidth: true) {
                        QhHaptics.shared.perform(.reject)
                        model.vm.mark(mark: ComposeApp.AyahMark.hard)
                    }
                    QhButton(h.remembered, size: .m, fullWidth: true) {
                        QhHaptics.shared.perform(.success)
                        model.vm.mark(mark: ComposeApp.AyahMark.remembered)
                    }
                }
            } else {
                QhButton(h.reveal, size: .m, fullWidth: true) { model.vm.reveal() }
            }
        }
        .padding(QhSpacing.x3)
        .background(qh.colors.surface, in: qh.shapes.lgShape)
        .overlay { qh.shapes.lgShape.stroke(qh.colors.hairline, lineWidth: 1) }
        .qhSoftShadow()
        .padding(.horizontal, QhSpacing.x3)
        .animation(QhMotion.reduced ? nil : QhMotion.snappy, value: check.revealed)
    }
}

struct MushafLeaveSheet: View {
    @Environment(Copy.self) private var copy
    let model: MushafModel

    var body: some View {
        let h = copy.hifz
        let check = model.check.value
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            Text(h.exitTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
            Text(h.exitBody(done: check?.markedCount ?? 0, total: check?.size ?? 0)).qhText({ $0.body }, color: { $0.inkSecondary })
            QhButton(h.keepGoing, fullWidth: true) { model.vm.dismissExitPrompt() }.padding(.top, QhSpacing.x2)
            QhButton(h.leave, variant: .tonal, size: .m, fullWidth: true) { model.vm.exitCheck() }
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .qhSheetStyle([.height(280)])
    }
}

/// The grade of the page: every surah part of the checked ayahs is written on its own (`CheckRouting.choose`).
struct MushafGradeSheet: View {
    @Environment(Copy.self) private var copy
    @Environment(\.dismiss) private var dismiss
    let model: MushafModel
    let check: PageCheck
    let title: String

    var body: some View {
        let items = check.items.enumerated().map {
            HifzItem(number: $0.offset + 1, inSurah: Int($0.element.key.ayah), mark: $0.element.mark.map { ReaderRecall.swiftMark($0) })
        }
        var session = HifzSession(surah: Int(check.items.first?.key.surah ?? 1), items: items)
        session.index = items.count
        return RecallGradeSheet(session: session, title: title, copy: ReaderRecall.makeCopy(copy.hifz, language: copy.language),
                                busy: model.saving.value.boolValue, initial: nil) { grade, hard in
            let keys = Set(check.items.filter { hard.contains(Int($0.key.ayah)) }.map { $0.key })
            model.vm.finishCheck(grade: ReaderRecall.kotlinGrade(grade), hard: keys)
            dismiss()
        }
    }
}

/// The ayah sheet of the pages: the same sheet as the text reader, fed by the mushaf view model.
struct MushafAyahSheet: View {
    @Environment(Copy.self) private var copy
    let model: MushafModel
    let ref: AyahRef
    let onCheck: () -> Void

    var body: some View {
        if let sheet = model.sheet.value {
            let r = copy.reader
            let display = QuranAyahDisplay(surah: ref.surah, ayah: ref.ayah, displayText: sheet.arabic,
                                           ranges: AyahWordsKt.wordRanges(text: sheet.arabic).map { Int($0.first)..<(Int($0.last) + 1) })
            let share = ReaderPositionKt.ayahShareText(
                arabic: BismillahKt.cutBismillah(surahNumber: Int32(ref.surah), ayahNumberInSurah: Int32(ref.ayah), text: sheet.ayah.text),
                translation: nil, quranLabel: r.quranRef, surahNumber: Int32(ref.surah), ayahNumberInSurah: Int32(ref.ayah), surahName: sheet.surahName)
            AyahSheetContent(data: AyahSheetData(
                surah: ref.surah, number: ref.ayah, title: sheet.surahName, status: sheet.status, weak: sheet.hard,
                stateLine: stateLine(sheet, r), display: display, note: sheet.note, shareText: share,
                onListen: { model.vm.listenFrom(key: ref.kotlin) }, onRepeat: { model.vm.repeatAyah(key: ref.kotlin) }, onCheck: onCheck,
                onKnown: { model.vm.markAyahKnown(key: ref.kotlin) }, onForget: { model.vm.forgetAyah(key: ref.kotlin) },
                onSaveNote: { model.vm.saveNote(key: ref.kotlin, note: $0) }))
        }
    }

    private func stateLine(_ sheet: AyahSheetModel, _ r: ReaderCopy) -> (String, Bool)? {
        var parts: [(String, Bool)] = []
        if sheet.hard { parts.append((r.weakAyah, true)) }
        if let schedule = sheet.schedule {
            switch onEnum(of: schedule) {
            case .circle: parts.append((r.inOldCircle, false))
            case .due: parts.append((r.reviewDue, true))
            case .inDays(let d): parts.append((r.reviewIn(days: d.days), false))
            }
        }
        return parts.isEmpty ? nil : (parts.map(\.0).joined(separator: " \u{00B7} "), parts.contains { $0.1 })
    }
}
