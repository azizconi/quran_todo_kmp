import ComposeApp
import SwiftUI

/// The "Quran" tab: large title with the settings gear, the "Continue" hero, Recent, the Surahs / Juz switch, a native `.searchable`
/// field with the status segments, 114 surahs (or the 30 juz). Port of `QuranTab.kt`. "In translation" search is S5.
struct QuranTabView: View {
    let home: HomeModel
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    @State private var query = ""
    @State private var filter = 0
    @State private var listTab = 0
    @State private var sheetSurah: Int?
    @State private var celebration = QuranCelebration()
    @State private var pendingChange: PendingStatusChange?
    @State private var stagger = StaggerState(screen: "quranTab")

    var body: some View {
        let ui = home.ui.value
        QuranList(
            home: home, ui: ui, query: $query, filter: $filter, listTab: $listTab, sheetSurah: $sheetSurah,
            celebration: celebration, stagger: stagger,
            requestStatus: requestStatus
        )
        .navigationTitle(copy.home.title)
        .navigationBarTitleDisplayMode(.large)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: copy.home.searchPlaceholder)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { router.push(.settings) } label: { Image(systemName: "gearshape") }
                    .accessibilityLabel(copy.home.settings)
            }
        }
        .qhNavigationStyle()
        .miniPlayerInset(home)
        .onChange(of: query) { _, value in home.setQuery(value) }
        .onChange(of: filter) { _, value in home.setFilter(SurahFilter.allCases[value]) }
        .onChange(of: ui.phase) { _, phase in if phase == .content { stagger.markPlayed() } }
        .onAppear { if ui.phase == .content { stagger.markPlayed() } }
        .sheet(item: sheetBinding) { item in
            SurahSheetView(home: home, row: item.row, onPicked: { target in apply(item.row, target) })
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
                .presentationBackground(qh.colors.surface)
        }
        .confirmationDialog(
            pendingChange.map { copy.home.forgetTitle(n: Int32($0.lost)) } ?? "",
            isPresented: Binding(get: { pendingChange != nil }, set: { if !$0 { pendingChange = nil } }),
            titleVisibility: .visible, presenting: pendingChange
        ) { change in
            Button(copy.home.forgetConfirm, role: .destructive) { apply(change.row, change.target) }
            Button(copy.home.cancel, role: .cancel) {}
        } message: { _ in
            Text(copy.home.forgetBody)
        }
    }

    private var sheetBinding: Binding<SheetItem?> {
        Binding(
            get: { sheetSurah.flatMap { n in home.ui.value.rows.first { Int($0.number) == n }.map(SheetItem.init) } },
            set: { if $0 == nil { sheetSurah = nil } }
        )
    }

    /// Asks first when memorized ayahs would leave the repetitions; else applies at once.
    private func requestStatus(_ row: SurahRowUi, _ target: SurahTarget) {
        let current = SurahHifzStatus.from(row.status)
        let covered = Int((row.learnedFraction * Float(row.ayahCount)).rounded())
        let lost = Int(SurahStatusChange.shared.lostAyahs(current: current, coveredAyahs: Int32(covered), target: target))
        if lost > 0 { pendingChange = PendingStatusChange(row: row, target: target, lost: lost) } else { apply(row, target) }
    }

    private func apply(_ row: SurahRowUi, _ target: SurahTarget) {
        home.applyStatus(surah: Int(row.number), target: target)
        if target == .known {
            QhHaptics.shared.perform(.success)
            celebration = QuranCelebration(surah: Int(row.number), token: celebration.token + 1)
        }
        pendingChange = nil
    }
}

struct QuranCelebration: Equatable {
    var surah = 0
    var token = 0
}

private struct SheetItem: Identifiable {
    let row: SurahRowUi
    var id: Int32 { row.number }
}

struct PendingStatusChange {
    let row: SurahRowUi
    let target: SurahTarget
    let lost: Int
}

extension SurahHifzStatus {
    /// The hifz status behind a row's todo status (null = not started).
    static func from(_ status: SurahTodoStatus?) -> SurahHifzStatus? {
        switch status {
        case .some(.learning): return .learning
        case .some(.learned): return .learned
        default: return nil
        }
    }
}

// MARK: - The list

private struct QuranList: View {
    let home: HomeModel
    let ui: HomeUi
    @Binding var query: String
    @Binding var filter: Int
    @Binding var listTab: Int
    @Binding var sheetSurah: Int?
    let celebration: QuranCelebration
    let stagger: StaggerState
    let requestStatus: (SurahRowUi, SurahTarget) -> Void

    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.isSearching) private var isSearching

    private var searchMode: Bool { isSearching || !query.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        List {
            switch ui.phase {
            case .loading:
                ForEach(0..<8, id: \.self) { _ in SkeletonRow().plainRow() }
            case .error:
                QhEmptyState(title: copy.home.loadErrorTitle, message: copy.home.loadErrorBody,
                             actionLabel: copy.home.retry) { home.retry() }
                    .plainRow()
            case .content:
                content
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.immediately)
        .background(qh.colors.bg)
        .environment(\.defaultMinListRowHeight, 1)
        .qhAnimation(QhMotion.gentle, value: searchMode)
        .qhAnimation(QhMotion.gentle, value: listTab)
        .qhAnimation(QhMotion.gentle, value: filter)
    }

    @ViewBuilder private var content: some View {
        let mushaf = copy.mushaf
        if !searchMode {
            if let hero = ui.continueItem {
                HeroCardView(item: hero, playing: isPlaying(hero.surah)) {
                    router.openReader(Int(hero.surah), ayah: hero.isFresh ? nil : Int(hero.ayah), origin: "hero")
                } onListen: {
                    home.listen(surah: Int(hero.surah), readAyah: Int(hero.ayah))
                }
                .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x3).padding(.bottom, QhSpacing.x4)
                .staggeredEnter(index: 0, state: stagger)
                .plainRow()
            }
            if !ui.recent.isEmpty {
                RecentSection(recent: ui.recent) { surah in
                    router.openReader(Int(surah), origin: "recent")
                } onLongPress: { surah in
                    sheetSurah = Int(surah)
                }
                .padding(.bottom, QhSpacing.x4)
                .staggeredEnter(index: 1, state: stagger)
                .plainRow()
            }
            QhSegmentedControl(options: [mushaf.tabSurahs, mushaf.tabJuz], selection: $listTab)
                .padding(.horizontal, QhSpacing.screen)
                .padding(.bottom, QhSpacing.x3)
                .staggeredEnter(index: 2, state: stagger)
                .plainRow()
            if listTab == 0 {
                filterSegments.plainRow()
            }
        } else {
            QhSegmentedControl(options: [mushaf.tabSurahs, mushaf.tabJuz], selection: $listTab)
                .padding(.horizontal, QhSpacing.screen)
                .padding(.vertical, QhSpacing.x2)
                .plainRow()
        }

        if listTab == 0 { surahRows } else { juzRows }
    }

    private var filterSegments: some View {
        QhSegmentedControl(options: [copy.home.filterAll, copy.home.filterLearning, copy.home.filterLearned], selection: $filter)
            .padding(.horizontal, QhSpacing.screen)
            .padding(.bottom, QhSpacing.x2)
    }

    private func isPlaying(_ surah: Int32) -> Bool {
        guard let player = home.player.value else { return false }
        return player.surah == surah && player.isPlaying
    }

    @ViewBuilder private var surahRows: some View {
        let listing = ui.listing
        if let smart = listing.smartResult {
            SmartResultCard(result: smart) {
                router.openReader(Int(smart.row.number), ayah: Int(smart.ayah), origin: "search")
            }
            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x1)
            .plainRow()
        }
        if listing.rows.isEmpty && listing.smartResult == nil {
            ListEmptyState(searching: listing.isSearching, filter: listing.filter).plainRow()
        }
        ForEach(listing.rows, id: \.number) { row in
            SurahRowView(row: row, celebrate: celebration.surah == Int(row.number) ? celebration.token : 0) {
                router.openReader(Int(row.number), origin: listing.isSearching ? "search" : "list")
            } onLongPress: {
                sheetSurah = Int(row.number)
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.visible, edges: .bottom)
            .listRowSeparatorTint(qh.colors.hairline)
            .alignmentGuide(.listRowSeparatorLeading) { _ in QhSpacing.screen + 40 + 14 }
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button { home.togglePin(Int(row.number)) } label: {
                    Label(row.isPinned ? copy.home.unpin : copy.home.pin, systemImage: row.isPinned ? "pin.slash" : "pin")
                }
                .tint(qh.colors.gold)
                if SurahStatusChange.shared.targetOf(status: SurahHifzStatus.from(row.status)) != .known {
                    Button { requestStatus(row, .known) } label: { Label(copy.home.sheetKnown, systemImage: "checkmark.circle") }
                        .tint(qh.colors.learned)
                }
            }
            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                Button { home.listen(surah: Int(row.number), readAyah: 1) } label: {
                    Label(copy.home.listenSurah, systemImage: "headphones")
                }
                .tint(qh.colors.primary)
                if SurahStatusChange.shared.targetOf(status: SurahHifzStatus.from(row.status)) != .learning {
                    Button { requestStatus(row, .learning) } label: { Label(copy.home.sheetLearning, systemImage: "circle.lefthalf.filled") }
                        .tint(qh.colors.learning)
                }
            }
            .qhTransition(.opacity)
        }
    }

    @ViewBuilder private var juzRows: some View {
        let rows = ui.juzListing
        if rows.isEmpty {
            ListEmptyState(searching: true, filter: .all).plainRow()
        }
        ForEach(rows, id: \.juz) { row in
            JuzRowView(row: row) {
                router.openReader(Int(row.startSurah), ayah: Int(row.startAyah), origin: JuzRows.shared.ORIGIN)
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.visible, edges: .bottom)
            .listRowSeparatorTint(qh.colors.hairline)
            .alignmentGuide(.listRowSeparatorLeading) { _ in QhSpacing.screen + 40 + 14 }
        }
    }
}

extension View {
    /// A list row with no insets, no separator and no background: the row draws everything itself.
    func plainRow() -> some View {
        listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}
