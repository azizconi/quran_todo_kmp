# IOS-S4 + S6 — lesson flow, progress and practice

Read: `docs/redesign/IOS_PORT_PLAN.md` (Principles, Decisions, Definition of done), `docs/redesign/ios/SCREEN_INVENTORY.md` (your sections, plus §0), and every Android file those sections name (Compose screen + VM + copy) before writing a view. Use `DesignSystem/`, `Features/Common/` (QuranText, recall core), `Bridge/` and `Services/`. Register each screen in the S3 `Router` in place of its `ComingSoon` placeholder. You own only the `Features/<yours>/` folders listed; if you need a shared component changed, write it in your report instead of editing it.

Folders: `Features/Lesson/`, `Features/Progress/`, `Features/Plan/`, `Features/Khatm/`, `Features/Listen/`, `Features/Drill/`, `Features/Quiz/`.

Order: S4 first (lesson card faces on the Lesson tab replacing the S3 placeholder card; lesson screen with new/recent/old
steps, new-passage phases, review card, recall with hints/peeks/grading, listen step, translation chip, summary with the calm
celebration, reminder ask on the first summary, review prompt gate), then S6 (progress tab four blocks + ring + streak + week +
hard places, analytics, teacher report PDF via Services, plan with drag reorder (native `.onMove`/drag with edge auto-scroll),
pause, khatm + Ramadan card, listen old sheet with echo ring, hard drill, quiz and tell-apart).
Animations to match Android exactly where the inventory lists them (counters, ring fill, stagger, card face changes, celebration).
Check on the iPhone 17 simulator: a full lesson from a fresh plan to the summary in ru, then progress shows it. Screenshots light/dark into `docs/redesign/ios/s4/` and `s6/`.
