# IOS-S2b — QuranText and the recall core (shared SwiftUI components)

Read: `docs/redesign/IOS_PORT_PLAN.md` (Decision 3), `docs/redesign/ios/SCREEN_INVENTORY.md` §S4.5, §S5.1, §S5.4, §S5.5, and Android
`ui/hifz/*` (`HifzAyahContent`, `RecallPassage`, `RecallBottomBar`, hint modes), `ui/reader/components/*` (ayah rendering,
word highlight, tajweed spans), `ui/tajweed/*`, `ui/words/*`.

Build in `iosApp/iosApp/Features/Common/`:
1. `QuranText`: renders one ayah (or a run of ayahs) word by word, RTL, chosen reading font and Arabic scale, ayah-end marker,
   tajweed colour runs (from the shared tajweed alignment), current-word wash (word timings), tap on a word → callback (seek,
   meaning popover, peek), hidden words per `HifzHintMode` (5 levels: first letters, first word, blanks, …) with peek reveal
   animation, similar-verse marks, selection/highlight of the current ayah. Smooth at 60/120 fps in a `LazyVStack` of 286 ayahs
   (measure: scroll Al-Baqarah on the simulator, no hitches; cache layout per ayah).
   Always pass text through the Kotlin display helpers so U+06DE (rub el hizb) never renders.
2. `HifzAyahContent`, `RecallPassage`, `RecallBottomBar` (pinned recall bar, tap-to-reveal, grade row, "Изменить" grade sheet),
   ported 1:1 from Android with the same VM calls; used later by the lesson (S4), the reader "Проверьте себя" (S5), the drill (S6).
3. Previews + a `#if DEBUG` demo screen with Al-Fatiha and Al-Baqarah 1–20 in all hint modes, light/dark.

You own only `Features/Common/` and `Features/Debug/` files you create. Check: xcodebuild for iPhone 17; screenshots of each
hint mode and tajweed into `docs/redesign/ios/s2b/`.
