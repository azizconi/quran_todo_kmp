Repository: quran_todo_kmp, branch `redesign/android`. Do not push and do not switch branches.

Task: put the new Google Play "What's new" texts for release 2.0.0 into the store listing files.

Source of the texts: `play_store_assets/RELEASE_2.0.0.md`, the code block in the section "Что нового — блок для вставки в Play Console". It holds five blocks: `<ru-RU>`, `<en-US>`, `<tg>`, `<uz>`, `<tr-TR>`.

Steps:
1. For each language, open `play_store_assets/listing/<code>.md` (`ru-RU.md`, `en-US.md`, `tg.md`, `uz.md`, `tr-TR.md`).
2. Replace the whole body of the `## What's new` section with the text between that language's tags. Copy it character for character: keep the line breaks, the `•` bullets, the `—` dashes and the `‘` in Uzbek. Do not include the tags.
3. Change nothing else in these files: not the title, short description, full description or any other section.
4. Run `python3 play_store_assets/listing/check_listing.py`. It must exit with code 0. Every "What's new" must be at most 500 characters. No emoji and no star glyphs are allowed anywhere (the owner forbids star shapes).
5. In `docs/redesign/RELEASE.md`, section 4 "Файлы для носителей языка (R2)", add one line: the new "What's new" texts are in `play_store_assets/RELEASE_2.0.0.md` and in the listing files; tg, uz and tr-TR need a native-speaker check.
6. Commit with the message `store: new what's new texts for 2.0.0`. Do not push.

Do not edit app code, `WhatsNewCopy.kt`, versions or Gradle files. If a listing file has no `## What's new` section or the check fails, stop and report the problem instead of guessing.

Report: the files you changed, the output of `check_listing.py`, and the commit hash.
