import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `HomeUiViewModel` of the Lesson and Quran tabs (and the mini player slot), with its flows as observable values.
/// MainTabs owns one for both tabs. Everything the tabs do goes through here.
@MainActor
@Observable
final class HomeModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.home() }
    @ObservationIgnored private var eventTask: FlowEvents<HomeEvent>?

    let ui: FlowState<HomeUi>
    let player: FlowState<MiniPlayerUi?>
    let listening: FlowState<ListeningUi?>
    let playbackProgress: FlowState<KotlinFloat>

    var vm: HomeUiViewModel { holder.vm }

    init() {
        let vm = holder.vm
        ui = FlowState(vm.ui)
        player = FlowState(vm.player)
        listening = FlowState(vm.listening)
        playbackProgress = FlowState(vm.playbackProgress)
        vm.setLanguage(next: IosUi.shared.language.value)
    }

    /// One-shot events become toasts. Call once from the screen that owns the model.
    func bindEvents(toaster: Toaster, copy: Copy) {
        guard eventTask == nil else { return }
        eventTask = FlowEvents(vm.events) { event in
            switch onEnum(of: event) {
            case .statusUpdateFailed: toaster.show(copy.home.statusUpdateFailed, kind: .error)
            case .resumeFailed: toaster.show(copy.pause.failed, kind: .error)
            }
        }
    }

    func setLanguage(_ language: AppLanguage) { vm.setLanguage(next: language) }
    func onResume() { vm.onResume() }
    func retry() { vm.retry() }
    func setQuery(_ query: String) { vm.setQuery(value: query) }
    func setFilter(_ filter: SurahFilter) { vm.setFilter(value: filter) }
    func togglePin(_ surah: Int) { vm.togglePin(surah: Int32(surah)) }
    func resumePlan() { vm.resumePlan() }
    func listen(surah: Int, readAyah: Int) { vm.listen(surah: Int32(surah), readAyah: Int32(readAyah)) }
    func togglePlayback() { vm.togglePlayback() }
    func stopPlayback() { vm.stopPlayback() }
    func applyStatus(surah: Int, target: SurahTarget) { vm.applyStatus(surah: Int32(surah), target: target) }
    func coverage(surah: Int) -> SkieSwiftFlow<SurahCoverage> { vm.coverage(surah: Int32(surah)) }
}
