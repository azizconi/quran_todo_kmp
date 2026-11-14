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
