# IOS-S0 — shared layer for the SwiftUI app

Read first: `docs/redesign/IOS_PORT_PLAN.md`, `AGENTS.md`, `composeApp/src/androidMain/kotlin/tj/app/quran_todo/ui/di/UiModules.kt`.

## Goal

Everything a screen needs except drawing becomes Kotlin in `commonMain`, so a SwiftUI app can show the exact same states and
call the exact same actions as the Android Compose screens. Android keeps working unchanged in behaviour.

## Work

1. **Move to commonMain, same package names** (`tj.app.quran_todo.ui.*` under `composeApp/src/commonMain/kotlin/...`):
   - all `ui/**/*ViewModel.kt` (20 files) and their state/model classes;
   - `ui/i18n/*` (all `*Copy.kt`), `ui/state/UiPrefs.kt`, `ui/common/*`, `ui/navigation/Dest.kt` (+ `ReaderRouting` logic if pure);
   - pure logic files next to screens: `HomeModels`, `HomeStateBuilder`, `LessonCardModel`, `QuranIndex`, `QuranMeta`,
     `SurahSearch`, `AyahActionRowState`, `GoToRules`, `ReaderPosition`, `MushafMarks`, `ReadingLine` (if pure), models in
     `ui/progress`, `ui/plan`, `ui/khatm`, `ui/quiz`, `ui/similar`, `ui/tajweed`, `ui/mushaf` (layout math), `ui/downloads`
     (`DownloadsManager` contract), `ui/recording` (`RecordingStore`/`VoiceController` contracts), `ui/settings` (`TransferIo`
     contract), `ui/whatsnew`, `ui/onboarding`, `ui/reminder` (copy + scheduling decisions, not AlarmManager).
   - A file that mixes models and `@Composable` code is split: models go to commonMain (`XxxModels.kt`), Compose stays.
   - `androidx.compose.runtime.Immutable/Stable` annotations are fine in commonMain (compose.runtime is a common dependency).
   - Unit tests in `androidUnitTest` that cover moved code may move to `commonTest` when they use no JVM-only API; otherwise leave them.
2. **Platform interfaces** (commonMain, `tj.app.quran_todo.ui.platform` or next to the feature) for every Android type a moved file
   used: `Context` → none (inject what is needed), `UiPrefs(context)` → backed by a common key–value store (`expect`/`actual` or
   the existing `UserSettingsStorage` pattern; iOS = `NSUserDefaults`, Android keeps the same SharedPreferences file and keys so
   no user loses settings), `StatFs` → `StorageSpace` interface, `Uri` → `String`/`PlatformFile` abstraction in `TransferIo`,
   `PlaybackController` → interface in commonMain (current Media3 class becomes `AndroidPlaybackController : PlaybackController`;
   keep `NowPlaying`, `ListenItem`, `ListenQueue`, word highlight state in common), review prompt, haptics, PDF report writer,
   widget refresh, reminder scheduler, locale applier, clock/day ticker if Android-only.
   Give every interface KDoc that says what the iOS implementation must do (S2 implements them).
3. **iOS stubs**: in `iosMain` provide minimal working or no-op implementations of each interface so the iOS framework links,
   and mark each with `// TODO(IOS-S2)`.
4. **Koin**: a common module `sharedUiModule` (commonMain) registers every moved ViewModel with `factory { params -> … }` (not
   `koinViewModel`), the same constructor arguments as `UiModules.kt`. Android `UiModules.kt` keeps `viewModel { }` declarations
   (it may delegate). iOS Koin (`iosMain/.../di/`) loads the common modules + iOS platform bindings.
5. **Swift access**:
   - Add **SKIE** (`co.touchlab.skie`, a version that supports Kotlin 2.1.21 — check the compatibility table) so `StateFlow`/`Flow`
     become Swift `AsyncSequence` and sealed classes/enums become exhaustive Swift enums. If SKIE cannot work with this Kotlin
     version, fall back to a small hand-written `FlowWatcher` (`fun <T> StateFlow<T>.watch(block: (T) -> Unit): Closeable`) and
     document why.
   - `iosMain/.../bridge/IosUi.kt`: `object IosUi` with one function per ViewModel (`fun reader(route: Dest.Reader): ReaderViewModel`,
     …) resolving from Koin, plus `fun clear(vm: ViewModel)` that triggers `onCleared` (use `ViewModelStore` per screen or call the
     protected clear through a helper), plus accessors for copy (`fun homeCopy(): HomeCopy` for the current language, and a
     `StateFlow<AppLanguage>`).
   - Export what Swift needs from the framework (`export(...)` only if required).
6. Do **not** touch `iosApp/` Swift files or the Xcode project (another agent owns them), and do not change Android screens'
   behaviour or look.

## Checks (all must pass, paste the tails in the report)

```
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```
(A stale KSP cache after moves: add `--rerun-tasks` to the failing task once.)

Install the debug APK on an emulator, clean data, open Home, a lesson and the reader once (see the emulator memory notes in the
repo docs / `docs/redesign/RELEASE.md`): no crash. SDK: `/Users/azizjon/Library/Android/sdk`, AVD `DoruIman_API_35`, set
`ANDROID_SERIAL` to the emulator you started.

## Report

List of moved files, new interfaces (name → what iOS must implement), SKIE version or fallback, test count before/after,
and anything you had to leave Android-only and why.
