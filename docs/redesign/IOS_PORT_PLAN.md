# iOS port of the Android redesign (SwiftUI)

Owner decision 2026-11-14: everything the Android redesign has (features, screens, motion, store screenshots) comes to iOS,
**written natively in SwiftUI**, beautiful and smooth. Shared Kotlin stays the single source of logic and strings.

## Principles

1. **One logic, two UIs.** Android ViewModels, state models, copy (`ui/i18n/*Copy.kt`), `UiPrefs`, `ui/common`, builders
   (`HomeStateBuilder`, `LessonCardModel`, `GoToRules`, …) move to `commonMain` **keeping their packages** (`tj.app.quran_todo.ui.*`),
   so Android imports do not change. Android Compose screens stay in `androidMain`. Swift reads the same `StateFlow`s via SKIE.
2. **Platform services behind interfaces in commonMain** (playback, downloads, reminders, recordings, file transfer, storage space,
   review prompt, PDF report, haptics, widget refresh, locale). Android keeps its current implementations; iOS gets native ones
   (AVFoundation, URLSession background, UNUserNotificationCenter, AVAudioRecorder, UIDocumentPicker, UIGraphicsPDFRenderer,
   SKStoreReviewController / AppStore.requestReview, WidgetKit).
3. **SwiftUI, iOS 17+.** Deployment target goes from 15.3 to 17.0 (NavigationStack, `@Observable`, `sensoryFeedback`,
   `phaseAnimator`, `scrollTransition`, `matchedGeometryEffect`, `ContentUnavailableView`). Native idioms over literal copies:
   `TabView`, `NavigationStack` with swipe-back, `.sheet` with detents, `.swipeActions`, `.searchable`, `.refreshable`, context menus,
   Dynamic Type, VoiceOver labels, Reduce Motion. The visual language (colours, calligraphy, ring, cards) follows
   `docs/redesign/ANDROID_REDESIGN.md` and the Android theme (`ui/theme`, `ui/motion`, `ui/components`).
4. **No star motifs** anywhere (see `docs/redesign/tasks/P5-no-stars-calligraphy.md`): no SF Symbols `star*`, `sparkle*`,
   `wand.and.stars`. Calligraphy, mihrab arch, 12-lobed medallion, ogee lattice, rhombus only.
5. **Android must not regress.** Every Kotlin move keeps `./gradlew :composeApp:testDebugUnitTest` green and
   `:composeApp:assembleDebug` building. iOS must keep compiling: `:composeApp:compileKotlinIosSimulatorArm64`.
6. **Existing iOS users keep their data**: same Room DB (`commonMain`), legacy import through `HifzStartup`.

## Architecture on iOS

```
iosApp/iosApp/
  App/            QuranHifzApp.swift (entry), AppRouter (NavigationStack paths per tab), DI bridge (KoinHelper)
  Bridge/         Observable wrappers over Kotlin VMs (VMHolder<T>), Flow→AsyncSequence via SKIE, Kotlin type helpers
  DesignSystem/   Theme (colours/typography/shapes/spacing), Motion (springs, reduce-motion), Components, Ornaments
  Services/       Playback (AVPlayer + Now Playing + remote commands), Downloads, Reminders, Recording, Transfer, Review, PDF
  Features/       Onboarding, Home(LessonTab, QuranTab), Lesson, Reader, Mushaf, Progress, Plan, Khatm, Listen, Drill, Quiz,
                  Similar, Tafsir, Translation, Notes, Words, Settings, Downloads, About, WhatsNew, WelcomeBack
  Widget/         (separate WidgetKit extension target QuranHifzWidget, App Group shared snapshot)
```

- The Xcode project is converted to **file-system synchronized groups** (objectVersion 77) so parallel agents add Swift files
  without touching `project.pbxproj`.
- A Kotlin VM lives as long as its SwiftUI screen: `VMHolder` creates it from Koin on appear and calls `clear()` (via a
  `onCleared` bridge) when the view goes away. Shared singletons (`HomeViewModel`, `SurahViewModel`, `HifzRepository`) are Koin singles.
- Strings: Swift reads the Kotlin `*Copy` for the current `AppLanguage` (single source with Android). Only system strings
  (Info.plist usage descriptions, widget gallery) go into an `.xcstrings` catalog.

## Stages

| Stage | What | Depends on | Brief |
|---|---|---|---|
| S0 | Shared layer: VMs, copy, prefs, builders → commonMain; platform interfaces; SKIE; iOS Koin wiring | — | `tasks/IOS-S0-shared-layer.md` |
| S1 | Xcode modernisation (sync groups, iOS 17), SwiftUI design system, motion, components | — (parallel to S0) | `tasks/IOS-S1-design-system.md` |
| S2 | iOS platform services (playback, downloads, reminders, recording, transfer, review, PDF, haptics, locale) | S0 | `tasks/IOS-S2-services.md` |
| S3 | App shell: tab bar Урок/Коран/Прогресс, routing of every `Dest`, onboarding, what's new, welcome back | S0, S1 | `tasks/IOS-S3-shell.md` |
| S4 | Lesson flow: lesson card faces, lesson, recall bar, peeking, grading, listen step, summary, reminder ask | S2, S3 | `tasks/IOS-S4-lesson.md` |
| S5 | Reader + mushaf pages: ayah list, action row, player dock, go-to, view sheet, tajweed, translation, tafsir, notes, words, similar marks | S2, S3 | `tasks/IOS-S5-reader.md` |
| S6 | Progress, plan, khatm, listen old, drill, quiz, tell-apart, analytics, report PDF | S2, S3 | `tasks/IOS-S6-progress.md` |
| S7 | Settings, backup, downloads, reminders, about/sources/privacy, recordings; widget (WidgetKit) | S2, S3 | `tasks/IOS-S7-settings-widget.md` |
| S8 | QA on simulator (clean install, existing-user upgrade, 5 languages, dark mode, Dynamic Type), App Store screenshots in 5 languages | all | `tasks/IOS-S8-qa-screenshots.md` |

At most three implementation agents run at once (Mac memory). Each works in its own git worktree, starts with
`git merge --ff-only redesign/android`, commits on its branch; the orchestrator reviews, merges into `redesign/android`,
builds and walks the simulator.

## Definition of done (each screen)

- Same content, states and actions as the Android screen it ports (read the Compose file and its VM first).
- Light and dark, all five languages (ru, tg, uz, en, tr) without clipping, Dynamic Type up to XXL, VoiceOver labels.
- Motion: springs from `DesignSystem/Motion`, respects Reduce Motion; haptics via `Haptics` (respects the in-app toggle).
- Builds with `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' build`.
- Screenshot of the screen on the simulator attached to the agent's report (light + dark).

## Decisions (orchestrator, 2026-11-14; inventory: `docs/redesign/ios/SCREEN_INVENTORY.md`)

1. **Shared-element flights**: iOS 18+ uses `.navigationTransition(.zoom(sourceID:in:))` + `.matchedTransitionSource`;
   iOS 17 gets the native push. Inside one screen use `matchedGeometryEffect` freely.
2. **Player dock**: a custom overlay pinned with `.safeAreaInset(edge: .bottom)` (spring height change, drag to expand);
   the expanded player is a `.sheet` with `.presentationDetents([.medium, .large])`.
3. **Arabic text**: one shared component `QuranText` (Features/Common/QuranText) built on `Text` + `AttributedString`
   per word run (words as tappable runs through a `Layout`-based flowing word view for hidden/peek/seek/meaning taps,
   tajweed colour runs, word wash highlight). Built in S2b before the screens; every Arabic string goes through the Kotlin
   display helpers (`splitBismillah`, `hideRubSign`) so U+06DE never shows.
4. **Reminders**: schedule the next 14 days of local notifications (respecting cadence, pause, quiet days), reschedule on
   foreground, on `BGAppRefreshTask`, and after each graded lesson (today's is cancelled once the lesson is done).
5. **Downloads**: background `URLSession`; progress lives in the app (Downloads screen + Live Activity is out of scope).
6. **In-app update banner**: not on iOS (App Store updates). In-app review: `AppStore.requestReview` with the same gate.
7. Top-bar progress ring on the Quran tab: port only if `TopBarActions.ProgressAction` is still used on Android.

## Waves

- Wave 1 (running): S0, S1, inventory.
- Wave 2 (after S0+S1 merge, parallel): **S2** services, **S2b** QuranText + Recall core components, **S3** shell.
- Wave 3 (parallel, 3 agents): **A** = S7 (+ widget), **B** = S5 (reader; mushaf pages as its second half), **C** = S4 + S6.
- Wave 4: S8 QA + screenshots (and a fix pass in parallel per stage owner).
8. Reminder weekdays (owner, 2026-11-14): iOS keeps the day-of-week chips in reminder settings (`UiPrefs.reminderDays`), an
   iOS-only addition; Android keeps its daily reminder for now.
