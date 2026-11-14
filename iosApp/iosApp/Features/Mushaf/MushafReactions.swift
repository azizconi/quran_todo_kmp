import ComposeApp
import SwiftUI

/// The reactions and the sheets of the mushaf screen: page settled, the sound moves, the check ends, the ayah sheet, "Go to", leave, grade.
struct MushafReactions: ViewModifier {
    let content: MushafContent
    let page: Int
    let playing: AyahRef?

    func body(content view: Content) -> some View {
        let c = content
        let model = c.model
        view
            .onChange(of: c.current) { _, now in
                guard let now else { return }
                model.vm.onPageSettled(page: Int32(now))
                Task { await MushafPageService.shared.prefetch([now + 1, now - 1, now + 2, now - 2]) }
            }
            .onChange(of: playing) { _, now in c.followSound(now) }
            .onChange(of: model.check.value?.revealed) { _, _ in QhHaptics.shared.perform(.tick) }
            .onChange(of: model.check.value?.finished) { _, finished in
                if finished == true {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.48) { if model.check.value?.finished == true { c.gradeOpen = true } }
                }
            }
            .onChange(of: model.anything.value.boolValue) { _, on in IdleLock.set(on || ReadingPrefs.shared.keepScreenOn, owner: model) }
            .onDisappear { IdleLock.set(false, owner: model) }
            .task(id: c.flash) {
                guard c.flash != nil else { return }
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                withQhAnimation(QhMotion.gentle) { c.flash = nil }
            }
            .task { model.vm.onPageSettled(page: Int32(c.initial)) }
            .sheet(item: Binding(get: { c.sheet.map { MushafSheetKey(ref: $0) } },
                                 set: { if $0 == nil { c.sheet = nil; model.vm.closeSheet() } })) { key in
                MushafAyahSheet(model: model, ref: key.ref, onCheck: { c.startCheck(from: key.ref) })
            }
            .sheet(isPresented: c.$gotoSheet) {
                GoToSheetContent(surahs: c.goToRows(), currentSurah: Int(MushafPages.shared.firstAyahOf(page: Int32(page)).surah)) { surah, ayah in
                    let target = Int(MushafPages.shared.pageOf(surah: Int32(surah), ayah: Int32(ayah)))
                    withQhAnimation(QhMotion.gentle) { c.current = target; c.selected = AyahRef(surah, ayah); c.flash = AyahRef(surah, ayah) }
                }
            }
            .sheet(isPresented: Binding(get: { model.exitPrompt.value.boolValue }, set: { if !$0 { model.vm.dismissExitPrompt() } })) {
                MushafLeaveSheet(model: model)
            }
            .sheet(isPresented: c.$gradeOpen, onDismiss: { if model.check.value?.finished == true { model.vm.exitCheck() } }) {
                if let check = model.check.value { MushafGradeSheet(model: model, check: check, title: c.gradeTitle(check)) }
            }
    }
}
