import ComposeApp
import SwiftUI
import UIKit

private struct SurahRoute: Hashable {
    let number: Int32
}

/// The small set of shared colours which must continue into iOS system areas.
/// Kotlin owns the source tokens; SwiftUI only renders the appearance it gets
/// from the shared app shell.
struct AppChrome {
    let background: Color
    let surface: Color
    let primary: Color
    let isDark: Bool

    init(_ appearance: AppChromeAppearance) {
        background = Color(argb: appearance.backgroundArgb)
        surface = Color(argb: appearance.surfaceArgb)
        primary = Color(argb: appearance.primaryArgb)
        isDark = appearance.isDark
    }

    var colorScheme: ColorScheme {
        isDark ? .dark : .light
    }
}

struct ComposeLibraryView: UIViewControllerRepresentable {
    let onOpenSurah: (Int32) -> Void
    let onOpenSettings: () -> Void
    @Binding var chrome: AppChrome

    func makeUIViewController(context: Context) -> UIViewController {
        let chrome = $chrome
        return MainViewControllerKt.MainViewController(onNativeSurahRequested: { number in
            onOpenSurah(Int32(number.intValue))
        }, onNativeSettingsRequested: onOpenSettings, onChromeChanged: { appearance in
            let nextChrome = AppChrome(appearance)
            DispatchQueue.main.async {
                chrome.wrappedValue = nextChrome
            }
        })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var selectedRoute: SurahRoute?
    @State private var showsSettings = false
    @State private var chrome: AppChrome

    init() {
        let isSystemDark = UIScreen.main.traitCollection.userInterfaceStyle == .dark
        _chrome = State(
            initialValue: AppChrome(
                IosAppChrome.shared.initialAppearance(isSystemDark: isSystemDark)
            )
        )
    }

    var body: some View {
        ZStack {
            // The colour intentionally reaches the Dynamic Island and Home
            // Indicator. Only the embedded Compose content is safe-area bound.
            chrome.background.ignoresSafeArea()

            NavigationView {
                ZStack {
                    chrome.background

                    ComposeLibraryView(
                        onOpenSurah: { surahNumber in
                            selectedRoute = SurahRoute(number: surahNumber)
                        },
                        onOpenSettings: {
                            showsSettings = true
                        },
                        chrome: $chrome
                    )
                    // Compose has its own keyboard handler. Do not ignore the
                    // container safe area: the library top bar must stay below
                    // the status bar and Dynamic Island.
                    .ignoresSafeArea(.keyboard)

                    // NavigationView owns an opaque UIKit controller behind its
                    // safe-area-bound SwiftUI child. While the library is the
                    // visible screen, give that controller the exact library
                    // colour too; otherwise its default white view leaks into
                    // the status-bar area. Native destinations install their
                    // own chrome after a push and this helper leaves the tree.
                    if selectedRoute == nil && !showsSettings {
                        Color.clear
                            .frame(width: 0, height: 0)
                            .appNavigationChrome(
                                background: chrome.background,
                                tint: chrome.primary,
                                isDark: chrome.isDark
                            )
                            .accessibilityHidden(true)
                    }

                    NavigationLink(
                        destination: Group {
                            if let route = selectedRoute {
                                NativeSurahReaderScreen(
                                    surahNumber: route.number,
                                    fallbackChrome: chrome
                                )
                            }
                        },
                        isActive: Binding(
                            get: { selectedRoute != nil },
                            set: { if !$0 { selectedRoute = nil } }
                        )
                    ) {
                        EmptyView()
                    }
                    .hidden()

                    NavigationLink(
                        destination: NativeSettingsScreen(chrome: $chrome),
                        isActive: $showsSettings
                    ) {
                        EmptyView()
                    }
                    .hidden()
                }
                .navigationBarHidden(true)
            }
            .navigationViewStyle(.stack)
            // NavigationView is backed by an opaque UIKit container. Give that
            // container its own full-bleed background as well, otherwise its
            // default system white can still appear above the safe area.
            .background(chrome.background.ignoresSafeArea())
        }
        .preferredColorScheme(chrome.colorScheme)
        .tint(chrome.primary)
    }
}

extension Color {
    init(argb: Int64) {
        let value = UInt64(bitPattern: argb)
        self.init(
            red: Double((value >> 16) & 0xFF) / 255.0,
            green: Double((value >> 8) & 0xFF) / 255.0,
            blue: Double(value & 0xFF) / 255.0,
            opacity: Double((value >> 24) & 0xFF) / 255.0
        )
    }

    var uiColor: UIColor {
        UIColor(self)
    }
}

/// iOS 15 has no SwiftUI navigation-bar background API. This small host view
/// configures the nearest UINavigationController without changing global
/// UIKit appearance, so the active native screen owns its own system chrome.
private struct NavigationChromeConfigurator: UIViewControllerRepresentable {
    let background: UIColor
    let tint: UIColor
    let isDark: Bool

    func makeUIViewController(context: Context) -> ChromeViewController {
        ChromeViewController(background: background, tint: tint, isDark: isDark)
    }

    func updateUIViewController(_ viewController: ChromeViewController, context: Context) {
        viewController.update(background: background, tint: tint, isDark: isDark)
    }

    final class ChromeViewController: UIViewController {
        private var chromeBackground: UIColor
        private var chromeTint: UIColor
        private var isDark: Bool

        init(background: UIColor, tint: UIColor, isDark: Bool) {
            chromeBackground = background
            chromeTint = tint
            self.isDark = isDark
            super.init(nibName: nil, bundle: nil)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .clear
            view.isUserInteractionEnabled = false
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            applyChrome()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            applyChrome()
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            applyChrome()
        }

        func update(background: UIColor, tint: UIColor, isDark: Bool) {
            chromeBackground = background
            chromeTint = tint
            self.isDark = isDark
            applyChrome()
        }

        private func applyChrome() {
            guard let navigationController = nearestNavigationController else { return }

            navigationController.overrideUserInterfaceStyle = isDark ? .dark : .light
            navigationController.view.backgroundColor = chromeBackground
            navigationController.topViewController?.view.backgroundColor = chromeBackground
            view.window?.backgroundColor = chromeBackground

            let appearance = UINavigationBarAppearance()
            appearance.configureWithOpaqueBackground()
            appearance.backgroundColor = chromeBackground
            appearance.backgroundEffect = nil
            appearance.shadowColor = .clear
            appearance.titleTextAttributes = [.foregroundColor: UIColor.label]
            appearance.largeTitleTextAttributes = [.foregroundColor: UIColor.label]

            let navigationBar = navigationController.navigationBar
            navigationBar.isTranslucent = false
            navigationBar.tintColor = chromeTint
            navigationBar.standardAppearance = appearance
            navigationBar.scrollEdgeAppearance = appearance
            navigationBar.compactAppearance = appearance
            navigationBar.setNeedsLayout()
            navigationController.setNeedsStatusBarAppearanceUpdate()
        }

        private var nearestNavigationController: UINavigationController? {
            if let navigationController {
                return navigationController
            }

            var current = parent
            while let controller = current {
                if let navigationController = controller as? UINavigationController {
                    return navigationController
                }
                current = controller.parent
            }
            return nil
        }
    }
}

extension View {
    func appNavigationChrome(background navigationBackground: Color, tint: Color, isDark: Bool) -> some View {
        self.background(
            NavigationChromeConfigurator(
                background: navigationBackground.uiColor,
                tint: tint.uiColor,
                isDark: isDark
            )
            .frame(width: 0, height: 0)
        )
    }
}
