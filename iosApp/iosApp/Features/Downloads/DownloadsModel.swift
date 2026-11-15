import ComposeApp
import Observation
import SwiftUI

/// What a confirmation dialog of the screen is about (Android `Ask`).
enum DownloadsAsk: Identifiable {
    case deleteRow(RowUi)
    case deleteTranslation(TranslationRowUi)
    case deleteAll, clearCache, cancelAll

    var id: String {
        switch self {
        case let .deleteRow(row): return "row-\(row.id)"
        case let .deleteTranslation(row): return "translation-\(row.key)"
        case .deleteAll: return "all"
        case .clearCache: return "cache"
        case .cancelAll: return "cancel"
        }
    }
}

/// The Kotlin `DownloadsViewModel` with its state as an observable value. The queue itself runs on the background `URLSession`
/// of the iOS service; this only reads and asks.
@MainActor
@Observable
final class DownloadsModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.downloads() }
    @ObservationIgnored private var events: FlowEvents<DownloadsEvent>?
    let ui: FlowState<DownloadsUi>
    var vm: DownloadsViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        holder.vm.setLanguage(next: IosUi.shared.language.value)
        holder.vm.refreshCache()
    }

    func bind(env: AppEnvironment) {
        guard events == nil else { return }
        let toaster = env.toaster
        let copy = env.copy
        events = FlowEvents(vm.events) { event in
            let d = copy.s7Downloads
            switch onEnum(of: event) {
            case .notEnoughSpace: toaster.show(d.notEnoughSpace, kind: .error)
            case .deleted: toaster.show(d.deletedToast, kind: .success)
            case .cacheCleared: toaster.show(d.cacheClearedToast, kind: .success)
            }
        }
    }

    func setLanguage(_ language: AppLanguage) { vm.setLanguage(next: language) }

    /// A download starts. The iOS download runs on a background `URLSession` and needs no notification permission, so none is asked here.
    func download(_ rowId: String) {
        vm.download(rowId: rowId)
    }

    func downloadTranslation(_ key: String) {
        vm.downloadTranslation(key: key)
    }
}
