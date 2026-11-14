import ComposeApp
import Observation
import SwiftUI

/// A Kotlin `StateFlow` as an `@Observable` value: SwiftUI redraws whatever reads `value` when the flow emits.
///
/// A `Task` iterates the flow (SKIE makes every Kotlin `Flow` an `AsyncSequence`) on the main actor and is cancelled in `deinit`, so
/// the observer lives exactly as long as the object that owns it (a screen model, usually next to a `VMHolder`).
/// ```swift
/// let ui = FlowState(vm.ui)            // StateFlow<HomeUi>        -> FlowState<HomeUi>
/// let player = FlowState(vm.player)    // StateFlow<MiniPlayerUi?> -> FlowState<MiniPlayerUi?>
/// Text(ui.value.phase.name)
/// ```
@MainActor
@Observable
final class FlowState<Value> {
    private(set) var value: Value
    @ObservationIgnored private var task: Task<Void, Never>?

    init<S: AsyncSequence>(initial: Value, sequence: S) where S.Element == Value {
        value = initial
        task = Task { [weak self] in
            do {
                for try await next in sequence {
                    guard let self else { return }
                    self.value = next
                }
            } catch {}
        }
    }

    convenience init(_ flow: SkieSwiftStateFlow<Value>) {
        self.init(initial: flow.value, sequence: flow)
    }

    convenience init<Wrapped>(_ flow: SkieSwiftOptionalStateFlow<Wrapped>) where Value == Wrapped? {
        self.init(initial: flow.value, sequence: flow)
    }

    func cancel() { task?.cancel(); task = nil }

    deinit { task?.cancel() }
}

/// A one-shot Kotlin `Flow` (events: a toast, "finished") consumed until the owner goes away.
@MainActor
final class FlowEvents<Value> {
    private var task: Task<Void, Never>?

    init<S: AsyncSequence>(_ sequence: S, _ handler: @escaping @MainActor (Value) -> Void) where S.Element == Value {
        task = Task {
            do {
                for try await event in sequence { handler(event) }
            } catch {}
        }
    }

    func cancel() { task?.cancel(); task = nil }

    deinit { task?.cancel() }
}
