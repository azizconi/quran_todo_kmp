import ComposeApp
import SwiftUI

/// The reading-plan and Ramadan cards under the row "Check" of the Lesson tab (`KhatmCards.kt`): the khatm card when there is a plan, the
/// Ramadan card within a week of Ramadan and during it, and the quiet row "Reading plan" when there is no plan.
struct KhatmHomeCards: View {
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @State private var model = KhatmModel()
    let onOpenKhatm: () -> Void
    let onOpenKhatmPicker: () -> Void
    let onOpenMushaf: (Int) -> Void

    var body: some View {
        let ui = model.ui.value
        VStack(spacing: QhSpacing.x3) {
            if ui.loaded {
                if let state = ui.state {
                    KhatmCard(info: KhatmDayInfo(state: state, today: Int(ui.today)), readPage: ui.readPage.map { Int($0.int32Value) },
                              onOpen: onOpenKhatm, onNewTarget: onOpenKhatmPicker, onRead: onOpenMushaf,
                              onMarkRead: { model.vm.markPortionRead() })
                        .transition(.opacity.combined(with: .scale(scale: 0.98)))
                }
                if case .outside = onEnum(of: ui.ramadan) {} else {
                    RamadanCard(status: ui.ramadan, today: Int(ui.today), hasPlan: ui.state != nil,
                                onCreatePlan: { model.vm.createMonthPlan() },
                                onListen: { model.vm.listenJuz(juz: Int32($0)) }, onRead: onOpenMushaf)
                }
                if ui.state == nil {
                    ReadingPlanEntry(label: copy.khatm.screenTitle, action: onOpenKhatm)
                }
            }
        }
        .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: ui.state != nil)
        .task { model.bind(toaster: env.toaster, copy: copy) }
    }
}

/// "Reading plan" as one quiet row of text with the book icon: no card, at least 48pt tall.
private struct ReadingPlanEntry: View {
    @Environment(\.qh) private var qh
    let label: String
    let action: () -> Void

    var body: some View {
        HStack(spacing: QhSpacing.x2) {
            Image(systemName: "book").font(.system(size: 16, weight: .medium)).foregroundStyle(qh.colors.inkSecondary)
            Text(label).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
        }
        .padding(.horizontal, QhSpacing.x2)
        .frame(maxWidth: .infinity, minHeight: QhSpacing.minTouch)
        .pressable(scale: 0.99, shape: qh.shapes.mdShape, action: action)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
    }
}

/// "Khatm: today pages 121-140 · ~25 min", the bar of the whole plan, "Read" and "Mark as read".
struct KhatmCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let info: KhatmDayInfo
    let readPage: Int?
    let onOpen: () -> Void
    let onNewTarget: () -> Void
    let onRead: (Int) -> Void
    let onMarkRead: () -> Void

    var body: some View {
        let c = copy.khatm
        let headline = info.headline(c)
        let progress = info.progress(c)
        QhCard(padding: QhSpacing.x3, onTap: onOpen) {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                HStack(alignment: .top, spacing: QhSpacing.x3) {
                    QhIconTile(icon: "book", tint: qh.colors.primary)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(headline).qhText({ $0.title2 }).lineLimit(2).fixedSize(horizontal: false, vertical: true)
                        Text(progress).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2).qhTabular()
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                QhLinearProgress(progress: info.fraction)
                switch onEnum(of: info.status) {
                case let .overdue(over):
                    HStack(spacing: QhSpacing.x2) {
                        QhButton(c.readButton, size: .m, icon: "book", fullWidth: true) { onRead(Int(over.nextPage)) }
                        QhButton(c.newTarget, variant: .tonal, size: .m, icon: "pencil", fullWidth: true, action: onNewTarget)
                    }
                case let .pending(pending):
                    // The person read on to the end of today's portion in the pages: ask softly, never decide.
                    if KhatmMath.shared.portionReachedBy(state: info.state, today: Int32(info.today), readPage: readPage.map { KotlinInt(int: Int32($0)) }) {
                        HStack(spacing: QhSpacing.x3) {
                            Text(c.reachedPrompt).qhText({ $0.callout })
                            QhButton(c.reachedConfirm, size: .s, icon: "checkmark", action: onMarkRead)
                        }
                    }
                    HStack(spacing: QhSpacing.x2) {
                        QhButton(c.readButton, size: .m, icon: "book", fullWidth: true) { onRead(Int(pending.portion.firstPage)) }
                        QhButton(c.markReadButton, variant: .tonal, size: .m, icon: "checkmark", fullWidth: true, action: onMarkRead)
                    }
                default: EmptyView()
                }
            }
        }
        .accessibilityElement(children: .contain)
    }
}

/// Ramadan: the calligraphy "رمضان كريم", the night, "A khatm plan for the month" (when there is no plan yet), and during the month the juz
/// of the night with "Listen to the juz" and "Read". Calm geometry only, no stars.
struct RamadanCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let status: RamadanStatus
    let today: Int
    let hasPlan: Bool
    let onCreatePlan: () -> Void
    let onListen: (Int) -> Void
    let onRead: (Int) -> Void

    var body: some View {
        let c = copy.khatm
        let line: String? = {
            switch onEnum(of: status) {
            case let .upcoming(up): return c.ramadanUpcoming(days: up.daysUntil)
            case let .active(active): return c.ramadanNight(night: active.dayOfMonth, daysLeft: active.daysLeft(today: Int32(today)))
            case .outside: return nil
            }
        }()
        if let line {
            QhCard(style: .hero, padding: QhSpacing.cardLarge) {
                VStack(alignment: .leading, spacing: QhSpacing.x3) {
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: QhSpacing.x1) {
                            Text(c.ramadanTitle.uppercased()).qhText({ $0.overline }, color: { $0.primary })
                            Text(line).qhText({ $0.title2 })
                        }
                        Spacer(minLength: QhSpacing.x3)
                        // The Arabic is always right to left, whatever the language of the app is.
                        Text("رمضان كريم")
                            .font(.custom("ArefRuqaa-Regular", fixedSize: 34))
                            .foregroundStyle(qh.colors.gold)
                            .environment(\.layoutDirection, .rightToLeft)
                            .lineLimit(1).minimumScaleFactor(0.5)
                            .accessibilityHidden(true)
                    }
                    if case let .active(active) = onEnum(of: status) {
                        Text(c.juzTitle(juz: active.juz)).qhText({ $0.title1 })
                        HStack(spacing: QhSpacing.x2) {
                            QhButton(c.listenJuz, size: .m, icon: "headphones", fullWidth: true) { onListen(Int(active.juz)) }
                            QhButton(c.readJuz, variant: .tonal, size: .m, icon: "book", fullWidth: true) {
                                onRead(Int(RamadanCalendar.shared.firstPageOfJuz(juz: active.juz)))
                            }
                        }
                    }
                    if !hasPlan {
                        VStack(alignment: .leading, spacing: QhSpacing.x1) {
                            QhButton(c.monthPlan, variant: isActive ? .text : .primary, size: .m, fullWidth: true, action: onCreatePlan)
                            Text(c.monthPlanHint).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.horizontal, QhSpacing.x1)
                        }
                    }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel("\(c.ramadanTitle). \(line)")
        }
    }

    private var isActive: Bool { if case .active = onEnum(of: status) { return true } else { return false } }
}
