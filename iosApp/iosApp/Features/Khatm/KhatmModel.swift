import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `KhatmViewModel` (the reading plan and Ramadan) with its state and events. The screen and the cards of Home each own one,
/// like two back stack entries on Android; the plan itself is stored (`hifz_meta`), so both show the same thing.
@MainActor
@Observable
final class KhatmModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.khatm() }
    let ui: FlowState<KhatmUi>
    @ObservationIgnored private var events: FlowEvents<KhatmEvent>?
    var vm: KhatmViewModel { holder.vm }

    init() { ui = FlowState(holder.vm.ui) }

    /// Wires the toasts of the events once (`KhatmEvent` -> the toast of the copy).
    func bind(toaster: Toaster, copy: Copy) {
        guard events == nil else { return }
        events = FlowEvents(holder.vm.eventFlow) { event in
            let c = copy.khatm
            switch event {
            case .marked:
                QhHaptics.shared.perform(.success)
                toaster.show(c.markedToast, kind: .success)
            case .monthPlanCreated: toaster.show(c.monthPlanCreatedToast, kind: .success)
            case .created: toaster.show(c.createdToast, kind: .success)
            case .deleted: toaster.show(c.deletedToast, kind: .info)
            case .failed: toaster.show(c.failed, kind: .error)
            case .listenFailed: toaster.show(c.juzUnavailable, kind: .error)
            default: break
            }
        }
    }
}

/// What the headline, the sub line and the buttons of a khatm card or overview say about today's state of the plan.
struct KhatmDayInfo {
    let status: KhatmToday
    let state: KhatmState
    let today: Int

    var overdue: Bool { if case .overdue = onEnum(of: status) { return true } else { return false } }
    var complete: Bool { if case .complete = onEnum(of: status) { return true } else { return false } }
    var delta: Int { overdue ? 0 : Int(KhatmMath.shared.deltaPages(state: state, today: Int32(today))) }

    init(state: KhatmState, today: Int) {
        self.state = state
        self.today = today
        status = KhatmMath.shared.status(state: state, today: Int32(today))
    }

    func headline(_ c: KhatmCopy) -> String {
        switch onEnum(of: status) {
        case let .pending(p): return c.cardPending(first: p.portion.firstPage, last: p.portion.lastPage, minutes: p.minutes)
        case .overdue: return c.overdueTitle
        case let .doneToday(done):
            if let next = done.next { return c.cardDone(first: next.firstPage, last: next.lastPage) }
            return c.cardCompleteTitle
        case .complete: return c.cardCompleteTitle
        }
    }

    /// "120 of 604 pages · 2 pages behind".
    func progress(_ c: KhatmCopy) -> String {
        if complete { return c.cardCompleteBody }
        let read = KhatmMath.shared.pagesRead(state: state)
        var text = c.progress(read: read, total: state.plan.totalPages)
        if delta < 0 { text += " · " + c.behind(pages: Int32(-delta)) } else if delta > 0 { text += " · " + c.ahead(pages: Int32(delta)) }
        return text
    }

    var fraction: Double { Double(KhatmMath.shared.fraction(state: state)) }
}
