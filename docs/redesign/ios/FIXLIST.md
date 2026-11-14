# iOS port — orchestrator fix list (open items found in review)

- [x] S2b: first-letters hint shows letters in isolated form; show the initial (joined) form like Android. Done by S4.
- [x] S2b demo: Al-Fatiha bismillah header follows Kotlin `splitBismillah` (reader uses it). Done in S5.
- [x] S2b: Madinah face U+06DF drawn as a dotted circle: the reader draws it as the sukun in the Madinah face (same length). Done in S5.
- [x] S1: `QhButton` hugs its content; add a full-width option and replace `RecallWideButton`. Done in S3: `QhButton(..., fullWidth: true)`, `RecallWideButton` deleted.
- [x] S1: switching from forced dark back to "system" may read a stale colour scheme. Done in S3: `QhThemeRoot` reads the device scheme from `SystemScheme` (window scene traits), not the environment.

## Orchestrator walk 2026-11-14 (QH-Orchestrator, ru, clean install)

Walked: onboarding (language → «С начала Корана» → nothing known) → Lesson tab → lesson Al-Fatiha (Listen, Recall: reveal/«Помню» ×7, grade «Хорошо, вернётся завтра») → summary («Урок выполнен», «Сура Аль-Фатиха пройдена полностью», reminder ask) → home → Progress tab. No crash, flows match Android.

- [ ] Onboarding: the 4th step («готово») was not seen — after «Пока ничего» the app went straight to the tabs (check whether Android also skips it when nothing is known, else S3 fix).
- [ ] Progress: «1 Дней подряд» — the label is capitalised mid-phrase and the plural doesn't agree with 1; use the copy's plural form like Android. Owner: S6.
- [ ] Progress: «Учу: 2 суры» after one finished Al-Fatiha lesson — compare with Android on the same state (it may count tomorrow's Al-Baqarah). Owner: S6.
- [ ] Simulator screenshots lag one action behind taps with the iOS simulator tool — not an app bug; wait ~1.5 s before a screenshot.

## Orchestrator walk 2 — 2026-11-14 (after S5, S6, S7 merges)

Walked by deep link: reader 2:255 (ayah tap → action row Слушать/Повтор/Ещё, dock), plan, khatm, quiz, analytics, drill, settings,
downloads. All render with real data, no crash.

Fix pass, agent **F1 (shell + settings)**:
- [x] Tab bar stays over pushed screens (plan «Сохранить», khatm, analytics, settings, downloads) — hide it on push like Android / iOS idiom (`.toolbar(.hidden, for: .tabBar)` or own bar hidden when the stack is not empty), with the spring.
- [x] Onboarding 4th step skipped after «Пока ничего» (compare with Android `OnboardingScreen`).
- [x] Shared `TrustCopy` privacy text speaks of Android — platform-specific lines for iOS (Kotlin copy, all 5 languages).
- [x] `SettingsLinks.appStoreId` empty — hide «Оценить» / share link until set, no dead button.
- [x] Reading font/size not observed reactively — reader and lesson must update when Aa changes.
- [x] Aref Ruqaa Ink missing in `QhQuranFace`; `TranslationSearchView` not embedded in the Quran tab search.
  - F1 notes: tab bar hidden on pushed routes (`.toolbar(.hidden, for: .tabBar)` in the navigation destination); onboarding 4th step was NOT skipped (walked: «Пока ничего» -> «План готов», same as Android; the earlier look was screenshot lag); `trustCopyIos` in Kotlin; rate row hidden while appStoreId is empty; `ReadingPrefs` is the single observable source for reader, lesson, drill, quiz and Settings; `QhQuranFace.ruqaa`; Quran tab search has the third tab «В переводе» (not walked on the simulator: the tool taps did not focus the search field).

Fix pass, agent **F2 (lesson + progress)**:
- [x] Progress: «1 Дней подряд» — plural from the copy like Android.
- [x] Progress: «Учу: 2 суры» after one finished Al-Fatiha — compare with Android state. Same as Android (shared `HifzStatuses`: Al-Fatiha covered = learning, plus the plan's current surah is learning); no change.
- [x] Lesson passage list lacks word highlight while playing and tajweed colours (reader has both through `QuranText`).
- [x] Lesson record button hidden — show it like Android (recording works on device).
- [x] Mushaf page check grades map by ayah number — wrong on pages with two surahs; key by (surah, ayah).

Fix pass, agent **F3 (reader + motion + reminders)**:
- [x] Reader play: verified on the simulator (F3): audio enqueues in AVPlayer, the dock shows pause, the word highlight follows, Now Playing info is set. The «…» is the preparing state while the first ayah is downloaded into the cache (no engine bug found); shots in f3/reader-*.png.
- [x] iOS 18 zoom transitions (lesson card → lesson, surah row → reader), `#available(iOS 18)`, iOS 17 push (Features/Shell/ZoomTransition.swift). Navigation verified; the animation itself not captured in a still.
- [x] Reminder weekdays: Android has no days/cadence setting, so added `UiPrefs.reminderDays` (bit mask, Mon..Sun) + `ReminderController.setDays` + `SettingsViewModel.setReminderDays`, localized names in `ReminderCopy`, iOS day chips in the reminder section, and `IosReminderPlanner` skips switched-off days over its 14-day horizon (absence tone still counts all days). Android keeps its daily alarm (not exposed there).
- [x] Mushaf swipe between pages, memorize mode, theme reveal walked on the simulator: all work, nothing to fix (f3/mushaf-*.png).
