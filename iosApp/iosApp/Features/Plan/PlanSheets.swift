import ComposeApp
import SwiftUI

// MARK: - Add a surah

/// "Add a surah": a search field (by name or number, the same index as the Quran tab) over the list of all surahs. A tap adds the surah
/// to the end of the queue and keeps the sheet open, so several can be added; the ones in the queue show a check.
struct PlanAddSurahSheet: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let catalog: [SurahRowUi]
    let inQueue: Set<Int>
    let onAdd: (Int) -> Void
    let onClose: () -> Void
    @State private var query = ""
    @State private var index: SurahSearch.Index?

    var body: some View {
        let c = copy.plan
        VStack(spacing: 0) {
            QhSheetHeader(title: c.addTitle, closeLabel: c.done, onClose: onClose)
            QhSearchField(text: $query, placeholder: c.searchPlaceholder, clearLabel: c.clearSearch)
                .padding(.bottom, QhSpacing.x2)
            let results = results()
            if results.isEmpty {
                QhEmptyState(title: c.noResults).frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(results, id: \.number) { row in
                            let added = inQueue.contains(Int(row.number))
                            Button {
                                guard !added else { return }
                                QhHaptics.shared.perform(.segment)
                                onAdd(Int(row.number))
                            } label: {
                                HStack(spacing: QhSpacing.x3) {
                                    SurahNumberBadge(number: Int(row.number), status: status(row), learnedFraction: Double(row.learnedFraction), size: 36)
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(row.name).qhText({ $0.title2 }, color: { added ? $0.inkTertiary : $0.ink }).lineLimit(1)
                                        Text(ayahCountLabel(n: row.ayahCount, language: copy.language))
                                            .qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
                                    }
                                    Spacer(minLength: 0)
                                    Image(systemName: added ? "checkmark.circle.fill" : "plus")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundStyle(added ? qh.colors.learned : qh.colors.primary)
                                        .contentTransition(.symbolEffect(.replace))
                                }
                                .frame(minHeight: 60)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityElement(children: .combine)
                            .accessibilityLabel(added ? "\(row.name), \(c.added)" : row.name)
                            .animation(QhMotion.reduced ? nil : QhMotion.snappy, value: added)
                        }
                    }
                }
                .scrollDismissesKeyboard(.interactively)
            }
        }
        .task(id: catalog.count) { index = HomeStateBuilder.shared.searchIndex(rows: catalog) }
    }

    private func status(_ row: SurahRowUi) -> QhStatus {
        switch row.status {
        case .some(.learned): return .learned
        case .some(.learning): return .learning
        default: return .notStarted
        }
    }

    private func results() -> [SurahRowUi] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, let index else { return catalog }
        let byNumber = Dictionary(uniqueKeysWithValues: catalog.map { (Int($0.number), $0) })
        return index.search(query: trimmed).compactMap { byNumber[$0.intValue] }
    }
}

// MARK: - Pause

/// The numbers of the pause sheet, as in Android's `PauseSheetMath` (the end of a pause is never further than 13 days from today).
enum PauseSheetMath {
    static let maxDays = 14
    static let presets = [1, 3, 7, 14]
    static let defaultDays = 3

    static func minDays(_ pause: PlanPause?, today: Int) -> Int {
        guard let pause else { return 1 }
        return min(max(Int(pause.untilEpochDay) - today + 1, 1), maxDays)
    }
    static func initialDays(_ pause: PlanPause?, today: Int) -> Int { pause == nil ? defaultDays : minDays(pause, today: today) }
    static func clamp(_ days: Int, _ pause: PlanPause?, today: Int) -> Int { min(max(days, minDays(pause, today: today)), maxDays) }
    static func shiftDays(_ pause: PlanPause?, today: Int, days: Int) -> Int {
        guard let pause else { return days }
        return (today + days - 1) - Int(pause.untilEpochDay)
    }
    static func canExtend(_ pause: PlanPause, today: Int) -> Bool { minDays(pause, today: today) < maxDays }
}

/// The row "Pause" of "My plan": a calm line that opens the sheet; while the plan is paused it says "On pause until ..." with
/// "Continue now" and "Extend". Nothing here goes through "Save": a pause is an action of its own.
struct PauseSection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let pause: PlanPause?
    let today: Int
    let onPause: () -> Void
    let onResume: () -> Void

    var body: some View {
        let p = copy.pause
        if let pause {
            QhCard {
                VStack(alignment: .leading, spacing: QhSpacing.x3) {
                    HStack(spacing: QhSpacing.x3) {
                        QhIconTile(icon: "pause", tint: qh.colors.gold)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(p.rowActive(untilDate: copy.day(Int(pause.untilEpochDay)))).qhText({ $0.title2 })
                            Text(p.lessonBody(resumeDate: copy.day(Int(pause.resumeEpochDay)))).qhText({ $0.callout }, color: { $0.inkSecondary })
                        }
                    }
                    // One under the other: "Continue now" is long in some languages and must never be cut.
                    QhButton(p.resumeNow, size: .m, fullWidth: true, action: onResume)
                    if PauseSheetMath.canExtend(pause, today: today) {
                        QhButton(p.extend, variant: .tonal, size: .m, fullWidth: true, action: onPause)
                    }
                }
            }
        } else {
            QhCard(onTap: onPause) {
                HStack(spacing: QhSpacing.x3) {
                    QhIconTile(icon: "pause", tint: qh.colors.gold)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(p.rowTitle).qhText({ $0.title2 })
                        Text(p.rowSubtitle).qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
        }
    }
}

/// The sheet that sets a pause (or extends the one that is on): the days as chips 1, 3, 7, 14 and a step of one day, the line
/// "Lessons return on 12 November. Reviews will move by 7 days; your streak is kept." and the button.
struct PauseSheet: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let pause: PlanPause?
    let today: Int
    let onConfirm: (Int) -> Void
    let onClose: () -> Void
    @State private var days: Int

    init(pause: PlanPause?, today: Int, onConfirm: @escaping (Int) -> Void, onClose: @escaping () -> Void) {
        self.pause = pause; self.today = today; self.onConfirm = onConfirm; self.onClose = onClose
        _days = State(initialValue: PauseSheetMath.initialDays(pause, today: today))
    }

    var body: some View {
        let p = copy.pause
        let extending = pause != nil
        let minimum = PauseSheetMath.minDays(pause, today: today)
        let resumeDate = copy.day(today + days)
        let shift = PauseSheetMath.shiftDays(pause, today: today, days: days)
        VStack(alignment: .leading, spacing: QhSpacing.x4) {
            QhSheetHeader(title: extending ? p.extendTitle : p.sheetTitle, closeLabel: p.cancel, onClose: onClose)
                .padding(.bottom, -QhSpacing.x2)
            Text(p.daysQuestion).qhText({ $0.callout }, color: { $0.inkSecondary })
            QhChipFlow {
                ForEach(PauseSheetMath.presets, id: \.self) { preset in
                    QhChip(label: p.days(count: Int32(preset)), selected: days == preset) {
                        days = PauseSheetMath.clamp(preset, pause, today: today)
                    }
                    .disabled(preset < minimum)
                }
            }
            HStack {
                QhIconButton(icon: "minus", label: p.less) {
                    QhHaptics.shared.perform(.step)
                    days = PauseSheetMath.clamp(days - 1, pause, today: today)
                }
                .disabled(days <= minimum)
                Text(p.days(count: Int32(days)))
                    .qhText({ $0.title1 }).qhTabular()
                    .contentTransition(.numericText(value: Double(days)))
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel(p.days(count: Int32(days)))
                QhIconButton(icon: "plus", label: p.more) {
                    QhHaptics.shared.perform(.step)
                    days = PauseSheetMath.clamp(days + 1, pause, today: today)
                }
                .disabled(days >= PauseSheetMath.maxDays)
            }
            .animation(QhMotion.reduced ? nil : QhMotion.snappy, value: days)
            Text(info(p, extending: extending, shift: shift, resumeDate: resumeDate))
                .qhText({ $0.body }, color: { $0.inkSecondary })
                .fixedSize(horizontal: false, vertical: true)
            QhButton(extending ? p.confirmExtend : p.confirmPause, fullWidth: true) { onConfirm(days) }
                .disabled(extending && shift <= 0)
            Spacer(minLength: 0)
        }
    }

    private func info(_ p: PauseCopy, extending: Bool, shift: Int, resumeDate: String) -> String {
        if !extending { return p.info(resumeDate: resumeDate, count: Int32(days)) }
        if shift > 0 { return p.extendInfo(resumeDate: resumeDate, count: Int32(shift)) }
        return p.cardBody(resumeDate: resumeDate)
    }
}
