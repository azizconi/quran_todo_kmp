import SwiftUI

/// Constants of the staggered entrance (ANDROID_REDESIGN.md, 3.3).
enum StaggerDefaults {
    /// Only the first items animate; the rest appear at once.
    static let maxItems = 8
    static let delayPerItem: Double = 0.030
    static let duration: Double = 0.300
    static let offset: CGFloat = 16
}

/// Remembers, per screen, whether the entrance stagger has already played, so coming back to a screen does not replay it.
/// Create one per screen with `@State private var stagger = StaggerState(screen: "home")`.
final class StaggerState {
    private static var played = Set<String>()
    private let screen: String
    private var shown = Set<Int>()
    /// False when the stagger already played for this screen: items then appear immediately.
    let play: Bool

    init(screen: String) {
        self.screen = screen
        self.play = !StaggerState.played.contains(screen)
    }

    /// True only the first time `index` is asked for, so items scrolled back into view do not replay.
    fileprivate func consume(_ index: Int) -> Bool { shown.insert(index).inserted }

    /// Call when the screen's real content is on screen (after loading), so a skeleton phase does not use up the entrance.
    func markPlayed() { StaggerState.played.insert(screen) }

    /// Forgets all screens (a cold start does this implicitly).
    static func reset() { played.removeAll() }
}

private struct StaggeredEnter: ViewModifier {
    @Environment(\.qhReduceMotion) private var reduced
    let index: Int
    let state: StaggerState
    let enabled: Bool
    @State private var progress: CGFloat
    @State private var animate: Bool

    init(index: Int, state: StaggerState, enabled: Bool) {
        self.index = index
        self.state = state
        self.enabled = enabled
        let will = enabled && state.play && index >= 0 && index < StaggerDefaults.maxItems && state.consume(index)
        _animate = State(initialValue: will)
        _progress = State(initialValue: will ? 0 : 1)
    }

    func body(content: Content) -> some View {
        content
            .opacity(Double(progress))
            .offset(y: (1 - progress) * StaggerDefaults.offset)
            .onAppear {
                guard animate else { return }
                if reduced { progress = 1; return }
                withAnimation(QhMotion.enter(StaggerDefaults.duration).delay(Double(index) * StaggerDefaults.delayPerItem)) {
                    progress = 1
                }
            }
    }
}

extension View {
    /// Fades an item in while sliding it up 16pt, delayed by `index x 30ms`. Only the first 8 items animate. Does nothing when
    /// the stagger was already played for this screen, when `enabled` is false, or under Reduce Motion.
    func staggeredEnter(index: Int, state: StaggerState, enabled: Bool = true) -> some View {
        modifier(StaggeredEnter(index: index, state: state, enabled: enabled))
    }
}
