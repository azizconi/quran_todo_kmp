import ComposeApp
import SwiftUI

/// Holds the model of a screen so it is made once, on the first `body`, and not on every init of the (value type) view.
final class LazyModelBox<Model> {
    var value: Model?
}

// MARK: - Frames of the rows (reading line, collapse, pinned row)

struct RowFrame: Equatable {
    let index: Int   // 0 header, 1...n ayahs, n + 1 the end card
    let minY: CGFloat
    let height: CGFloat
}

struct RowFramesKey: PreferenceKey {
    static var defaultValue: [RowFrame] = []
    static func reduce(value: inout [RowFrame], nextValue: () -> [RowFrame]) { value.append(contentsOf: nextValue()) }
}

extension View {
    func trackRow(_ index: Int, space: String) -> some View {
        background(GeometryReader { g in
            let f = g.frame(in: .named(space))
            Color.clear.preference(key: RowFramesKey.self, value: [RowFrame(index: index, minY: f.minY, height: f.height)])
        })
    }
}

/// `ayahOnReadingLine` of Kotlin (ReadingLine.kt) without the allocation of Kotlin objects on every scroll frame: the ayah crossing a
/// line a fifth of the way down the visible area; at the end of the list the last ayah on screen. -2 not measured, -1 header, else index.
func ayahOnReadingLine(_ frames: [RowFrame], top: CGFloat, visibleHeight: CGFloat, atEnd: Bool, total: Int) -> Int {
    let items = frames.sorted { $0.index < $1.index }
    guard !items.isEmpty, visibleHeight > 0 else { return ReaderViewModel.companion.NotMeasured.int }
    let item: RowFrame
    if atEnd {
        item = items.last { $0.index >= 1 && $0.index <= total && $0.minY - top < visibleHeight } ?? items[items.count - 1]
    } else {
        let line = visibleHeight / 5
        item = items.first { ($0.minY - top) + $0.height > line } ?? items[items.count - 1]
    }
    if item.index == 0 { return ReaderViewModel.companion.HeaderOnTop.int }
    return item.index <= total ? item.index - 1 : total - 1
}

// MARK: - Screen

/// The text reader (S5.1): the surah as a list (header with the cartouche, Bismillah, ayahs, the next surah), the native bar that takes
/// over when the header scrolls away, and the player dock. Audio belongs to the shared `PlaybackController`, so leaving the screen
/// never interrupts it.
struct ReaderScreen: View {
    let route: Route
    @State private var box = LazyModelBox<ReaderModel>()
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.scenePhase) private var scenePhase

    private var model: ReaderModel {
        if let made = box.value { return made }
        let made = ReaderModel(route: route)
        box.value = made
        return made
    }

    var body: some View {
        let model = model
        Group {
            if model.surah == nil {
                ReaderLoading(model: model)
            } else {
                ReaderContent(model: model)
            }
        }
        .background(qh.colors.readerBg.ignoresSafeArea())
        .toolbar(.hidden, for: .tabBar)
        .toolbarBackground(qh.colors.readerBg, for: .navigationBar)
        .toolbarColorScheme(qh.isDark ? .dark : .light, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: copy.language.name) { model.vm.onLanguage(language: copy.language) }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.vm.reloadPrefs(); model.vm.ensureTranslations() }
        }
        .onDisappear { model.vm.flushReadingPosition() }
    }
}

private struct ReaderLoading: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel

    var body: some View {
        let r = copy.reader
        Group {
            if model.loadFailed.value.boolValue {
                QhEmptyState(title: r.notLoaded, actionLabel: r.retry) { model.vm.retryLoading() }
                    .padding(.top, QhSpacing.x12)
            } else {
                VStack(spacing: QhSpacing.x4) {
                    Skeleton(shape: qh.shapes.lgShape).frame(height: 80)
                    HStack { Skeleton().frame(width: 160, height: 20); Spacer() }
                    Skeleton().frame(height: 60)
                    Skeleton().frame(height: 60)
                    HStack { Skeleton().frame(height: 60).frame(maxWidth: .infinity); Spacer().frame(width: 60) }
                }
                .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x6)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .navigationTitle("")
    }
}

// MARK: - Content

private struct ReaderContent: View {
    let model: ReaderModel

    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @Environment(AppEnvironment.self) private var env
    @Environment(\.scenePhase) private var scenePhase

    @State private var sheet: ReaderSheet?
    @State private var actionOpen: Int?
    @State private var following = true
    @State private var flash: Int?
    @State private var celebration = 0
    @State private var reading = ReaderViewModel.companion.NotMeasured.int
    @State private var collapsed: CGFloat = 0
    @State private var frames: [RowFrame] = []
    @State private var viewport = CGSize.zero
    @State private var insets = EdgeInsets()
    @State private var enter: CGFloat = 0
    @State private var meaning: (ayah: Int, word: Int)?
    @State private var sheetAfter: ReaderSheet?
    @State private var recall: ReaderRecall?
    @State private var scrollTarget: Int?

    private let space = "reader-scroll"
    private let total: Int
    private let initialIndex: Int

    init(model: ReaderModel) {
        self.model = model
        total = model.ayahs.count
        initialIndex = Int(model.vm.initialAyahIndex)
        _flash = State(initialValue: model.vm.initialFlashIndex.map { Int(truncating: $0) })
    }

    // MARK: Body

    var body: some View {
        let prefs = ReadingPrefs.shared
        let player = model.player.value
        let playingHere = player.active && (player.isPlaying || player.isPreparing)
        let anything = model.anythingPlaying.value.boolValue
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ReaderHeaderView(model: model, celebration: celebration, enter: enter,
                                     onStatus: { sheet = .surah(status: true) }, onTitle: { sheet = .goTo },
                                     offerVisible: offerVisible, onOffer: answerOffer)
                        .id(-1).trackRow(0, space: space)
                    ForEach(0..<total, id: \.self) { index in
                        row(index).id(index).trackRow(index + 1, space: space)
                    }
                    NextSurahCardView(next: model.nextSurah(copy: copy),
                                      onOpen: { router.push(.reader(model.number + 1, origin: "next")) },
                                      onStartOver: { router.push(.reader(1, origin: "next")) })
                        .opacity(enter).trackRow(total + 1, space: space).padding(.bottom, QhSpacing.x4)
                }
            }
            .coordinateSpace(name: space)
            .scrollDismissesKeyboard(.interactively)
            .background(GeometryReader { g in Color.clear.onAppear { updateGeometry(g) }.onChange(of: g.size) { _, _ in updateGeometry(g) } })
            .onPreferenceChange(RowFramesKey.self) { handle($0) }
            .simultaneousGesture(DragGesture(minimumDistance: 10).onChanged { _ in if following { following = false } })
            .overlay(alignment: .bottom) { floaters(proxy: proxy, player: player, playingHere: playingHere) }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                PlayerDockBar(model: model, expandable: recall == nil, onPlay: { following = true; model.vm.togglePlayback() },
                              onExpand: { sheet = .player })
            }
            .onAppear { start(proxy) }
            .onChange(of: followTarget(playingHere)) { _, target in
                guard let target else { return }
                bring(target, proxy: proxy, anchor: 0.3)
            }
            .onChange(of: player.index) { _, now in
                if !player.active, player.selected { bringIfHidden(Int(now), proxy: proxy) }
            }
            .onChange(of: actionOpen) { _, open in
                guard let open else { return }
                DispatchQueue.main.asyncAfter(deadline: .now() + QhMotion.short + 0.05) { revealRow(open, proxy: proxy) }
            }
            .onChange(of: scrollTarget) { _, target in
                guard let target else { return }
                scrollTarget = nil
                bring(target, proxy: proxy, anchor: 0.3)
            }
            .onChange(of: reading) { _, index in model.vm.onVisibleAyah(index: Int32(index)) }
            .onChange(of: prefs.wordMeanings) { _, on in if !on { meaning = nil } }
        }
        .toolbar { toolbar }
        .navigationTitle("")
        .sheet(item: $sheet, onDismiss: presentQueuedSheet) { sheetContent($0) }
        .onChange(of: model.statuses.value.count) { _, _ in }
        .onChange(of: anything || prefs.keepScreenOn) { _, on in IdleLock.set(on, owner: model) }
        .onChange(of: player.hasError) { _, error in if error { QhHaptics.shared.perform(.reject) } }
        .task {
            IdleLock.set(anything || prefs.keepScreenOn, owner: model)
            model.bindEvents(toaster: env.toaster, copy: copy) { celebration += 1 }
            model.vm.onOpened()
            if prefs.tajweed { TajweedService.shared.prepare(surah: model.number) }
        }
        .onChange(of: prefs.tajweed) { _, on in if on { TajweedService.shared.prepare(surah: model.number) } }
        .onDisappear { IdleLock.set(false, owner: model) }
        .task(id: flash) {
            guard flash != nil else { return }
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            withQhAnimation(QhMotion.gentle) { flash = nil }
        }
        .environment(\.layoutDirection, .leftToRight)
    }

    // MARK: Rows

    private var offerVisible: Bool {
        !model.prefs.value.showTranslation && !IosUi.shared.uiPrefs.translationOfferAnswered.value.boolValue && offerState
    }
    @State private var offerState = true

    private func answerOffer(_ yes: Bool) {
        IosUi.shared.uiPrefs.setTranslationOfferAnswered(value: true)
        AppTelemetry.shared.logEvent(name: "translation_offer_answer", params: ["answer": yes ? "yes" : "no"])
        withQhAnimation(QhMotion.gentle) { offerState = false }
        if yes { model.vm.setShowTranslation(show: true) }
    }

    private func row(_ index: Int) -> some View {
        let prefs = ReadingPrefs.shared
        let ayah = model.ayahs[index]
        let player = model.player.value
        let marks = model.marks(at: index)
        let follow = recall == nil && Int(truncating: player.followIndex ?? -1) == index
        let marked = recall != nil ? false : (model.highlightIndex() == index || flash == index)
        let hasLine = marks?.hasLine == true
        let nextHasLine = (index + 1 < total) ? (model.marks(at: index + 1)?.hasLine == true) : false
        let line: [String?]? = hasLine ? { let l = copy.mushaf.markLine(marks: marks!); return [l.start, l.end] }() : nil
        let practice = model.practice.value
        let range: ClosedRange<Int>? = practice.rangeEnabled ? (Int(practice.startAyah) - 1)...(Int(practice.endAyah) - 1) : nil
        let data = AyahRowData(
            index: index, number: Int(ayah.numberInSurah),
            ayah: model.display(at: index, tajweed: prefs.tajweed) ?? model.displays[index],
            translation: prefs.shows(model) ? model.translation(ayah) : nil,
            status: rdStatus(model.statusOf(ayah)), weak: model.isWeak(ayah), hasNote: model.hasNote(ayah),
            marked: marked, range: rangePosition(index, range),
            markLine: line, markSpoken: hasLine ? copy.mushaf.markLine(marks: marks!).spoken : "",
            showDivider: index != total - 1 && !nextHasLine,
            actionsOpen: recall == nil && actionOpen == index,
            translationMissing: index == 0 && model.translationMissing.value.boolValue,
            follow: follow, playing: player.active && player.isPlaying,
            meaningsOn: prefs.wordMeanings && recall == nil, meaningWord: meaning?.ayah == index ? meaning?.word : nil,
            tajweed: prefs.tajweed, face: prefs.face, size: prefs.size,
            similar: prefs.similarMarks && recall == nil && SimilarService.shared.hasSimilar(surah: model.number, ayah: Int(ayah.numberInSurah))
        )
        let callbacks = AyahRowCallbacks(
            select: { select($0) },
            more: { sheet = .ayah($0) },
            listen: { index in following = true; actionOpen = nil; model.vm.listenFrom(index: Int32(index)) },
            repeatAyah: { index in following = true; actionOpen = nil; model.vm.repeatAyah(index: Int32(index)) },
            retryTranslation: { model.vm.retryTranslation() },
            wordTap: { index, word, startMs in wordTap(index, word, startMs) }
        )
        return AyahRowView(data: data, sajdaValue: marks?.sajda, callbacks: callbacks)
            .equatable()
            .opacity(enter)
            .offset(y: (1 - enter) * 24)
    }

    private func select(_ index: Int) {
        QhHaptics.shared.perform(.tick)
        following = true
        meaning = nil
        withQhAnimation(QhMotion.gentle) { actionOpen = (actionOpen == index) ? nil : index }
        model.vm.selectAyah(index: Int32(index))
    }

    private func wordTap(_ index: Int, _ word: Int, _ startMs: Int?) {
        if ReadingPrefs.shared.wordMeanings {
            QhHaptics.shared.perform(.tick)
            withQhAnimation(QhMotion.snappy) { meaning = (meaning?.ayah == index && meaning?.word == word) ? nil : (index, word) }
        } else if let startMs {
            following = true
            model.vm.onWordTap(index: Int32(index), startMs: Int32(startMs))
        }
    }

    // MARK: Geometry and scrolling

    private func updateGeometry(_ g: GeometryProxy) {
        viewport = g.size
        insets = g.safeAreaInsets
    }

    private func handle(_ next: [RowFrame]) {
        frames = next
        let top = insets.top
        if let header = next.first(where: { $0.index == 0 }) {
            let scrolled = top - header.minY
            let value = min(max((scrolled - 72) / 60, 0), 1)
            if abs(value - collapsed) > 0.02 { collapsed = value }
        } else if collapsed != 1 { collapsed = 1 }
        let visible = viewport.height - insets.top - insets.bottom
        let end = next.first { $0.index == total + 1 }
        let atEnd = end.map { $0.minY + $0.height <= viewport.height - insets.bottom + 1 } ?? false
        let index = ayahOnReadingLine(next, top: top, visibleHeight: visible, atEnd: atEnd, total: total)
        if index != reading { reading = index }
    }

    private func start(_ proxy: ScrollViewProxy) {
        if initialIndex > 0 {
            DispatchQueue.main.async { proxy.scrollTo(initialIndex, anchor: .top) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { proxy.scrollTo(initialIndex, anchor: .top) }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + (initialIndex > 0 ? 0.2 : 0.12)) {
            withAnimation(reduced ? .easeOut(duration: 0.15) : QhMotion.enter(QhMotion.long)) { enter = 1 }
        }
    }

    private func followTarget(_ playingHere: Bool) -> Int? {
        following && playingHere && recall == nil ? Int(model.player.value.index) : nil
    }

    private func bring(_ index: Int, proxy: ScrollViewProxy, anchor: CGFloat) {
        guard total > 0 else { return }
        let i = min(max(index, 0), total - 1)
        if let f = frames.first(where: { $0.index == i + 1 }) {
            let wanted = insets.top + (viewport.height - insets.top - insets.bottom) * anchor
            if abs(f.minY - wanted) < 16 { return }
        }
        withQhAnimation(.smooth(duration: 0.45)) { proxy.scrollTo(i, anchor: UnitPoint(x: 0.5, y: anchor)) }
    }

    private func bringIfHidden(_ index: Int, proxy: ScrollViewProxy) {
        let bottom = viewport.height - insets.bottom
        if let f = frames.first(where: { $0.index == index + 1 }), f.minY >= insets.top, f.minY + f.height <= bottom { return }
        bring(index, proxy: proxy, anchor: 0.3)
    }

    /// Once the row of actions has opened, make sure it is not hidden behind the dock.
    private func revealRow(_ index: Int, proxy: ScrollViewProxy) {
        guard actionOpen == index, let f = frames.first(where: { $0.index == index + 1 }) else { return }
        let bottom = viewport.height - insets.bottom
        let visible = bottom - insets.top
        if f.minY + f.height > bottom, f.height < visible {
            withQhAnimation(.smooth(duration: 0.3)) { proxy.scrollTo(index, anchor: .bottom) }
        }
    }

    /// The open row sits below the dock (an ayah taller than the screen): the same row stands above the dock instead.
    private var pinnedActionIndex: Int? {
        guard recall == nil, let open = actionOpen, let f = frames.first(where: { $0.index == open + 1 }) else { return nil }
        let dockTop = viewport.height - insets.bottom + QhSpacing.x8 - 12
        return f.minY + f.height > dockTop ? open : nil
    }

    // MARK: Floating chips

    @ViewBuilder private func floaters(proxy: ScrollViewProxy, player: PlayerUi, playingHere: Bool) -> some View {
        let r = copy.reader
        let current = Int(player.index)
        let currentVisible = frames.contains { $0.index == current + 1 }
        let showJump = !following && playingHere && !currentVisible && recall == nil
        let pointsUp = frames.map(\.index).min().map { $0 > current + 1 } ?? false
        VStack(spacing: QhSpacing.x2) {
            if let pinned = pinnedActionIndex {
                AyahActionRow(onListen: { following = true; actionOpen = nil; model.vm.listenFrom(index: Int32(pinned)) },
                              onRepeat: { following = true; actionOpen = nil; model.vm.repeatAyah(index: Int32(pinned)) },
                              onMore: { sheet = .ayah(pinned) })
                    .padding(.horizontal, QhSpacing.x3).padding(.bottom, QhSpacing.x3)
                    .background(qh.colors.surface, in: qh.shapes.lgShape)
                    .qhSoftShadow()
                    .padding(.horizontal, QhSpacing.screen)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            if let elsewhere = player.elsewhereSurah {
                FloatingChip(icon: "headphones", label: r.playingElsewhere(surahName: model.displayName(Int(truncating: elsewhere), copy: copy))) {
                    if let dest = model.vm.routeToPlayingSurah(), let route = Route(dest: dest) { router.push(route) }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            if showJump {
                FloatingChip(icon: pointsUp ? "arrow.up" : "arrow.down", label: r.jumpToCurrent) {
                    following = true
                    bring(current, proxy: proxy, anchor: 0.3)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .padding(.bottom, QhSpacing.x3)
        .animation(reduced ? nil : QhMotion.snappy, value: showJump)
        .animation(reduced ? nil : QhMotion.snappy, value: player.elsewhereSurah?.intValue)
        .animation(reduced ? nil : QhMotion.snappy, value: pinnedActionIndex)
    }

    // MARK: Toolbar

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) { titleView }
        ToolbarItemGroup(placement: .topBarTrailing) {
            barButton("book", label: copy.pages.openAsPages) { openPages() }
            barButton("eye", label: copy.hifz.eyeAction) { startRecall(at: nil) }
            Button { sheet = .appearance } label: {
                Text("Aa").font(.custom("Manrope-Bold", size: 17)).foregroundStyle(qh.colors.ink).frame(minWidth: 32, minHeight: 44)
            }
            .accessibilityLabel(copy.reader.textSettings)
            barButton("ellipsis", label: copy.reader.more) { sheet = .surah(status: false) }
        }
    }

    private func barButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 17, weight: .medium)).foregroundStyle(qh.colors.ink).frame(minWidth: 32, minHeight: 44)
        }
        .accessibilityLabel(label)
    }

    private var titleView: some View {
        let index = max(reading, 0)
        let place = model.place(at: index)
        return VStack(spacing: 0) {
            Button { sheet = .goTo } label: {
                HStack(spacing: 6) {
                    Text(model.title(copy: copy)).qhText({ $0.title2 }).lineLimit(1)
                    HStack(spacing: 0) {
                        AnimatedCounter(value: index + 1, style: { $0.callout }, color: qh.colors.inkSecondary)
                        Text(" / \(total)").qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                }
            }
            .buttonStyle(.plain)
            if let place {
                Text(copy.mushaf.placeText(place: place)).qhText({ $0.label }, color: { $0.inkTertiary }).lineLimit(1)
            }
        }
        .opacity(collapsed)
        .animation(reduced ? nil : .easeOut(duration: 0.15), value: collapsed > 0.5)
        .accessibilityHidden(collapsed < 0.5)
    }

    // MARK: Sheets

    private func presentQueuedSheet() {
        if let next = sheetAfter { sheetAfter = nil; sheet = next }
    }

    @ViewBuilder private func sheetContent(_ kind: ReaderSheet) -> some View {
        switch kind {
        case .surah(let status):
            SurahSheetContent(surahName: model.title(copy: copy), status: model.status.value,
                              learnedAyahs: model.coverage.value.map { Int($0.coveredAyahs.count) } ?? 0, initialStatusPage: status,
                              onChoice: { model.vm.chooseSurah(target: $0) },
                              onGoTo: { sheet = .goTo })
        case .goTo:
            GoToSheetContent(surahs: goToSurahs(), currentSurah: model.number) { surah, ayah in goTo(surah, ayah) }
        case .ayah(let index):
            AyahSheetContent(model: model, index: index,
                             onListen: { following = true; model.vm.listenFrom(index: Int32(index)) },
                             onRepeat: { following = true; model.vm.repeatAyah(index: Int32(index)) },
                             onCheck: { startRecall(at: index) })
        case .appearance:
            AppearanceSheetContent(model: model, previewIndex: max(reading, 0))
        case .player:
            PlayerDockSheet(model: model) { following = true; model.vm.togglePlayback() }
        }
    }

    private func goToSurahs() -> [GoToSurahRow] {
        (1...114).map { n in
            let chapter = model.chapter(n)
            return GoToSurahRow(number: n, name: model.displayName(n, copy: copy),
                                alternatives: [chapter?.transliteration, chapter?.translated].compactMap { $0 }.filter { !$0.isEmpty },
                                arabic: chapter?.arabic ?? "")
        }
    }

    private func goTo(_ surah: Int, _ ayah: Int) {
        if surah == model.number {
            let index = min(max(ayah - 1, 0), total - 1)
            withQhAnimation(QhMotion.gentle) { flash = index }
            following = false
            scrollTarget = index
        } else {
            let dest = ReaderRouting.shared.goTo(surah: Int32(surah), ayah: Int32(ayah))
            if let route = Route(dest: dest) { router.replaceTop(with: route) }
        }
    }

    private func openPages() {
        IosUi.shared.uiPrefs.setReaderLayout(value: ReaderLayout.mushaf)
        let index = max(reading, 0)
        let ayah = model.ayahs.indices.contains(index) ? model.ayahs[index] : model.ayahs[0]
        router.replaceTop(with: .mushaf(page: Int(ayah.page), ayahKey: "\(model.number):\(ayah.numberInSurah)"))
    }

    private func startRecall(at index: Int?) {
        sheet = nil
        ReaderRecall.begin(model: model, start: index ?? max(reading, 0)) { recall = $0 }
    }
}

private extension ReadingPrefs {
    /// The translation shows under the ayahs when the switch of this surah is on.
    func shows(_ model: ReaderModel) -> Bool { model.prefs.value.showTranslation }
}
