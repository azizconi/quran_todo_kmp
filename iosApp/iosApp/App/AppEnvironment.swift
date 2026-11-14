import ComposeApp
import Observation
import SwiftUI

/// App-wide state that comes from the shared preferences: the language (and so the `Copy`), the theme mode and palette, reduced motion
/// and haptics. One instance (`AppEnvironment.shared`), created at launch after Koin starts.
///
/// Settings screens change these only through the setters below, so the whole UI follows at once and the value is stored where
/// Android stores it (`LanguageStorage`, `ThemeStorage`, `UiPrefs`).
@MainActor
@Observable
final class AppEnvironment {
    static let shared = AppEnvironment()

    let copy: Copy
    let theme = ThemeSettings.shared
    let toaster = Toaster()

    @ObservationIgnored private var tasks: [Task<Void, Never>] = []

    private init() {
        let prefs = IosUi.shared.uiPrefs
        copy = Copy(language: IosUi.shared.language.value)
        applyTheme(followsSystem: prefs.themeFollowsSystem.value.boolValue)
        theme.palette = Self.palette(ThemeStorage.shared.getSavedThemePalette())
        theme.reducedMotion = prefs.reduceMotion.value.boolValue
        theme.hapticsEnabled = prefs.hapticsEnabled.value.boolValue

        observe(IosUi.shared.language) { [weak self] in self?.copy.set(language: $0) }
        observe(prefs.themeFollowsSystem) { [weak self] in self?.applyTheme(followsSystem: $0.boolValue) }
        observe(prefs.reduceMotion) { [weak self] in self?.theme.reducedMotion = $0.boolValue }
        observe(prefs.hapticsEnabled) { [weak self] in self?.theme.hapticsEnabled = $0.boolValue }
    }

    private func observe<S: AsyncSequence>(_ flow: S, _ apply: @escaping @MainActor (S.Element) -> Void) {
        tasks.append(Task {
            do { for try await value in flow { apply(value) } } catch {}
        })
    }

    // MARK: - Mapping

    private static func palette(_ saved: ThemePalette?) -> QhThemePalette {
        switch saved {
        case .sand: return .sand
        case .ocean: return .ocean
        default: return .forest
        }
    }

    private func applyTheme(followsSystem: Bool) {
        if followsSystem {
            theme.mode = .system
        } else {
            theme.mode = ThemeStorage.shared.getSavedThemeMode() == .dark ? .dark : .light
        }
    }

    // MARK: - Setters (Settings, Onboarding)

    var language: AppLanguage { copy.language }

    func setLanguage(_ language: AppLanguage) { IosUi.shared.setLanguage(language: language) }

    /// `.system` follows the device; light / dark are remembered. Plays the circular reveal from the last touch.
    func setThemeMode(_ mode: QhThemeMode, reveal: Bool = true) {
        let change = {
            let prefs = IosUi.shared.uiPrefs
            switch mode {
            case .system:
                prefs.setThemeFollowsSystem(value: true)
            case .light:
                ThemeStorage.shared.saveThemeMode(mode: .light)
                prefs.setThemeFollowsSystem(value: false)
            case .dark:
                ThemeStorage.shared.saveThemeMode(mode: .dark)
                prefs.setThemeFollowsSystem(value: false)
            }
            self.theme.mode = mode
        }
        if reveal { QhThemeReveal.reveal(change: change) } else { change() }
    }

    func setPalette(_ palette: QhThemePalette, reveal: Bool = true) {
        let change = {
            let stored: ThemePalette
            switch palette {
            case .forest: stored = .forest
            case .sand: stored = .sand
            case .ocean: stored = .ocean
            }
            ThemeStorage.shared.saveThemePalette(palette: stored)
            self.theme.palette = palette
        }
        if reveal { QhThemeReveal.reveal(change: change) } else { change() }
    }

    func setReduceMotion(_ on: Bool) { IosUi.shared.uiPrefs.setReduceMotion(value: on) }
    func setHaptics(_ on: Bool) { IosUi.shared.uiPrefs.setHapticsEnabled(value: on) }
}
