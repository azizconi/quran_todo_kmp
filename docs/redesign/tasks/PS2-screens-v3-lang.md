# PS2 — Google Play screenshots v3 for one language

One agent per language (`ru`, `en`, `tg`, `uz`, `tr`). The template is approved by the owner: do not change its look.

## Inputs

- Template: `play_store_assets/screens_v3/build_v3.py` (do not edit it; if something can only be fixed there, say so in the report).
- Approved sample: `play_store_assets/screens_v3/sample/` (ru) — this is how every language must look.
- Texts: `play_store_assets/screens_v3/copy/<lang>.json`. `ru.json` and `en.json` exist; for `tg`, `uz`, `tr` create the file
  with the same structure.
- Captures: `play_store_assets/capture/raw/<lang>/01_home.png … 08_quiz.png` (fresh 2.0 captures, 1080x2400).
- Existing store wording for your language (tone, terms): `play_store_assets/listing/<locale>.md`,
  `play_store_assets/RELEASE_2.0.0.md`, and the app copy `composeApp/src/commonMain/kotlin/**/ui/i18n/*Copy.kt` — use the
  same terms the app uses (e.g. the app's word for "lesson", "plan", "quiz", "mushaf", "tafsir", "tajweed").

## Texts (copy/<lang>.json)

Per screen: `word` — ONE short word, the screen's topic, shown huge with a coloured dot (no dot in the text itself);
`sub1` / `sub2` — two short lines (benefit; sub2 is shown in the accent colour); screen 1 also has `pill` — "FREE · NO ADS"
in capitals in your language. Meaning per screen (from en.json): Lesson / Listen (12 reciters, word highlighting) / Mushaf /
Tajweed / Tafsir / Plan (pace and finish date) / Progress / Quiz (tests and similar ayahs).
Rules: no emoji, no star symbols, no Arabic script in the texts (the renderer cannot shape it), only true claims (all
features are free). Keep each sub line under ~28 characters so it stays large. For `tg`, `uz`, `tr` the owner will have a
native speaker check later — write natural, simple language, not word-for-word English.

## Render and check

```
cd play_store_assets
python3 screens_v3/build_v3.py <lang> --out ../play_store_assets/localized_v3_tmp      # or any scratch dir
```

Open EVERY output PNG and check:
1. The huge word fits with its dot fully visible, nothing touches the right edge; subtitle lines are not cut.
2. The lifted card (`pop`) shows a complete UI element of THIS language's capture: no half-cut text rows at its top or
   bottom edge, no row of the next element peeking in. If the capture of your language is laid out a little differently
   (longer text wraps to another line), set `"override": {"pop": [x0, y0, x1, y1]}` for that screen in `copy/<lang>.json`
   (raw 1080x2400 pixels) until it is clean. Do not change `tilt` or `accent`.
3. The screen inside the device is the right screen, light theme, no dialogs / spinners.

If a word is too long and the headline shrinks a lot (it auto-fits between 120 and 236 px), prefer a shorter synonym.

## Output

When all 8 are clean, write them to the store folder (replaces the old set):

```
rm -f localized/<locale>/phone/*.png
python3 screens_v3/build_v3.py <lang> --out localized
```

Locales: ru → ru-RU, en → en-US, tg → tg, uz → uz, tr → tr-TR. Do not touch other languages' files, the manifest, or the
feature graphic. Commit on your branch: `copy/<lang>.json` and `localized/<locale>/phone/*.png` only (short lowercase
message), do not push.

Report: the eight headlines (word + two lines), any pop overrides, anything you could not fix.
