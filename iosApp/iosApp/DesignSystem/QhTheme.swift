import SwiftUI
import Observation

/// The tokens of the active theme, read with `@Environment(\.qh)`.
struct QhTheme: Equatable {
    var colors: QhColors
    var type = QhTypography()
    var shapes = QhShapes()
    var isDark: Bool { colors.isDark }

    static let `default` = QhTheme(colors: .resolve(dark: false, palette: .forest))
    static func resolve(dark: Bool, palette: QhThemePalette) -> QhTheme { QhTheme(colors: .resolve(dark: dark, palette: palette)) }
}

private struct QhThemeKey: EnvironmentKey { static let defaultValue = QhTheme.default }
private struct QhReduceMotionKey: EnvironmentKey { static let defaultValue = false }

extension EnvironmentValues {
    var qh: QhTheme {
        get { self[QhThemeKey.self] }
        set { self[QhThemeKey.self] = newValue }
    }

    /// True when the system "Reduce Motion" or the in-app "fewer animations" toggle is on.
    var qhReduceMotion: Bool {
        get { self[QhReduceMotionKey.self] }
        set { self[QhReduceMotionKey.self] = newValue }
    }
}

/// Theme mode, palette and the in-app toggles. Later they come from the shared preferences (`UiPrefs`); for now defaults.
@Observable
final class ThemeSettings {
    static let shared = ThemeSettings()

    var mode: QhThemeMode = .system
    var palette: QhThemePalette = .forest
    /// The in-app "fewer animations" toggle.
    var reducedMotion = false
    /// The in-app haptics toggle.
    var hapticsEnabled = true {
        didSet { QhHaptics.shared.enabled = hapticsEnabled }
    }

    var preferredColorScheme: ColorScheme? {
        switch mode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// Root of the new UI: provides the tokens, reduced motion and the colour scheme. Wrap the whole app (and every preview) in it.
struct QhThemeRoot<Content: View>: View {
    @Bindable var settings: ThemeSettings
    @Environment(\.colorScheme) private var systemScheme
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    private let content: Content

    init(settings: ThemeSettings = .shared, @ViewBuilder content: () -> Content) {
        self.settings = settings
        self.content = content()
    }

    private var dark: Bool {
        switch settings.mode {
        case .system: return systemScheme == .dark
        case .light: return false
        case .dark: return true
        }
    }

    var body: some View {
        let theme = QhTheme.resolve(dark: dark, palette: settings.palette)
        content
            .environment(\.qh, theme)
            .environment(\.qhReduceMotion, systemReduceMotion || settings.reducedMotion)
            .tint(theme.colors.primary)
            .foregroundStyle(theme.colors.ink)
            .preferredColorScheme(settings.preferredColorScheme)
    }
}

extension View {
    /// Previews and screens: the theme root with default settings, optionally forced dark.
    func qhPreviewTheme(dark: Bool = false, palette: QhThemePalette = .forest) -> some View {
        let settings = ThemeSettings()
        settings.mode = dark ? .dark : .light
        settings.palette = palette
        return QhThemeRoot(settings: settings) {
            self.background(QhTheme.resolve(dark: dark, palette: palette).colors.bg)
        }
    }
}
