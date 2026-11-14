# IOS-S8 — QA and App Store screenshots

Read `docs/redesign/IOS_PORT_PLAN.md` and the Android capture briefs `docs/redesign/tasks/PS1-capture-v2.md`, `PS2-screens-v3-lang.md`,
plus `play_store_assets/` (screens_v3, build_store_graphics.py, listing texts).

1. QA matrix on the simulator: clean install, upgrade from master build with data, 5 languages, light/dark, Dynamic Type XXL,
   VoiceOver labels on main flows, Reduce Motion, no-network. File each defect as a line in `docs/redesign/ios/QA.md` with screen,
   steps and a screenshot.
2. Screenshots: an XCUITest target (or `simctl` + launch arguments for seeded demo data, same demo data as Android capture) that
   captures the same scenes as the Android store set (lesson tab, action row, progress ring, test, reader, mushaf) in ru, tg, uz,
   en, tr on iPhone 6.9" (iPhone 17 Pro Max) and 6.5"; then frame them with the same marketing style as Android
   (`play_store_assets/build_store_graphics.py` adapted) into `play_store_assets/app_store_screenshots/<lang>/`.
