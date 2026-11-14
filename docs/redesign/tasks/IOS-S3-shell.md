# IOS-S3 — app shell, navigation, onboarding, home tabs

Read: `docs/redesign/IOS_PORT_PLAN.md` (Decisions), `docs/redesign/ios/SCREEN_INVENTORY.md` §0 and §S3 (all of it), and the Android
files it names. Use the S1 design system (`DesignSystem/`) and the S0 bridge (`IosUi`, SKIE flows).

- `App/QuranHifzApp.swift` replaces `iOSApp.swift` as `@main` (Koin init, theme/palette/language from shared prefs applied to the
  whole tree with the circular theme reveal, scene phase hooks for reminders/widget). `Bridge/VMHolder` (create VM on appear,
  clear on disappear) and `Bridge/Copy` (current language copy as `@Observable`).
- Gate: onboarding vs main (same `OnboardingGate` logic), what's new sheet for existing users, welcome back.
- Main: native `TabView` Урок / Коран / Прогресс (SF Symbols without stars, labels from `TabsCopy`), each tab its own
  `NavigationStack(path:)`; a `Router` that maps every `Dest` (routing table §S3.2 incl. replace-in-stack rules) to a view —
  screens not built yet show a placeholder `ComingSoon(dest)`; deep links from notifications/widget (`quranhifz://lesson`, …).
- Onboarding (4 steps) with its transitions; Lesson tab (lesson card faces can be a placeholder card that S4 replaces —
  but the tab layout, plan row, continue reading, sheets are yours); Quran tab (surah list, juz tab, search `.searchable`,
  pins/recent, status change sheet, swipe actions / context menu); mini player slot above the tab bar (S5 fills it).
- Delete the legacy SwiftUI/Compose iOS entry (`Legacy/` folder, `ContentView`, `MainViewController` usage) once the new shell runs.

Check: clean install on iPhone 17 simulator goes through onboarding to the tabs in ru and en; an existing install (install the
current App Store-like build from `master` first, add some progress, then install yours) keeps data and sees what's new.
Screenshots light/dark into `docs/redesign/ios/s3/`.
