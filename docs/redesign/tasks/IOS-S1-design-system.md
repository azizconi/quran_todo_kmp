# IOS-S1 — Xcode modernisation and the SwiftUI design system

Read first: `docs/redesign/IOS_PORT_PLAN.md`, `docs/redesign/ANDROID_REDESIGN.md` (sections on colour, type, shape, motion,
ornaments), `docs/redesign/tasks/P5-no-stars-calligraphy.md`, and the Android sources you port:
`composeApp/src/androidMain/kotlin/tj/app/quran_todo/ui/{theme,motion,components,haptics}/`.

You own `iosApp/` only. Do not edit Kotlin or Gradle files (another agent moves the shared layer at the same time).

## 1. Xcode project

- Convert `iosApp/iosApp.xcodeproj` to **file-system synchronized groups** (objectVersion 77,
  `PBXFileSystemSynchronizedRootGroup` for `iosApp/iosApp`), so any Swift file added under `iosApp/iosApp/` is compiled without
  editing `project.pbxproj`. Keep the existing Run Script phase (`embedAndSignAppleFrameworkForXcode`), bundle id, signing,
  `Info.plist`, `GoogleService-Info.plist`, fonts, assets.
- Deployment target **iOS 17.0**, Swift 5 language mode is fine (Swift 6 strict concurrency not required).
- Create the folder layout from the plan: `App/ Bridge/ DesignSystem/ Services/ Features/` (empty features are fine). Leave the
  existing `ContentView.swift`, `NativeSettingsScreen.swift`, `NativeSurahReaderScreen.swift`, `ReaderPlayerSheet.swift` in place and
  compiling for now (they get replaced in S3); move them into `Legacy/`.
- Fonts: every font in `composeApp/src/commonMain/composeResources/font/` (Amiri Quran, Aref Ruqaa + Ink, Lateef, Madinah mushaf,
  Noto Naskh, Noto Nastaliq, Scheherazade New) registered in Info.plist `UIAppFonts` (aref_ruqaa.ttf is missing in iosApp/Fonts now).

## 2. Design system (`iosApp/iosApp/DesignSystem/`)

Port faithfully, adapt idiomatically:
- `QhColors.swift`: every token of `QhColors.kt` for every `ThemePalette` (read the whole file) in light/dark; exposed through
  an `@Environment(\.qh)` value `QhTheme` (colors, typography, shapes, spacing, isDark). Theme mode (system/light/dark) and palette
  come later from shared prefs — for now a `ThemeSettings` observable with defaults.
- `QhTypography.swift`: the Android scale mapped to SwiftUI `Font` with Dynamic Type (`relativeTo:`); Arabic faces via
  `Font.custom` with the arabic size scale of `ArabicScale.kt`.
- `QhShapes.swift`, `QhSpacing.swift`.
- `Motion.swift`: `QhMotion.snappy/bouncy/gentle/press/bounds` as SwiftUI `.spring(response:dampingFraction:)` equivalents
  (convert stiffness/damping: response = 2π/√stiffness, dampingFraction = dampingRatio), durations, emphasized curves as
  `.timingCurve`, a `reduceMotion`-aware helper (`withQhAnimation`, `.qhAnimation(_:value:)`) that snaps when Reduce Motion is on.
  Port `Pressable` (scale on press, ButtonStyle), `Stagger` (staggered appear), `AnimatedCounter` (`.contentTransition(.numericText())`),
  `Celebration` (calm, no stars/confetti stars — use rising calligraphy/medallion petals as Android does), `CircularReveal`.
- `Haptics.swift`: `UIImpactFeedbackGenerator`/`UINotificationFeedbackGenerator`/`sensoryFeedback` mapping of `Haptics.kt`
  events; a global enabled flag.
- Components (one file each, `#Preview` for each in light + dark): Buttons (primary, tonal, text, danger, icon-with-label),
  Chips, Surfaces/cards, Progress (linear + the **progress ring** used on Home/Progress), SegmentedControl, SearchField (prefer
  `.searchable` where screens allow, keep a component for inline use), Sheets (detents, grabber, header), Skeleton (shimmer),
  Toast (top banner with spring), Toggles, TopBars (large title + labelled icons like Android U23), Wheel (picker 48pt rows),
  EmptyState, inline Hints (`ui/components/hints`), Ornaments (mihrab arch, 12-lobed medallion, ogee lattice, rhombus,
  calligraphy headers — vector `Shape`s, never stars).
- A `DesignCatalogView` (debug only, reachable from a hidden long-press later) showing all components; set it as the temporary
  root of the app so the build shows something.

## Checks

- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' build`
  succeeds (the Kotlin framework builds through the Run Script; first build is slow).
- Launch on the simulator (`xcrun simctl install/launch`), screenshot the catalog in light and dark
  (`xcrun simctl io booted screenshot`, `xcrun simctl ui booted appearance dark`), save them under
  `docs/redesign/ios/s1/` and list them in the report.
- `grep -ri 'star\|sparkle' iosApp/iosApp/DesignSystem` finds nothing.
