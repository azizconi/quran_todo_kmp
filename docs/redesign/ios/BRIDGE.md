# Swift <-> Kotlin bridge (written by IOS-S3; follow it in every screen)

Kotlin owns logic and strings; Swift draws. SKIE 0.10.2 turns every Kotlin `Flow` into an `AsyncSequence`.
Swift names: Kotlin `Dest.Reader` is `DestReader`, `ViewModel` is `Lifecycle_viewmodelViewModel`, boxed ints are `KotlinInt` /
`KotlinBoolean` / `KotlinFloat`, sealed types are switched with `switch onEnum(of: x)`, enums have `allCases`.

## View models: `Bridge/VMHolder.swift`
`VMHolder { IosUi.shared.plan() }` makes the Kotlin view model lazily and calls `IosUi.clear` when the holder is released, i.e. when
SwiftUI drops the `@State` of the screen that left the hierarchy (pop, sheet dismissed). Same life as a back stack entry.
Do NOT clear in `onDisappear`: a tab root disappears on every tab switch.

## Flows: `Bridge/FlowState.swift`
- `FlowState(vm.ui)` (`StateFlow<T>` -> `FlowState<T>`, `StateFlow<T?>` -> `FlowState<T?>`): `@Observable`, `.value`; a `Task` iterates the
  flow on the main actor and is cancelled in `deinit`.
- `FlowEvents(vm.events) { event in ... }`: one-shot events (toasts, "finished"); cancelled in `deinit`.
- For a flow that exists only while a view is up: `.task { for await v in flow { state = v } }` (see `SurahSheetView`).

The pattern for a screen is one `@Observable` model that owns the holder and the states:
```swift
@MainActor @Observable final class PlanModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.plan() }
    let ui: FlowState<PlanUi>
    var vm: PlanViewModel { holder.vm }
    init() { ui = FlowState(holder.vm.ui) }
}
struct PlanScreen: View { @State private var model = PlanModel() /* model.ui.value, model.vm.setX(...) */ }
```
`HomeModel` and `OnboardingModel` (Features/Home, Features/Onboarding) are the reference implementations. Kotlin calls take `Int32`.

## Copy: `Bridge/Copy.swift`
`@Environment(Copy.self) var copy` then `copy.home.lessonTitle`, `copy.tabs.tabLesson`. Reading `copy.x` reads `language` first, so
views redraw on a language change; objects are cached per language. Add an accessor for another `*Copy` when needed (list: `IosCopy`).

## App state: `App/AppEnvironment.swift`
`AppEnvironment.shared` follows `UiPrefs` / `LanguageStorage` / `ThemeStorage` (language, theme follows system, palette, reduce motion,
haptics) and drives `ThemeSettings.shared` and `Copy`. Settings screens change things ONLY through its setters:
`setLanguage`, `setThemeMode` / `setPalette` (play the circular reveal), `setReduceMotion`, `setHaptics`. Also `env.toaster`
(`env.toaster.show(msg, kind: .error)`). `QhThemeRoot` reads the device colour scheme through `SystemScheme`.

## Navigation: `App/Router.swift` (the only file to touch to plug a screen in)
`Route` (Hashable Swift enum of every `Dest`) <-> Kotlin `Dest` (`route.dest`, `Route(dest:)`). `Route.screen()` is THE routing table:
replace a `ComingSoon(route: self, stage: "Sx")` line with the real view. `@Environment(Router.self)`:
`push(_:in:)` (debounced), `openReader(_ surah:ayah:origin:)` (applies `ReaderRouting`: Pages mode opens the mushaf), `replaceTop(with:)`
(Go to, Text<->Pages), `pop()`, `goHome()`, `finishedOnboarding(_:)`, `open(url:)` for `quranhifz://lesson|plan|quran|progress|reader/<s>/<a>`.
Pushed screens use the native bar (`.navigationTitle`, `.qhNavigationStyle()`); the Lesson tab root hides it for a custom large title
with the greeting. The mini player slot is `Features/Shell/MiniPlayerSlot.swift` (`.miniPlayerInset(home)` on tab roots).

## Startup (Kotlin, `bridge/IosStartup.kt`)
`IosStartup.shared.resolveGate()` (marks an existing user as passed) then `whatsNewPending()`; both run in `QuranHifzApp.init`.
`// ServicesBootstrap.register()` is the commented hook for the S2 services.
