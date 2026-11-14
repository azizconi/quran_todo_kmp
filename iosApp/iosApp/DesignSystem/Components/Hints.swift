import SwiftUI
import Observation

/// Where the "hint used" marks are kept.
protocol QhHintStore {
    func read() -> Set<String>
    func add(_ hintId: String)
    func clear()
}

/// UserDefaults store, in its own suite (`quran_hifz_spotlights`), like the Android SharedPreferences file: not part of the backup.
struct UserDefaultsHintStore: QhHintStore {
    private let defaults = UserDefaults(suiteName: "quran_hifz_spotlights") ?? .standard
    private let prefix = "hint_used_"

    func read() -> Set<String> {
        Set(defaults.dictionaryRepresentation().compactMap { key, value in
            key.hasPrefix(prefix) && (value as? Bool) == true ? String(key.dropFirst(prefix.count)) : nil
        })
    }
    func add(_ hintId: String) { defaults.set(true, forKey: prefix + hintId) }
    func clear() {
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(prefix) { defaults.removeObject(forKey: key) }
    }
}

struct InMemoryHintStore: QhHintStore {
    func read() -> Set<String> { [] }
    func add(_ hintId: String) {}
    func clear() {}
}

/// The hints of the app: which ones the person already used or dismissed.
@Observable
final class HintFlags {
    static let shared = HintFlags(store: UserDefaultsHintStore())
    private let store: QhHintStore
    private var used: Set<String>

    init(store: QhHintStore) {
        self.store = store
        self.used = store.read()
    }

    func isUsed(_ id: String) -> Bool { used.contains(id) }

    /// The person did the thing (or tapped "x"): the hint never shows again.
    func markUsed(_ id: String) {
        guard !used.contains(id) else { return }
        used.insert(id)
        store.add(id)
    }

    func resetAll() {
        used.removeAll()
        store.clear()
    }
}

/// One quiet line that explains a gesture on the screen itself: no overlay, no dimming. It goes away for good after the person does
/// the thing once (the screen calls `HintFlags.shared.markUsed(id)`) or taps the "x".
struct QhInlineHint: View {
    @Environment(\.qh) private var qh
    let id: String
    let text: String
    var dismissLabel = "Dismiss"
    var hints: HintFlags = .shared

    var body: some View {
        if !hints.isUsed(id) {
            HStack(spacing: 0) {
                Text(text).qhText({ $0.callout }, color: { $0.inkSecondary }).frame(maxWidth: .infinity, alignment: .leading)
                Button { withQhAnimation(QhMotion.snappy) { hints.markUsed(id) } } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(qh.colors.inkSecondary)
                        .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(dismissLabel)
            }
            .frame(minHeight: QhSpacing.minTouch)
            .transition(.opacity.combined(with: .scale(scale: 0.98, anchor: .top)))
        }
    }
}

#Preview("Hint light") { HintPreview().qhPreviewTheme() }
#Preview("Hint dark") { HintPreview().qhPreviewTheme(dark: true) }

private struct HintPreview: View {
    var body: some View {
        QhInlineHint(id: "preview", text: "Swipe a card left to postpone it.", hints: HintFlags(store: InMemoryHintStore())).padding()
    }
}
