import ComposeApp
import SwiftUI

extension MushafContent {
    /// Reactions to state: page settled, sound, check, sheets.
    func reacted<V: View>(_ view: V, page: Int, playing: AyahRef?) -> some View {
        let check = model.check.value
        return view
            .onChange(of: current) { _, now in
                guard let now else { return }
                model.vm.onPageSettled(page: Int32(now))
                Task { await MushafPageService.shared.prefetch([now + 1, now - 1, now + 2, now - 2]) }
            }
            .onChange(of: playing) { _, now in followSound(now) }
            .onChange(of: check?.revealed) { _, _ in QhHaptics.shared.perform(.tick) }
            .onChange(of: check?.finished) { _, finished in
                if finished == true {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.48) { if model.check.value?.finished == true { gradeOpen = true } }
                }
            }
            .onChange(of: model.anything.value.boolValue) { _, on in IdleLock.set(on || ReadingPrefs.shared.keepScreenOn, owner: model) }
            .onDisappear { IdleLock.set(false, owner: model) }
            .task(id: flash) {
                guard flash != nil else { return }
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                withQhAnimation(QhMotion.gentle) { flash = nil }
            }
            .task { model.vm.onPageSettled(page: Int32(initial)) }
            .sheet(item: Binding(get: { sheet.map { MushafSheetKey(ref: $0) } }, set: { if $0 == nil { sheet = nil; model.vm.closeSheet() } })) { key in
                MushafAyahSheet(model: model, ref: key.ref, onCheck: { startCheck(from: key.ref) })
            }
            .sheet(isPresented: $gotoSheet) {
                GoToSheetContent(surahs: goToRows(), currentSurah: Int(MushafPages.shared.firstAyahOf(page: Int32(page)).surah)) { surah, ayah in
                    let target = Int(MushafPages.shared.pageOf(surah: Int32(surah), ayah: Int32(ayah)))
                    withQhAnimation(QhMotion.gentle) { current = target; selected = AyahRef(surah, ayah); flash = AyahRef(surah, ayah) }
                }
            }
            .sheet(isPresented: Binding(get: { model.exitPrompt.value.boolValue }, set: { if !$0 { model.vm.dismissExitPrompt() } })) {
                MushafLeaveSheet(model: model)
            }
            .sheet(isPresented: $gradeOpen, onDismiss: { if model.check.value?.finished == true { model.vm.exitCheck() } }) {
                if let check = model.check.value { MushafGradeSheet(model: model, check: check, title: gradeTitle(check)) }
            }
    }

    func pager(page: Int, playing: AyahRef?) -> some View {
        GeometryReader { proxy in
            let topInset = max(proxy.safeAreaInsets.top, 100)
            let reserve: CGFloat = 64 + (model.check.value != nil || model.miniPlayer.value != nil ? 88 : 0)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 0) {
                    ForEach(1...total, id: \.self) { n in
                        MushafPageView(
                            page: n, model: model, selected: selected, playing: playing, flash: flash,
                            looks: model.looks(page: n), top: topInset, bottom: reserve,
                            active: abs(n - page) <= 1,
                            onTap: { ref in tapAyah(ref) },
                            onLong: { ref in sheet = ref; model.vm.openSheet(key: ref.kotlin) },
                            onBlank: { withQhAnimation(QhMotion.gentle) { chrome.toggle() } },
                            onOpenText: {
                                let first = MushafPages.shared.firstAyahOf(page: Int32(n))
                                router.replaceTop(with: .reader(Int(first.surah), ayah: Int(first.ayah), origin: "pages"))
                            }
                        )
                        .containerRelativeFrame(.horizontal)
                        .id(n)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $current, anchor: .center)
            .environment(\.layoutDirection, .rightToLeft)
            .simultaneousGesture(DragGesture(minimumDistance: 12).onChanged { _ in lastManualTurn = Date() })
            .ignoresSafeArea()
        }
    }

    func tapAyah(_ ref: AyahRef) {
        QhHaptics.shared.perform(.tick)
        withQhAnimation(QhMotion.gentle) { selected = (selected == ref) ? nil : ref }
    }

    /// The page follows the sound; not within five seconds of a manual turn.
    func followSound(_ playing: AyahRef?) {
        guard let playing, model.check.value == nil, Date().timeIntervalSince(lastManualTurn) > 5 else { return }
        let target = Int(MushafPages.shared.pageOf(surah: Int32(playing.surah), ayah: Int32(playing.ayah)))
        if target != (current ?? initial) { withQhAnimation(.smooth(duration: 0.4)) { current = target } }
    }

    func startCheck(from ref: AyahRef?) {
        let page = current ?? initial
        Task {
            guard let data = await MushafPageService.shared.load(page) else { return }
            model.vm.closeSheet()
            sheet = nil
            model.vm.startCheck(page: data.page, from: (ref ?? selected)?.kotlin)
        }
    }

    func listen(page: Int) {
        Task {
            let data = await MushafPageService.shared.load(page)
            model.vm.playPage(page: data?.page, from: selected?.kotlin)
        }
    }

    func goToRows() -> [GoToSurahRow] {
        (1...114).map { n in GoToSurahRow(number: n, name: model.name(n), alternatives: [], arabic: model.arabic(n)) }
    }

    func gradeTitle(_ check: PageCheck) -> String {
        "\(model.name(Int(check.items.first?.key.surah ?? 1))) \u{00B7} \(copy.pages.pageOf(page: check.page))"
    }
}
