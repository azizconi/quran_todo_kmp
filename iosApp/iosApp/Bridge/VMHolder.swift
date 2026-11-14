import ComposeApp
import SwiftUI

/// Keeps a Kotlin view model alive for exactly as long as the SwiftUI screen that owns the holder.
///
/// The view model is made lazily on first use (`IosUi.shared.plan()` and friends) and `IosUi.clear` runs when the holder is released,
/// which is when SwiftUI drops the `@State` storage of the view that left the hierarchy (a popped screen, a dismissed sheet). That
/// is the same life as a back stack entry on Android. Do NOT use `onDisappear`: a tab root disappears on every tab switch.
///
/// ```swift
/// struct PlanScreen: View {
///     @State private var model = PlanModel()          // an @Observable class that owns a VMHolder and FlowStates
/// }
/// ```
@MainActor
final class VMHolder<VM: Lifecycle_viewmodelViewModel> {
    private let make: () -> VM
    private var made: VM?

    init(_ make: @escaping () -> VM) { self.make = make }

    var vm: VM {
        if let made { return made }
        let created = make()
        made = created
        return created
    }

    /// Ends the Kotlin view model now (`onCleared`, coroutines cancelled). Safe to call twice; also runs on release.
    func clear() {
        guard let made else { return }
        self.made = nil
        IosUi.shared.clear(viewModel: made)
    }

    deinit {
        guard let made else { return }
        Task { @MainActor in IosUi.shared.clear(viewModel: made) }
    }
}
