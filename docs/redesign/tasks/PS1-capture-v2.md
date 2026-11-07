# PS1 — fresh store captures of the 2.0 screens, all five languages

Goal: new raw emulator captures of the eight 2.0 store screens in **ru, en, tg, uz, tr**, made by a script that anyone can
re-run. The app has had more redesign fixes since the last captures, so every language is captured again (ru and en too).
Do not change app code (`composeApp/`). Only `play_store_assets/capture/` changes.

## Output

`play_store_assets/capture/raw/<lang>/` for lang in `ru en tg uz tr`, exactly these files, each 1080x2400 PNG:

| file | screen | what must be visible (see the current `raw/ru/*.png` as the reference of content and framing) |
|---|---|---|
| `01_home.png` | Home | "Lesson of the day" card with new / recent / old rows and the Start button, then "Check" row, "Continue" card |
| `02_reader.png` | Reader, ayah mode | Al-Mu'minun, ayah 18 playing (audio bar at the bottom), one word of ayah 18 highlighted, translation under each ayah |
| `03_mushaf.png` | Reader, mushaf pages | page 343 (or the page of Al-Mu'minun 18), full page of text, page number at the bottom |
| `04_tajweed.png` | Reader with tajweed colours on | ayahs 18–20 of Al-Mu'minun with coloured tajweed, translation under each |
| `05_tafsir.png` | Tafsir of 23:18 | the ayah card and the tafsir chips, tafsir text below |
| `06_plan.png` | My plan | "What I learn" chips (Juz Amma selected), order line, surah list with "now" badge |
| `07_details.png` | Progress > Details | retention block with the surah bars (percent + repetitions) |
| `08_quiz.png` | Quiz, "put in order" question | three ayah cards with numbers, Check button |

Delete the old E3 files in `raw/tg`, `raw/uz`, `raw/tr` (`02_recall`, `03_summary`, `04_plan`, `05_reader`, `06_progress`,
`07_ready`, `08_dark`) and any `*_top.png` leftovers, so every language folder has exactly the eight files above.

All languages must show the **same data and the same scroll position** (the layout of the screenshot template crops fixed
areas of these images — see `play_store_assets/screens_v3/build_v3.py`, `SCREENS[].pop`). Compare each new capture with
`raw/ru` side by side; the vertical position of the lesson card, the highlighted ayah 18 block, tajweed ayah 19, the tafsir
ayah card, the plan chips, the first two progress bars and the quiz cards should be within ~30 px of the ru capture.
Light theme. Clean status bar at 12:00 (`capture/demo_mode.sh`).

## How

- Emulator `DoruIman_API_35` (`~/Library/Android/sdk/emulator/emulator -avd DoruIman_API_35`, run it in the background;
  adb is `~/Library/Android/sdk/platform-tools/adb`, not on PATH). Install the debug build of this worktree:
  `./gradlew :composeApp:installDebug`.
- Demo data: `capture/make_demo_backup.py` (made for the day of the capture; set the emulator date to the same day) and/or
  `capture/seed_demo.py`. Read their docstrings and `capture/capture.py` first: `capture.py` already has the adb helpers,
  waiting on the accessibility tree, `step_setlang` (switch language without redoing the demo) and the E3 steps.
- Extend `capture.py` with the 2.0 steps (keep the helpers; the old E3 steps can stay or go) so that
  `python3 capture.py <lang>` produces the eight files above, and `run_all.sh` runs all five languages.
  Drive the UI by text from the language copy (`composeApp/src/commonMain/kotlin/**/ui/i18n/*Copy.kt`) or by stable positions;
  for things like "play ayah 18 and wait until a word of ayah 18 is highlighted", poll and retry rather than sleep blindly.
  If the word highlight cannot be caught reliably, pausing right after it starts is fine.
- Tafsir source: whatever the app offers by default for that language; Arabic and translation are what the app shows.
- Look at every PNG you produce (open it) before you call a language done. A loading spinner, a toast, a keyboard, a
  dialog, or a different scroll position means: redo that screen.

## Done when

- 40 PNGs (5 × 8) in place, checked by eye, consistent with ru framing.
- `capture.py` / `run_all.sh` updated, docstring says how to run the 2.0 set.
- Committed on your branch (short lowercase message, e.g. `store: capture 2.0 screens in all languages`), not pushed.
- Report: what you changed in the script, anything that differs between languages (e.g. a tafsir chip that does not exist
  in tg), and any screen you could not get right.
