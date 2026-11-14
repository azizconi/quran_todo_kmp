import ComposeApp
import SwiftUI

/// Wraps its children onto new lines like text (chips of the plan, the khatm and the pause sheet), leading aligned.
struct QhChipFlow: Layout {
    var spacing: CGFloat = QhSpacing.x2
    var lineSpacing: CGFloat = QhSpacing.x1

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        return layout(width: width, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = layout(width: bounds.width, subviews: subviews)
        for (index, origin) in result.origins.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y), proposal: .unspecified)
        }
    }

    private func layout(width: CGFloat, subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        var origins: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var maxX: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += rowHeight + lineSpacing
                rowHeight = 0
            }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return (CGSize(width: width.isFinite ? width : maxX, height: y + rowHeight), origins)
    }
}

// MARK: - What I learn

/// The scope chips with their description (the queue rows follow as rows of the list, so they can be reordered).
struct WhatHeader: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: PlanUi
    let model: PlanModel

    var body: some View {
        let c = copy.plan
        let draft = ui.draft
        let scopes: [(HifzScope, String)] = [(.juz30, c.scopeJuz30), (.juz29, c.scopeJuz29), (.fromStart, c.scopeFromStart), (.custom, c.scopeCustom)]
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhChipFlow {
                ForEach(scopes, id: \.0) { scope, label in
                    QhChip(label: label, selected: draft.scope == scope) {
                        withQhAnimation { model.vm.selectScope(scope: scope) }
                    }
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                Text(description(draft.scope, c)).qhText({ $0.callout }, color: { $0.inkSecondary })
                if !ui.queue.isEmpty {
                    let surahs = Set(draft.queue.map(\.intValue))
                    let ayahs = surahs.reduce(0) { $0 + Int(QuranMeta.shared.ayahCount(surah: Int32($1))) }
                    Text(c.scopeInfo(surahs: Int32(surahs.count), ayahs: Int32(ayahs)))
                        .qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                }
            }
        }
        .padding(.horizontal, QhSpacing.x1)
    }

    private func description(_ scope: HifzScope, _ c: PlanCopy) -> String {
        switch scope {
        case .juz30: return c.describeJuz30
        case .juz29: return c.describeJuz29
        case .fromStart: return c.describeFromStart
        default: return c.describeCustom
        }
    }
}

/// One surah of the queue as a small card of its own: the number badge, the name and the count of ayahs, a mark ("now" in gold, a check
/// once learned) and, in an own list, the remove button (the system draws the drag handle at the trailing edge).
struct QueueRow: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let row: QueueRowUi
    let editable: Bool
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    let onRemove: () -> Void

    var body: some View {
        let c = copy.plan
        let subtitle = ayahCountLabel(n: row.ayahCount, language: copy.language)
        HStack(spacing: QhSpacing.x3) {
            SurahNumberBadge(number: Int(row.surah), status: row.learned ? .learned : .notStarted,
                             learnedFraction: row.learned ? 1 : 0, size: 36)
            VStack(alignment: .leading, spacing: 0) {
                Text(row.name).qhText({ $0.title2 }, color: { row.learned ? $0.inkTertiary : $0.ink }).lineLimit(1)
                Text(subtitle).qhText({ $0.callout }, color: { row.learned ? $0.inkTertiary : $0.inkSecondary }).lineLimit(1)
            }
            Spacer(minLength: 0)
            if row.learned {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 18)).foregroundStyle(qh.colors.inkTertiary)
            } else if row.current {
                Text(c.currentMark).qhText({ $0.label }, color: { $0.gold }).lineLimit(1)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(qh.colors.goldContainer, in: Capsule())
            }
            if editable {
                Button(action: onRemove) {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkSecondary)
                        .frame(width: 36, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(c.remove): \(row.name)")
            }
        }
        .padding(.horizontal, QhSpacing.x3)
        .padding(.vertical, QhSpacing.x2)
        .frame(minHeight: 60)
        .background(qh.colors.surface, in: qh.shapes.mdShape)
        .overlay { qh.shapes.mdShape.strokeBorder(qh.colors.hairline, lineWidth: 1) }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(row.name), \(subtitle)\(row.learned ? ", " + c.learnedMark : "")\(row.current ? ", " + c.currentMark : "")")
        .accessibilityActions {
            if editable {
                Button(c.moveUp, action: onMoveUp)
                Button(c.moveDown, action: onMoveDown)
                Button(c.remove, action: onRemove)
            }
        }
    }
}

// MARK: - Pace

/// A soft warning: an icon and a line in the "due" colour. Not a red error: the plan can still be saved.
struct PlanWarning: View {
    @Environment(\.qh) private var qh
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: QhSpacing.x2) {
            Image(systemName: "exclamationmark.triangle").font(.system(size: 15, weight: .semibold)).foregroundStyle(qh.colors.due)
            Text(text).qhText({ $0.callout })
            Spacer(minLength: 0)
        }
        .padding(QhSpacing.x3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(qh.colors.dueContainer, in: qh.shapes.mdShape)
        .accessibilityElement(children: .combine)
    }
}

struct PaceCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let model: PlanModel
    let ui: PlanUi
    let onPickDate: () -> Void

    var body: some View {
        let c = copy.plan
        let draft = ui.draft
        let byDate = draft.mode == .byDate
        QhCard {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                QhSegmentedControl(options: [c.modePerDay, c.modeByDate],
                                   selection: Binding(get: { byDate ? 1 : 0 },
                                                      set: { model.vm.setMode(mode: $0 == 0 ? .perDay : .byDate) }))
                if byDate {
                    let target = Int(draft.targetDay?.int32Value ?? ui.suggestedTarget)
                    Button(action: onPickDate) {
                        HStack(spacing: QhSpacing.x3) {
                            Image(systemName: "calendar").font(.system(size: 18, weight: .semibold)).foregroundStyle(qh.colors.primary)
                            Text(c.finishBy).qhText({ $0.callout }, color: { $0.inkSecondary })
                            Spacer(minLength: QhSpacing.x2)
                            Text(c.dateLabel(epochDay: Int32(target), today: ui.today))
                                .qhText({ $0.title2 }, color: { $0.primary }).qhTabular()
                                .contentTransition(.numericText())
                        }
                        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
                        .frame(maxWidth: .infinity, minHeight: QhSpacing.minTouch)
                        .background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
                        .contentShape(qh.shapes.mdShape)
                    }
                    .buttonStyle(QhPressStyle(scale: PressableDefaults.scale, shape: AnyShape(qh.shapes.mdShape), highlight: true))
                    .accessibilityLabel("\(c.finishBy): \(c.dateLabel(epochDay: Int32(target), today: ui.today))")
                    if let summary = ui.summary, let required = summary.requiredPerDay {
                        Text(c.needPerDay(n: Int32(max(required.int32Value, 1))))
                            .qhText({ $0.title2 }, color: { summary.tooFast ? $0.due : $0.ink })
                        if summary.tooFast {
                            PlanWarning(text: c.tooFast(n: required.int32Value))
                        }
                    }
                } else {
                    AyahWheel(label: c.perDayLabel, minimum: 1, maximum: 20,
                              value: Binding(get: { Int(draft.perDay) }, set: { model.vm.setPerDay(value: Int32($0)) }))
                }
                if let estimate = ui.estimate {
                    HStack(spacing: QhSpacing.x2) {
                        Image(systemName: "clock").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkSecondary)
                        Text(c.minutesPerDay(n: estimate.minutes)).qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                    }
                    if estimate.overBudget { PlanWarning(text: c.longLesson(n: estimate.minutes)) }
                }
            }
            .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: byDate)
        }
    }
}

// MARK: - The circle of the old

struct OldCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: PlanUi
    let model: PlanModel

    var body: some View {
        let c = copy.plan
        let days = Int(ui.draft.manzilDays)
        QhCard {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                Text(c.oldTitle(days: Int32(days))).qhText({ $0.title2 }).contentTransition(.numericText())
                QhSlider(value: Binding(get: { Double(days) }, set: { model.vm.setManzilDays(days: Int32($0.rounded())) }),
                         range: 3 ... 30, step: 1)
                    .accessibilityLabel(c.oldTitle(days: Int32(days)))
                Text(detail(ui, c)).qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                if let hint = ui.oldHint {
                    VStack(alignment: .leading, spacing: QhSpacing.x3) {
                        HStack(alignment: .top, spacing: QhSpacing.x2) {
                            Image(systemName: "clock").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkSecondary)
                                .padding(.top, 3)
                            Text(c.oldHint(minutes: hint.minutesPerDay, days: hint.suggestedDays, targetMinutes: hint.suggestedMinutes))
                                .qhText({ $0.callout })
                        }
                        QhButton(c.daysLabel(n: hint.suggestedDays), variant: .tonal, size: .s) {
                            withQhAnimation { model.vm.applySuggestedManzilDays() }
                        }
                        .accessibilityLabel(c.oldSetDays(days: hint.suggestedDays))
                    }
                    .padding(QhSpacing.x3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
                    .transition(.opacity.combined(with: .scale(scale: 0.96, anchor: .top)))
                }
            }
            .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: ui.oldHint != nil)
        }
    }

    private func detail(_ ui: PlanUi, _ c: PlanCopy) -> String {
        guard let pages = ui.oldPagesPerDay else { return c.oldNone }
        guard let minutes = ui.oldMinutesPerDay else { return c.oldPages(pages: pages.floatValue) }
        return "\(c.oldPages(pages: pages.floatValue)) · \(c.minutesPerDay(n: minutes.int32Value))"
    }
}

// MARK: - Forecast

struct ForecastCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: PlanUi

    var body: some View {
        let c = copy.plan
        QhCard {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                if let summary = ui.summary {
                    let headline = headline(summary.headline, c)
                    Text(headline).qhText({ $0.title1 }).id(headline)
                        .transition(.opacity)
                    if !(summary.headline is ForecastHeadlineEmpty) {
                        if summary.totalAyahs > 0 {
                            QhLinearProgress(progress: Double(summary.totalAyahs - summary.remainingAyahs) / Double(summary.totalAyahs))
                                .accessibilityLabel(c.remaining(remaining: summary.remainingAyahs, total: summary.totalAyahs))
                            Text(c.remaining(remaining: summary.remainingAyahs, total: summary.totalAyahs))
                                .qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                        }
                        if let required = summary.requiredPerDay, summary.remainingAyahs > 0 {
                            Text(c.needPerDay(n: Int32(max(required.int32Value, 1)))).qhText({ $0.title2 })
                        }
                        if summary.behindAyahs > 0 {
                            Text(c.behind(ayahs: summary.behindAyahs)).qhText({ $0.title2 }, color: { $0.due })
                        }
                    }
                } else {
                    Skeleton().frame(width: 200, height: 28)
                    Skeleton().frame(width: 130, height: 18)
                }
            }
            .animation(QhMotion.reduced ? nil : .easeOut(duration: QhMotion.short), value: ui.summary?.remainingAyahs)
        }
    }

    private func headline(_ headline: ForecastHeadline, _ c: PlanCopy) -> String {
        switch onEnum(of: headline) {
        case .empty: return c.forecastEmpty
        case .finished: return c.finished
        case let .finishOn(on): return c.finishOn(epochDay: on.epochDay, today: ui.today)
        }
    }
}
