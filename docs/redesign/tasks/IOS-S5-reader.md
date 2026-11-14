# IOS-S5 — reader, mushaf pages, marks and playback UI

Read: `docs/redesign/IOS_PORT_PLAN.md` (Principles, Decisions, Definition of done), `docs/redesign/ios/SCREEN_INVENTORY.md` (your sections, plus §0), and every Android file those sections name (Compose screen + VM + copy) before writing a view. Use `DesignSystem/`, `Features/Common/` (QuranText, recall core), `Bridge/` and `Services/`. Register each screen in the S3 `Router` in place of its `ComingSoon` placeholder. You own only the `Features/<yours>/` folders listed; if you need a shared component changed, write it in your report instead of editing it.

Folders: `Features/Reader/`, `Features/Mushaf/`, `Features/Tajweed/`, `Features/Translation/`, `Features/Tafsir/`, `Features/Notes/`, `Features/Words/`, `Features/Similar/`, `Features/Player/`, `Features/Recording/`.

Part 1 — text reader (§S5.1–S5.2, S5.4–S5.6, S5.8): ayah list on QuranText with bismillah header, scroll to/highlight ayah,
action row under a tapped ayah (Слушать / Повтор / Ещё) above the dock, "Проверьте себя" through the recall core, player dock
(Decision 2) with reciter, Повтор Нет/Аят/Отрывок, auto-advance, speed, word highlight, go-to sheet ("2:255"), "Вид текста" two-tab
sheet with marks legend, tajweed toggle, translation offer + editions + search, tafsir screen, notes (add/edit/delete, notes list),
word meanings popover, similar marks + tell-apart entry, mini player in the tab shell, own-voice recordings sheet, keep screen on.
Part 2 — mushaf pages (§S5.3): horizontal RTL pager of 604 pages (`TabView(.page)` or a paging `ScrollView` with
`.scrollTargetBehavior(.paging)`), per-page Madinah font or chosen font fitted per page as Android does, page marks, juz/hizb
headers, tap ayah → same action row, prefetch neighbour pages, smooth paging.
Check: open Al-Baqarah, play from 2:255 with the screen locked, repeat an ayah 3 times, go to page 300 in mushaf and swipe 20 pages
without hitches. Screenshots light/dark into `docs/redesign/ios/s5/`.
