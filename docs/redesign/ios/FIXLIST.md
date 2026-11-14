# iOS port — orchestrator fix list (open items found in review)

- [x] S2b: first-letters hint shows letters in isolated form; show the initial (joined) form like Android (append U+200D ZWJ, or the same approach as Android `HifzAyahContent`). Owner of fix: S4 agent (lesson) — `Features/Common/HifzAyahContent.swift`. Done in S4: U+200D appended in the QuranText overlay and in `RecallHints.preview`.
- [ ] S2b demo: Al-Fatiha shows the bismillah header and ayah 1 bismillah; the reader must follow Kotlin `splitBismillah` (no header for surah 1 and 9). Owner: S5 agent.
- [ ] S2b: Madinah face draws U+06DF as a dotted filled circle (2:5); compare with Android reader in the same font. Owner: S5 agent.
- [x] S1: `QhButton` hugs its content; add a full-width option and replace `RecallWideButton`. Done in S3: `QhButton(..., fullWidth: true)`, `RecallWideButton` deleted.
- [x] S1: switching from forced dark back to "system" may read a stale colour scheme. Done in S3: `QhThemeRoot` reads the device scheme from `SystemScheme` (window scene traits), not the environment.

## Orchestrator walk 2026-11-14 (QH-Orchestrator, ru, clean install)

Walked: onboarding (language → «С начала Корана» → nothing known) → Lesson tab → lesson Al-Fatiha (Listen, Recall: reveal/«Помню» ×7, grade «Хорошо, вернётся завтра») → summary («Урок выполнен», «Сура Аль-Фатиха пройдена полностью», reminder ask) → home → Progress tab. No crash, flows match Android.

- [ ] Onboarding: the 4th step («готово») was not seen — after «Пока ничего» the app went straight to the tabs (check whether Android also skips it when nothing is known, else S3 fix).
- [ ] Progress: «1 Дней подряд» — the label is capitalised mid-phrase and the plural doesn't agree with 1; use the copy's plural form like Android. Owner: S6.
- [ ] Progress: «Учу: 2 суры» after one finished Al-Fatiha lesson — compare with Android on the same state (it may count tomorrow's Al-Baqarah). Owner: S6.
- [ ] Simulator screenshots lag one action behind taps with the iOS simulator tool — not an app bug; wait ~1.5 s before a screenshot.
