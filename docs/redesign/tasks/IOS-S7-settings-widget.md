# IOS-S7 — settings, backup, downloads, reminders, about, widget

Read: `docs/redesign/IOS_PORT_PLAN.md` (Principles, Decisions, Definition of done), `docs/redesign/ios/SCREEN_INVENTORY.md` (your sections, plus §0), and every Android file those sections name (Compose screen + VM + copy) before writing a view. Use `DesignSystem/`, `Features/Common/` (QuranText, recall core), `Bridge/` and `Services/`. Register each screen in the S3 `Router` in place of its `ComingSoon` placeholder. You own only the `Features/<yours>/` folders listed; if you need a shared component changed, write it in your report instead of editing it.

Folders: `Features/Settings/`, `Features/Downloads/`, `Features/About/`, `Features/Reminder/`, and a new WidgetKit extension
target `QuranHifzWidget` (create it in the Xcode project: small/medium/accessory lock-screen families, App Group
`group.tj.app.quran-todo` on app + widget, reads the snapshot S2 writes, deep link to the lesson, look of Android `ui/widget`).

Settings (~25 rows in sections as Android U27) in a native `List`/`Form` styled with the design system: theme mode + palette with
the circular reveal, app language, reading font + Arabic size, haptics, reduce motion, reminders (time, days, cadence), audio
(autoplay, reciter), backup sheet (export/import JSON via Services transfer), downloads & storage, analytics toggle, privacy,
sources, about, contact. Replace the legacy `NativeSettingsScreen`. Downloads screen with per-pack progress and delete.
Check: change every setting and see it applied app-wide; add the widget on the simulator home screen and lock screen;
export, reinstall, import. Screenshots into `docs/redesign/ios/s7/`.
