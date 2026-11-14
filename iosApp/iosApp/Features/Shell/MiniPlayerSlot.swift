import SwiftUI

/// The slot of the mini player above the tab bar on the three tab roots. S5 fills `MiniPlayerSlot` (it observes
/// `IosUi.shared.homeViewModel`-independent `HomeUiViewModel.player`, which `HomeModel.player` already exposes). Empty until then.
struct MiniPlayerSlot: View {
    let home: HomeModel

    var body: some View {
        EmptyView()
    }
}

extension View {
    /// Puts the mini player slot above the tab bar. Apply to the ROOT view of each tab (not to pushed screens: they have their own dock).
    func miniPlayerInset(_ home: HomeModel) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayerSlot(home: home) }
    }
}
