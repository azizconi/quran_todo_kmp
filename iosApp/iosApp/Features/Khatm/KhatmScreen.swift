import ComposeApp
import SwiftUI

/// The reading plan (`KhatmScreen.kt`, `Dest.Khatm`). No plan: the presets (7, 15, 30, 60 days) and "By a chosen date", from the first
/// page or from the current one. A plan: how far it is, the portion of today with "Read" and "Mark as read", the calendar of its days
/// (read, missed, today), a new end date, "Mark up to a page" for a manual fix and "Delete the plan". Reading here never touches lessons.
struct KhatmScreen: View {
    let pickTarget: Bool
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @State private var model = KhatmModel()
    @State private var stagger = StaggerState(screen: "khatm")
    @State private var fromPage = 1
    @State private var datePicker: DatePickerFor?
    @State private var confirmDelete = false
    @State private var openedPicker = false

    private enum DatePickerFor: Identifiable { case new, change; var id: Int { self == .new ? 0 : 1 } }

    var body: some View {
        let ui = model.ui.value
        let c = copy.khatm
        ScrollView {
            VStack(spacing: QhSpacing.section) {
                if ui.loaded {
                    if let state = ui.state {
                        let info = KhatmDayInfo(state: state, today: Int(ui.today))
                        Overview(info: info, onRead: { router.push(.mushaf(page: $0, ayahKey: nil)) },
                                 onMarkRead: { model.vm.markPortionRead() }, onNewTarget: { datePicker = .change })
                            .staggeredEnter(index: 0, state: stagger)
                        KhatmCalendar(state: state, today: Int(ui.today))
                            .staggeredEnter(index: 1, state: stagger)
                        ManagePlan(state: state, onChangeTarget: { datePicker = .change },
                                   onMarkUpTo: { model.vm.markUpTo(page: Int32($0)) },
                                   onDelete: { model.vm.delete() }, confirmDelete: $confirmDelete)
                            .staggeredEnter(index: 2, state: stagger)
                    } else {
                        NewPlan(currentPage: ui.readPage.map { Int($0.int32Value) }, fromPage: $fromPage,
                                onPreset: { model.vm.create(days: KotlinInt(int: Int32($0)), targetEpochDay: nil, fromPage: Int32(fromPage)) },
                                onPickDate: { datePicker = .new })
                            .staggeredEnter(index: 0, state: stagger)
                    }
                } else {
                    Skeleton(shape: qh.shapes.lgShape).frame(height: 200)
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x8)
        }
        .background(qh.colors.bg)
        .navigationTitle(c.screenTitle)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .onChange(of: ui.loaded) { _, loaded in if loaded { stagger.markPlayed(); openPickerIfAsked(ui) } }
        .onAppear { openPickerIfAsked(ui) }
        .task { model.bind(toaster: env.toaster, copy: copy) }
        .qhSheet(item: $datePicker, detents: [.large]) { which in
            let current = ui.state?.plan.targetEpochDay
            TargetDateSheet(title: copy.plan.pickDate, initialDay: Int(current ?? ui.today + 30), today: Int(ui.today),
                            okLabel: copy.plan.ok, cancelLabel: copy.plan.cancel,
                            onPick: { day in
                                datePicker = nil
                                if which == .new {
                                    model.vm.create(days: nil, targetEpochDay: KotlinInt(int: Int32(day)), fromPage: Int32(fromPage))
                                } else {
                                    model.vm.changeTarget(targetEpochDay: Int32(day))
                                }
                            }, onCancel: { datePicker = nil })
        }
    }

    /// `Dest.Khatm(pickTarget: true)` (the overdue card): the date picker opens with the screen.
    private func openPickerIfAsked(_ ui: KhatmUi) {
        guard pickTarget, !openedPicker, ui.loaded, ui.state != nil else { return }
        openedPicker = true
        datePicker = .change
    }
}

private extension View {
    /// `qhSheet` for an `Identifiable` item.
    func qhSheet<Item: Identifiable, SheetContent: View>(item: Binding<Item?>, detents: Set<PresentationDetent>,
                                                         @ViewBuilder content: @escaping (Item) -> SheetContent) -> some View {
        sheet(item: item) { value in
            content(value)
                .presentationDetents(detents)
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
    }
}

// MARK: - No plan

private struct NewPlan: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let currentPage: Int?
    @Binding var fromPage: Int
    let onPreset: (Int) -> Void
    let onPickDate: () -> Void

    var body: some View {
        let c = copy.khatm
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhCard {
                Text(c.newPlanTitle).qhText({ $0.title1 })
                Text(c.newPlanBody).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
            }
            if let currentPage, currentPage > 1 {
                QhSectionHeader(c.startTitle).padding(.horizontal, QhSpacing.x1)
                HStack(spacing: QhSpacing.x2) {
                    QhChip(label: c.fromFirstPage, selected: fromPage == 1) { fromPage = 1 }
                    QhChip(label: c.fromCurrent(page: Int32(currentPage)), selected: fromPage == currentPage) { fromPage = currentPage }
                }
            }
            ForEach(KhatmMath.shared.PRESET_DAYS.map(\.intValue), id: \.self) { days in
                QhButton(c.preset(days: Int32(days)), variant: days == 30 ? .primary : .tonal, size: .m, fullWidth: true) { onPreset(days) }
            }
            QhButton(c.toDate, variant: .text, size: .m, fullWidth: true, action: onPickDate)
        }
    }
}

// MARK: - A plan

private struct Overview: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let info: KhatmDayInfo
    let onRead: (Int) -> Void
    let onMarkRead: () -> Void
    let onNewTarget: () -> Void

    var body: some View {
        let c = copy.khatm
        let plan = info.state.plan
        QhCard {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                Text(c.summary(totalPages: plan.totalPages, untilDate: c.dateLabel(epochDay: plan.targetEpochDay, today: Int32(info.today))))
                    .qhText({ $0.title1 })
                VStack(alignment: .leading, spacing: QhSpacing.x2) {
                    QhLinearProgress(progress: info.fraction)
                    Text(c.progress(read: KhatmMath.shared.pagesRead(state: info.state), total: plan.totalPages))
                        .qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                }
                switch onEnum(of: info.status) {
                case .complete:
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.completeBanner).qhText({ $0.title2 }, color: { $0.learned })
                        Text(c.cardCompleteBody).qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                case let .overdue(over):
                    Text(c.overdueTitle).qhText({ $0.title2 }, color: { $0.due })
                    HStack(spacing: QhSpacing.x2) {
                        QhButton(c.readButton, size: .m, icon: "book", fullWidth: true) { onRead(Int(over.nextPage)) }
                        QhButton(c.newTarget, variant: .tonal, size: .m, icon: "pencil", fullWidth: true, action: onNewTarget)
                    }
                case let .pending(pending):
                    Text(pace).qhText({ $0.callout }, color: { $0.inkSecondary })
                    Text(c.cardPending(first: pending.portion.firstPage, last: pending.portion.lastPage, minutes: pending.minutes))
                        .qhText({ $0.title2 })
                    HStack(spacing: QhSpacing.x2) {
                        QhButton(c.readButton, size: .m, icon: "book", fullWidth: true) { onRead(Int(pending.portion.firstPage)) }
                        QhButton(c.markReadButton, variant: .tonal, size: .m, icon: "checkmark", fullWidth: true, action: onMarkRead)
                    }
                case let .doneToday(done):
                    Text(pace).qhText({ $0.callout }, color: { $0.inkSecondary })
                    if let next = done.next {
                        Text(c.cardDone(first: next.firstPage, last: next.lastPage)).qhText({ $0.title2 })
                    }
                }
            }
        }
    }

    /// "20 pages a day · 12 days left · 2 pages ahead".
    private var pace: String {
        let c = copy.khatm
        let toEnd = Int(KhatmMath.shared.daysToEnd(plan: info.state.plan, today: Int32(info.today)))
        var parts = [c.daily(pages: KhatmMath.shared.dailyPages(state: info.state, today: Int32(info.today)))]
        parts.append(toEnd == 0 ? c.lastDay : c.daysLeft(days: Int32(toEnd + 1)))
        if info.delta < 0 { parts.append(c.behind(pages: Int32(-info.delta))) } else if info.delta > 0 { parts.append(c.ahead(pages: Int32(info.delta))) }
        return parts.joined(separator: " · ")
    }
}

/// The days of the plan, seven in a row: read (filled with a check), missed (warm outline), today (outline of the brand), later (calm).
private struct KhatmCalendar: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let state: KhatmState
    let today: Int

    var body: some View {
        let c = copy.khatm
        let days = KhatmMath.shared.calendar(state: state, today: Int32(today), limit: 400)
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhSectionHeader(c.calendarTitle).padding(.horizontal, QhSpacing.x1)
            QhCard {
                VStack(spacing: 6) {
                    ForEach(Array(stride(from: 0, to: days.count, by: 7)), id: \.self) { start in
                        HStack(spacing: 6) {
                            ForEach(0 ..< 7, id: \.self) { column in
                                let index = start + column
                                if index < days.count {
                                    DayCell(number: index + 1, status: days[index].status,
                                            label: label(days[index].status, c))
                                } else {
                                    Color.clear.frame(maxWidth: .infinity, minHeight: 36)
                                }
                            }
                        }
                    }
                    HStack(spacing: QhSpacing.x4) {
                        Legend(label: c.legendRead, status: .read)
                        Legend(label: c.legendMissed, status: .missed)
                        Legend(label: c.legendToday, status: .today)
                        Spacer(minLength: 0)
                    }
                    .padding(.top, QhSpacing.x2)
                }
            }
        }
    }

    private func label(_ status: KhatmDayStatus, _ c: KhatmCopy) -> String {
        switch status {
        case .read: return c.legendRead
        case .missed: return c.legendMissed
        case .today: return c.legendToday
        default: return ""
        }
    }
}

private struct DayCell: View {
    @Environment(\.qh) private var qh
    let number: Int
    let status: KhatmDayStatus
    let label: String

    var body: some View {
        let look = DayLook(status: status, colors: qh.colors)
        ZStack {
            Circle().fill(look.fill)
            Circle().strokeBorder(look.outline, lineWidth: status == .future ? 0 : 1.5)
            if status == .read {
                Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(look.content)
            } else {
                Text("\(number)").qhText({ $0.label }, color: { _ in look.content }).qhTabular()
            }
        }
        .frame(width: 36, height: 36)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(number) \(label)".trimmingCharacters(in: .whitespaces))
    }
}

/// Colours of a day of the calendar: the state is also carried by the shape (a check, an outline), never by colour alone.
private struct DayLook {
    let fill: Color
    let outline: Color
    let content: Color

    init(status: KhatmDayStatus, colors c: QhColors) {
        switch status {
        case .read: (fill, outline, content) = (c.learned, c.learned, c.onLearned)
        case .missed: (fill, outline, content) = (c.dueContainer, c.due, c.due)
        case .today: (fill, outline, content) = (.clear, c.primary, c.primary)
        default: (fill, outline, content) = (c.surfaceMuted, .clear, c.inkSecondary)
        }
    }
}

private struct Legend: View {
    @Environment(\.qh) private var qh
    let label: String
    let status: KhatmDayStatus

    var body: some View {
        let look = DayLook(status: status, colors: qh.colors)
        HStack(spacing: QhSpacing.x1) {
            Circle().fill(look.fill).overlay { Circle().strokeBorder(look.outline, lineWidth: 1.5) }.frame(width: 14, height: 14)
            Text(label).qhText({ $0.label }, color: { $0.inkSecondary }).lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Manage

private struct ManagePlan: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let state: KhatmState
    let onChangeTarget: () -> Void
    let onMarkUpTo: (Int) -> Void
    let onDelete: () -> Void
    @Binding var confirmDelete: Bool
    @State private var pageText = ""
    @FocusState private var focused: Bool

    var body: some View {
        let c = copy.khatm
        let plan = state.plan
        let complete = KhatmMath.shared.isComplete(state: state)
        let page = Int(pageText)
        let valid = page.map { $0 >= Int(plan.startPage) - 1 && $0 <= Int(plan.endPage) } ?? false
        VStack(spacing: QhSpacing.x3) {
            if !complete {
                QhButton(c.changeTarget, variant: .tonal, size: .m, icon: "pencil", fullWidth: true, action: onChangeTarget)
            }
            QhCard {
                VStack(alignment: .leading, spacing: QhSpacing.x3) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(c.markUpToTitle).qhText({ $0.title2 })
                        Text(c.markUpToHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                    HStack(spacing: QhSpacing.x3) {
                        TextField("\(c.pageLabel) \(plan.startPage)–\(plan.endPage)", text: $pageText)
                            .keyboardType(.numberPad)
                            .focused($focused)
                            .qhText({ $0.body })
                            .padding(.horizontal, QhSpacing.x3)
                            .frame(minHeight: QhSpacing.minTouch)
                            .background(qh.shapes.mdShape.strokeBorder(focused ? qh.colors.primary : qh.colors.hairline, lineWidth: focused ? 2 : 1))
                            .onChange(of: pageText) { _, text in
                                let digits = String(text.filter(\.isNumber).prefix(3))
                                if digits != text { pageText = digits }
                            }
                        QhButton(c.markUpToAction, size: .m) {
                            if let page { onMarkUpTo(page) }
                            pageText = ""
                            focused = false
                        }
                        .disabled(!valid)
                    }
                }
            }
            QhButton(complete ? c.startNew : c.deletePlan, variant: complete ? .primary : .text, size: .m,
                     icon: complete ? nil : "trash", fullWidth: true) {
                if complete { onDelete() } else { confirmDelete = true }
            }
        }
        .confirmationDialog(c.deleteTitle, isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(c.delete, role: .destructive, action: onDelete)
            Button(c.cancel, role: .cancel) {}
        } message: { Text(c.deleteBody) }
    }
}
