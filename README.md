This is a Kotlin Multiplatform project targeting Android, iOS.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## Firebase (Android)

- Firebase Analytics and Crashlytics are integrated in `composeApp`.
- Add your Firebase config file to:
  - `composeApp/google-services.json`
- Then build/run Android:
  - `./gradlew :composeApp:assembleDebug`

Notes:
- Shared `AppTelemetry` API is available in `commonMain`; Android sends events/errors to Firebase.
- iOS currently uses a no-op telemetry implementation. If you want Firebase on iOS too, add Firebase SDK in `iosApp` and connect it there.
