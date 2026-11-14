# IOS-S2 — iOS platform services

Read: `docs/redesign/IOS_PORT_PLAN.md` (Decisions), `docs/redesign/ios/SCREEN_INVENTORY.md` §5.7, §8, and the S0 report section
"interfaces" (KDoc on each interface in commonMain marked `TODO(IOS-S2)` in iosMain stubs: `grep -rn "TODO(IOS-S2)" composeApp/src/iosMain`).

Implement every iOS stub for real. Kotlin `iosMain` where it is simple (Foundation/UIKit via cinterop), Swift in
`iosApp/iosApp/Services/` where Apple APIs are Swift-first (inject the Swift object into Koin through a Kotlin interface
registered at startup from `QuranHifzApp.init`).

- **Playback** (the `PlaybackController` interface; port behaviour of `ui/playback/*` exactly): `AVQueuePlayer` per-ayah items
  from the same URLs/cache keys as Android (12 reciters, `AyahAudioSource`), speed, repeat (none/ayah/passage, counts), echo mode
  with silences, listen queue (`startListenQueue`, heard/finished), auto-advance, word timings (`WordTimingsStore`, poll 60 ms via
  `addPeriodicTimeObserver`), sleep/stop, audio cache (reuse `AudioCache.ios.kt`, 1 GB LRU, pinned downloads never evicted).
  `AVAudioSession` `.playback`, `UIBackgroundModes audio`, `MPNowPlayingInfoCenter` (surah/ayah/reciter, artwork = app icon),
  `MPRemoteCommandCenter` (play/pause/next/prev ayah/seek), interruptions and route changes (pause on headphones out).
- **Downloads** (`DownloadsManager`): background `URLSession` (identifier, `handleEventsForBackgroundURLSession`), resume after
  relaunch, per-surah/reciter/translation/pages packs, storage space via `volumeAvailableCapacityForImportantUsage`, files excluded
  from iCloud backup (`isExcludedFromBackup`).
- **Reminders**: `UNUserNotificationCenter` per Decision 4, actions/deep link to the lesson (`Dest.Lesson`), permission request
  when the shared flow asks, `BGTaskScheduler` refresh (Info.plist `BGTaskSchedulerPermittedIdentifiers`).
- **Recording** (own voice): `AVAudioRecorder` m4a in `Application Support/recordings` excluded from backup, playback,
  delete; mic permission (`NSMicrophoneUsageDescription` in 5 languages via InfoPlist.xcstrings).
- **Transfer** (backup JSON export/import): `fileExporter`/`fileImporter`-friendly API (Kotlin gives bytes/filename, Swift presents).
- **Report PDF**: render the same content as `ReportPdf` with `UIGraphicsPDFRenderer`, share via `ShareLink`/`UIActivityViewController`.
- **Review prompt**: `AppStore.requestReview(in:)` behind the shared gate. No update banner.
- **Haptics** flag from `UiPrefs.hapticsEnabled`; **locale**: app language from shared prefs drives copy; set
  `AppleLanguages` only if needed for system sheets.
- **Analytics/Crashlytics**: keep current iOS telemetry, debug builds never send.
- **Widget refresh**: write the widget snapshot JSON to the App Group container `group.tj.app.quran-todo` and call
  `WidgetCenter.shared.reloadAllTimelines()` (the widget itself is S7).

Do not build screens. Write small Swift test harness screens only under `#if DEBUG` in `Features/Debug/`.
Checks: Kotlin `:composeApp:compileKotlinIosSimulatorArm64`, Android `:composeApp:testDebugUnitTest` (if you touch common),
xcodebuild for iPhone 17 simulator; on the simulator play a surah with the screen locked (Now Playing visible), schedule a reminder
1 minute ahead and see it fire, record and play back 5 s, export and import a backup. Report what you verified.
