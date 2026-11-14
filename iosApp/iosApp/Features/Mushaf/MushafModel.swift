import ComposeApp
import Observation
import SwiftUI

@MainActor
@Observable
final class MushafModel {
    @ObservationIgnored private let holder: VMHolder<MushafViewModel>
    let route: DestMushaf
    let names: FlowState<[KotlinInt: String]>
    let arabicNames: FlowState<[KotlinInt: String]>
    let playing: FlowState<AyahKey?>
    let miniPlayer: FlowState<MiniPlayerUi?>
    let progress: FlowState<KotlinFloat>
    let anything: FlowState<KotlinBoolean>
    let sheet: FlowState<AyahSheetModel?>
    let check: FlowState<PageCheck?>
    let exitPrompt: FlowState<KotlinBoolean>
    let saving: FlowState<KotlinBoolean>
    var vm: MushafViewModel { holder.vm }

    init(route: Route) {
        let dest = route.dest as! DestMushaf
        self.route = dest
        let holder = VMHolder { IosUi.shared.mushaf(route: dest) }
        self.holder = holder
        let vm = holder.vm
        names = FlowState(vm.names)
        arabicNames = FlowState(vm.arabicNames)
        playing = FlowState(vm.playing)
        miniPlayer = FlowState(vm.miniPlayer)
        progress = FlowState(vm.playbackProgress)
        anything = FlowState(vm.anythingPlaying)
        sheet = FlowState(vm.sheet)
        check = FlowState(vm.check)
        exitPrompt = FlowState(vm.exitPrompt)
        saving = FlowState(vm.saving)
    }

    func name(_ surah: Int) -> String { names.value[KotlinInt(int: Int32(surah))] ?? "" }
    func arabic(_ surah: Int) -> String { arabicNames.value[KotlinInt(int: Int32(surah))] ?? "" }

    func place(of page: Int) -> MushafPlace {
        MushafPlace(juz: MushafPages.shared.juzOf(page: Int32(page)), hizb: MushafPages.shared.hizbOf(page: Int32(page)), page: Int32(page))
    }

    /// The looks of "Check yourself" on `page` (nothing outside the check).
    func looks(page: Int) -> [AyahRef: AyahLook] {
        guard let check = check.value, Int(check.page) == page else { return [:] }
        var out: [AyahRef: AyahLook] = [:]
        for item in check.items {
            let ref = AyahRef(item.key)
            let phase = check.phaseOf(key: item.key)
            if phase == ComposeApp.HifzPhase.done {
                out[ref] = item.mark?.name == "HARD" ? .hard : .normal
            } else if phase == ComposeApp.HifzPhase.current {
                out[ref] = check.revealed ? .normal : .hidden
            } else if phase == ComposeApp.HifzPhase.upcoming {
                out[ref] = .waiting
            }
        }
        return out
    }
}
