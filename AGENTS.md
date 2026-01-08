# Repository Guidelines

## Project Structure & Module Organization
- `composeApp/` is the shared Kotlin Multiplatform module.
  - `composeApp/src/commonMain/` holds shared Compose UI and logic (`kotlin/`) plus resources in `composeResources/`.
  - `composeApp/src/androidMain/` contains Android-specific code, `AndroidManifest.xml`, and `res/`.
  - `composeApp/src/iosMain/` contains iOS-specific Kotlin code.
- `iosApp/` is the native iOS entry point for Xcode/SwiftUI glue.
- Gradle configuration lives in `build.gradle.kts`, `composeApp/build.gradle.kts`, and `settings.gradle.kts`.
- Room schemas are generated under `composeApp/schemas/`.

## Build, Test, and Development Commands
- `./gradlew :composeApp:assembleDebug` builds the Android debug APK.
- `./gradlew :composeApp:installDebug` installs the debug build on a connected device/emulator.
- `./gradlew build` runs the full Gradle build (all targets configured for the host).
- iOS: open `iosApp/` in Xcode and run the iOS scheme for device/simulator.

## Coding Style & Naming Conventions
- Kotlin code uses standard Kotlin formatting (4 spaces, braces on the same line) and Compose idioms.
- Package naming follows `tj.app.quran_todo` (lowercase with underscores where needed).
- Use PascalCase for classes/composables (e.g., `HomeScreen`), camelCase for functions/vars.

## Testing Guidelines
- No automated tests are currently present.
- When adding tests, place shared tests in `composeApp/src/commonTest/` and platform tests in `androidTest/` or `iosTest/`.
- Typical command: `./gradlew test` (or `./gradlew :composeApp:allTests` for KMP-wide runs).

## Commit & Pull Request Guidelines
- Commit history suggests short, descriptive summaries (often lowercase, sometimes with bullet-style clauses). Keep messages brief and action-oriented.
- PRs should include: a concise description, affected platforms (Android/iOS), and screenshots or screen recordings for UI changes.
- Link related issues or tasks when applicable and mention any schema changes in `composeApp/schemas/`.

## Configuration Notes
- Ensure `local.properties` points to a valid Android SDK path.
- iOS builds require Xcode and CocoaPods tooling as needed by the KMP setup.
