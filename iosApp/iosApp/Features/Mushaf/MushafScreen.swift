import ComposeApp
import SwiftUI
import UIKit

/// The pages of the mushaf (S5.3): a horizontal pager of 604 pages that turns right to left, each page its own font fitted to the block,
/// tap an ayah to mark it, long press for its sheet, "Check yourself" on a page, the mini player at the bottom, the page follows the sound.
struct MushafScreen: View {
    let route: Route
    @State private var box = LazyModelBox<MushafModel>()
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    private var model: MushafModel {
        if let made = box.value { return made }
        let made = MushafModel(route: route)
        box.value = made
        return made
    }

    var body: some View {
        MushafContent(model: model)
            .background(qh.colors.readerBg.ignoresSafeArea())
            .toolbar(.hidden, for: .tabBar)
            .navigationBarTitleDisplayMode(.inline)
            .task(id: copy.language.name) { model.vm.onLanguage(next: copy.language) }
            .onDisappear { model.vm.flushReadingPosition() }
    }
}

struct MushafSheetKey: Identifiable {
    let ref: AyahRef
    var id: AyahRef { ref }
}

struct MushafContent: View {
    let model: MushafModel
    @Environment(\.qh) var qh
    @Environment(\.qhReduceMotion) var reduced
    @Environment(Copy.self) var copy
    @Environment(Router.self) var router

    @State var current: Int?
    @State var selected: AyahRef?
    @State var flash: AyahRef?
    @State var chrome = true
    @State var sheet: AyahRef?
    @State var gradeOpen = false
    @State var lastManualTurn = Date.distantPast
    @State var gotoSheet = false

    let total = 604
    let initial: Int

    init(model: MushafModel) {
        self.model = model
        initial = Int(model.vm.initialPage)
        _current = State(initialValue: Int(model.vm.initialPage))
        _flash = State(initialValue: model.vm.initialFlash.map { AyahRef($0) })
    }

    var body: some View {
        let page = current ?? initial
        let playing = model.playing.value.map { AyahRef($0) }
        return reacted(base(page: page, playing: playing), page: page, playing: playing)
    }

    @ViewBuilder func base(page: Int, playing: AyahRef?) -> some View {
        let mini = model.miniPlayer.value
        let check = model.check.value
        ZStack(alignment: .bottom) {
            pager(page: page, playing: playing)
            VStack(spacing: QhSpacing.x2) {
                if let check {
                    CheckPanel(model: model, check: check).transition(.move(edge: .bottom).combined(with: .opacity))
                } else if let mini {
                    MiniPlayerPill(player: mini, progress: CGFloat(truncating: model.progress.value),
                                   onToggle: { model.vm.togglePlayback() }, onStop: { model.vm.stopPlayback() })
                        .padding(.horizontal, 12)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .padding(.bottom, 6)
            .animation(reduced ? nil : QhMotion.bounds, value: mini != nil)
            .animation(reduced ? nil : QhMotion.snappy, value: check != nil)
        }
        .toolbar { toolbar(page: page) }
        .toolbar(chrome && check == nil ? .visible : .hidden, for: .navigationBar)
        .navigationBarBackButtonHidden(check != nil)
        .toolbarBackground(qh.colors.readerBg, for: .navigationBar)
        .toolbarColorScheme(qh.isDark ? .dark : .light, for: .navigationBar)
        .navigationTitle("")
        .animation(reduced ? nil : QhMotion.gentle, value: chrome)
        .environment(\.layoutDirection, .leftToRight)
    }
}
