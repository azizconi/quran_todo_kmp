import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `PlanViewModel` ("My plan") with its state, the pause and the one-shot events as observable values.
@MainActor
@Observable
final class PlanModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.plan() }
    let ui: FlowState<PlanUi>
    let pause: FlowState<PlanPause?>
    @ObservationIgnored private var events: FlowEvents<PlanEvent>?
    var vm: PlanViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        pause = FlowState(holder.vm.pause)
        holder.vm.setLanguage(next: IosUi.shared.language.value)
    }

    func listen(_ handler: @escaping @MainActor (PlanEvent) -> Void) {
        events = FlowEvents(holder.vm.events, handler)
    }
}

/// "My plan" (`PlanScreen.kt`): what to learn (the preset or an own list with native drag reorder and edge auto-scroll), the pace, the
/// circle of the old, the forecast, the pause, then Save / Delete. The person edits a draft; nothing is saved until "Save".
///
/// A `List` carries the queue so that the system reorder (long press on the handle, rows make room, the list scrolls at the edges)
/// is the real thing; the other sections are single rows made to look like the cards of the rest of the app.
struct PlanScreen: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @State private var model = PlanModel()
    @State private var stagger = StaggerState(screen: "plan")
    @State private var expanded = false
    @State private var pickDate = false
    @State private var addSurah = false
    @State private var pauseSheet = false
    @State private var askResume = false
    @State private var askDelete = false
    @State private var askUnsaved = false
    @State private var wired = false

    var body: some View {
        let ui = model.ui.value
        let c = copy.plan
        ZStack {
            qh.colors.bg.ignoresSafeArea()
            if ui.loaded {
                content(ui)
                    .transition(.opacity)
            } else {
                loading
            }
        }
        .animation(QhMotion.reduced ? nil : .easeOut(duration: 0.2), value: ui.loaded)
        .navigationTitle(c.title)
        .navigationBarTitleDisplayMode(.large)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                QhIconButton(icon: "chevron.backward", label: c.back, filled: false) { back(ui) }
            }
        }
        .qhNavigationStyle()
        .background(SwipeBackGuard(enabled: !ui.edited))
        .onChange(of: ui.loaded) { _, loaded in if loaded { stagger.markPlayed() } }
        .onChange(of: copy.language) { _, language in model.vm.setLanguage(next: language) }
        .task {
            guard !wired else { return }
            wired = true
            model.listen { event in handle(event) }
        }
        .qhSheet(isPresented: $pickDate, detents: [.large]) {
            // The system sheet draws its own bar; the shared sheet frame is not used for a calendar.
            TargetDateSheet(title: c.pickDate, initialDay: Int(ui.draft.targetDay?.int32Value ?? ui.suggestedTarget), today: Int(ui.today),
                            okLabel: c.ok, cancelLabel: c.cancel,
                            onPick: { day in pickDate = false; model.vm.setTarget(epochDay: Int32(day)) },
                            onCancel: { pickDate = false })
        }
        .qhSheet(isPresented: $addSurah, detents: [.large]) {
            PlanAddSurahSheet(catalog: ui.catalog, inQueue: Set(ui.draft.queue.map { $0.intValue }),
                          onAdd: { model.vm.add(surah: Int32($0)) }, onClose: { addSurah = false })
        }
        .qhSheet(isPresented: $pauseSheet, detents: [.medium, .large]) {
            let active = model.pause.value.flatMap { Int(ui.today) >= Int($0.fromEpochDay) && Int(ui.today) <= Int($0.untilEpochDay) ? $0 : nil }
            PauseSheet(pause: active, today: Int(ui.today), onConfirm: { days in
                pauseSheet = false
                model.vm.pausePlan(days: Int32(days))
            }, onClose: { pauseSheet = false })
        }
        .confirmationDialog(copy.pause.resumeTitle, isPresented: $askResume, titleVisibility: .visible) {
            Button(copy.pause.resumeConfirm) { model.vm.resumePlan() }
            Button(copy.pause.cancel, role: .cancel) {}
        } message: { Text(copy.pause.resumeBody) }
        .confirmationDialog(c.deleteTitle, isPresented: $askDelete, titleVisibility: .visible) {
            Button(c.deleteConfirm, role: .destructive) { model.vm.clear() }
            Button(c.cancel, role: .cancel) {}
        } message: { Text(c.deleteBody) }
        .confirmationDialog(c.unsavedTitle, isPresented: $askUnsaved, titleVisibility: .visible) {
            if ui.canSave { Button(c.save) { model.vm.save() } }
            Button(c.discard, role: .destructive) { router.pop() }
            Button(c.keepEditing, role: .cancel) {}
        } message: { Text(c.unsavedBody) }
    }

    private func back(_ ui: PlanUi) {
        if ui.edited { askUnsaved = true } else { router.pop() }
    }

    private func handle(_ event: PlanEvent) {
        let c = copy.plan
        switch onEnum(of: event) {
        case .saved:
            QhHaptics.shared.perform(.success)
            env.toaster.show(c.savedToast, kind: .success)
            router.pop()
        case .cleared:
            env.toaster.show(c.deletedToast, kind: .info)
            router.pop()
        case .failed: env.toaster.show(c.saveFailed, kind: .error)
        case let .paused(paused):
            QhHaptics.shared.perform(.success)
            env.toaster.show(copy.pause.pausedToast(untilDate: copy.day(Int(paused.untilEpochDay))), kind: .success)
        case .resumed: env.toaster.show(copy.pause.resumedToast, kind: .success)
        case .pauseFailed: env.toaster.show(copy.pause.failed, kind: .error)
        }
    }

    private var loading: some View {
        VStack(spacing: QhSpacing.section) {
            Skeleton(shape: qh.shapes.lgShape).frame(height: 220)
            Skeleton(shape: qh.shapes.lgShape).frame(height: 160)
            Skeleton(shape: qh.shapes.lgShape).frame(height: 120)
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x4)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    // MARK: The list

    private func content(_ ui: PlanUi) -> some View {
        let c = copy.plan
        let draft = ui.draft
        let custom = draft.isCustom
        let rows = visibleRows(ui)
        return List {
            PlanSectionRow(title: c.sectionWhat, index: 0, stagger: stagger) { WhatHeader(ui: ui, model: model) }

            if ui.queue.isEmpty {
                Text(c.queueEmpty).qhText({ $0.body }, color: { $0.inkSecondary })
                    .plan(top: 8)
            } else {
                ForEach(rows, id: \.surah) { row in
                    QueueRow(row: row, editable: custom,
                             onMoveUp: { move(Int(row.surah), by: -1, in: ui) },
                             onMoveDown: { move(Int(row.surah), by: 1, in: ui) },
                             onRemove: { withQhAnimation { model.vm.remove(surah: Int32(row.surah)) } })
                        .plan(top: 6)
                        .moveDisabled(!custom)
                        .deleteDisabled(true)
                }
                .onMove { source, destination in
                    guard custom, let from = source.first else { return }
                    let to = from < destination ? destination - 1 : destination
                    guard from != to else { return }
                    QhHaptics.shared.perform(.tick)
                    model.vm.move(from: Int32(from), to: Int32(to))
                }
                if !custom && ui.queue.count > Self.foldLimit {
                    foldToggle(count: ui.queue.count).plan(top: 6)
                }
            }
            queueActions(custom: custom).plan(top: 10)

            PlanSectionRow(title: c.sectionPace, index: 1, stagger: stagger) { PaceCard(model: model, ui: ui, onPickDate: { pickDate = true }) }
                .plan(top: QhSpacing.section)
            PlanSectionRow(title: c.sectionOld, index: 2, stagger: stagger) { OldCard(ui: ui, model: model) }
                .plan(top: QhSpacing.section)
            PlanSectionRow(title: c.sectionForecast, index: 3, stagger: stagger) { ForecastCard(ui: ui) }
                .plan(top: QhSpacing.section)

            let activePause = model.pause.value.flatMap { Int(ui.today) >= Int($0.fromEpochDay) && Int(ui.today) <= Int($0.untilEpochDay) ? $0 : nil }
            if ui.hasSavedPlan || model.pause.value != nil {
                PauseSection(pause: activePause, today: Int(ui.today), onPause: { pauseSheet = true }, onResume: { askResume = true })
                    .staggeredEnter(index: 4, state: stagger)
                    .plan(top: QhSpacing.section)
            }
            if ui.hasSavedPlan {
                QhButton(c.delete, variant: .danger, size: .m, fullWidth: true) { askDelete = true }
                    .staggeredEnter(index: 5, state: stagger)
                    .plan(top: QhSpacing.x4)
            }
            Color.clear.frame(height: 96).plan(top: 0)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.interactively)
        .environment(\.editMode, .constant(custom ? .active : .inactive))
        .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: ui.queue.map(\.surah))
        .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: expanded)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            SaveBar(label: ui.hasSavedPlan ? c.save : c.create, enabled: ui.canSave && ui.dirty) { model.vm.save() }
        }
    }

    // MARK: Queue pieces

    /// A preset is long (up to 114 rows): folded to the rows around the current surah, one tap shows all of it.
    private static let foldLimit = 6

    private func visibleRows(_ ui: PlanUi) -> [QueueRowUi] {
        let rows = ui.queue
        if ui.draft.isCustom || rows.count <= Self.foldLimit || expanded { return rows }
        let current = rows.firstIndex { $0.current } ?? 0
        let start = min(max(current - 1, 0), rows.count - Self.foldLimit)
        return Array(rows[start ..< start + Self.foldLimit])
    }

    private func foldToggle(count: Int) -> some View {
        Button {
            QhHaptics.shared.perform(.tick)
            expanded.toggle()
        } label: {
            HStack(spacing: QhSpacing.x2) {
                Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 14, weight: .bold))
                Text(expanded ? copy.plan.showLess : copy.plan.showAll(n: Int32(count)))
                    .qhText({ $0.title2 }, color: { $0.primary })
            }
            .foregroundStyle(qh.colors.primary)
            .frame(maxWidth: .infinity, minHeight: QhSpacing.minTouch)
        }
        .buttonStyle(.plain)
    }

    private func queueActions(custom: Bool) -> some View {
        let c = copy.plan
        return Group {
            if custom {
                QhButton(c.addSurah, variant: .tonal, size: .m, icon: "plus", fullWidth: true) { addSurah = true }
            } else {
                QhButton(c.editOrder, variant: .tonal, size: .m, icon: "pencil", fullWidth: true) {
                    withQhAnimation { model.vm.editOrder() }
                }
            }
        }
    }

    /// The "Move up / Move down" accessibility actions of a row (VoiceOver cannot drag).
    private func move(_ surah: Int, by delta: Int, in ui: PlanUi) {
        guard let from = ui.draft.queue.firstIndex(where: { $0.intValue == surah }) else { return }
        let to = from + delta
        guard to >= 0, to < ui.draft.queue.count else { return }
        QhHaptics.shared.perform(.step)
        withQhAnimation { model.vm.move(from: Int32(from), to: Int32(to)) }
    }
}

// MARK: - List row styling

private extension View {
    /// One row of the plan list: transparent, no separator, the screen inset, [top] points of air above.
    func plan(top: CGFloat) -> some View {
        listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: top, leading: QhSpacing.screen, bottom: 0, trailing: QhSpacing.screen))
    }
}

/// An overline and its content, as one row of the list, entering with the stagger of the screen.
private struct PlanSectionRow<Content: View>: View {
    let title: String
    let index: Int
    let stagger: StaggerState
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            QhSectionHeader(title).padding(.horizontal, QhSpacing.x1)
            content()
        }
        .staggeredEnter(index: index, state: stagger)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
        .listRowInsets(EdgeInsets(top: 0, leading: QhSpacing.screen, bottom: 0, trailing: QhSpacing.screen))
    }
}

/// The button that saves the plan, pinned to the bottom over a soft fade of the page colour.
private struct SaveBar: View {
    @Environment(\.qh) private var qh
    let label: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        QhButton(label, fullWidth: true, action: action)
            .disabled(!enabled)
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x6)
            .padding(.bottom, QhSpacing.x3)
            .background(
                LinearGradient(stops: [.init(color: qh.colors.bg.opacity(0), location: 0), .init(color: qh.colors.bg.opacity(0.96), location: 0.4),
                                       .init(color: qh.colors.bg, location: 1)], startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea(edges: .bottom)
            )
    }
}
