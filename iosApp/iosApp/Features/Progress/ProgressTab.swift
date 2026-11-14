import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `ProgressViewModel` of the Progress tab with its state as an observable value.
@MainActor
@Observable
final class ProgressModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.progress() }
    let ui: FlowState<ProgressUi>
    var vm: ProgressViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        holder.vm.setLanguage(value: IosUi.shared.language.value)
    }
}

/// The root of the Progress tab (`ProgressScreen.kt`): the big ring, then four blocks (the plan and the streak, the week, the hard
/// places with the way to the drill, the Quran map with the juz, collapsed) and a row for the detailed analytics and the teacher's
/// report. With nothing learned yet it invites to make a plan instead. A large title with the share action; the sections stagger in.
struct ProgressTabRoot: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = ProgressModel()
    @State private var stagger = StaggerState(screen: "progress")
    @State private var reportOpen = false

    var body: some View {
        let ui = model.ui.value
        let p = copy.progress
        QhLargeTitleScroll(title: p.title) {
            Color.clear.frame(width: 8, height: 1)
        } actions: {
            if ui.phase == .content {
                QhIconButton(icon: "square.and.arrow.up", label: p.reportAction, filled: false) { reportOpen = true }
            }
        } content: {
            VStack(spacing: QhSpacing.section) {
                switch ui.phase {
                case .loading: loading
                case .empty: empty(ui)
                default: content(ui)
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x8)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { model.vm.onResume() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.vm.onResume() } }
        .onChange(of: copy.language) { _, language in model.vm.setLanguage(value: language) }
        .onChange(of: ui.phase) { _, phase in if phase != .loading { stagger.markPlayed() } }
        .qhSheet(isPresented: $reportOpen, detents: [.large]) {
            ReportSheet(model: model, today: Int(ui.today)) { reportOpen = false }
        }
    }

    private var loading: some View {
        VStack(spacing: QhSpacing.section) {
            Skeleton(shape: qh.shapes.lgShape).frame(height: 268)
            HStack(spacing: QhSpacing.x3) {
                Skeleton(shape: qh.shapes.lgShape).frame(height: 124)
                Skeleton(shape: qh.shapes.lgShape).frame(height: 124)
            }
            Skeleton(shape: qh.shapes.lgShape).frame(height: 180)
        }
    }

    /// Nothing learned yet: the empty ring is the picture, with an invitation to make a plan (or to open the one that exists).
    private func empty(_ ui: ProgressUi) -> some View {
        let p = copy.progress
        let action = ui.plan.status == .noPlan ? p.emptyAction : copy.plan.title
        return VStack(spacing: QhSpacing.x5) {
            BigRing(hero: ui.hero)
            Text(p.emptyTitle).qhText({ $0.title1 }).multilineTextAlignment(.center)
            Text(p.emptyBody).qhText({ $0.body }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
            QhButton(action) { router.push(.plan) }
        }
        .frame(maxWidth: .infinity, minHeight: 440)
        .staggeredEnter(index: 0, state: stagger)
    }

    @ViewBuilder
    private func content(_ ui: ProgressUi) -> some View {
        RingHero(hero: ui.hero)
        // The ring is there at once, the rest follows. Four blocks.
        ForEach(Array(ProgressBuilder.shared.blocks(ui: ui).enumerated()), id: \.offset) { index, block in
            Group {
                switch block {
                case .planAndStreak:
                    PlanSection(line: ui.plan, streakDays: Int(ui.tiles.streakDays), today: ui.today) { router.push(.plan) }
                case .week:
                    ActivitySection(activity: ProgressBuilder.shared.week(activity: ui.activity))
                case .hardPlaces:
                    HardSpotsSection(spots: ui.hard,
                                     onOpenAyah: { surah, ayah in router.openReader(surah, ayah: ayah, origin: "map", in: .progress) },
                                     onTrain: { router.push(.hardDrill) })
                default:
                    QuranMapBlock(cells: ui.cells, juz: ui.juz,
                                  onOpenSurah: { router.openReader($0, origin: "map", in: .progress) },
                                  onOpenJuz: { juz in
                                      router.openReader(Int(juz.startSurah), ayah: juz.startAyah > 1 ? Int(juz.startAyah) : nil, origin: "map", in: .progress)
                                  })
                }
            }
            .staggeredEnter(index: index + 1, state: stagger)
        }
        ReportRows(onDetails: { router.push(.progressDetails) }, onShare: { reportOpen = true })
            .staggeredEnter(index: 5, state: stagger)
    }
}

/// The card at the bottom of the page: "In detail" (the analytics screen) and the report for the teacher.
private struct ReportRows: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let onDetails: () -> Void
    let onShare: () -> Void

    var body: some View {
        let a = copy.analyticsCopy
        QhCard(padding: 4) {
            VStack(spacing: 0) {
                QhNavRow(title: a.detailsTitle, description: a.detailsBody, icon: "chart.bar", iconTint: qh.colors.primary, action: onDetails)
                QhRowDivider()
                QhNavRow(title: copy.progress.reportAction, description: copy.progress.reportRowBody, icon: "square.and.arrow.up",
                         iconTint: qh.colors.primary, action: onShare)
            }
        }
    }
}
