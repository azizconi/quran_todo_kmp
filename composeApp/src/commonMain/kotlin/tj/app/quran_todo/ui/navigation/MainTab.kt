package tj.app.quran_todo.ui.navigation

/**
 * The three top-level tabs of the bottom bar (UX task U14), all living in the one destination [Dest.Home]. The order is the
 * order of the bar: the Quran is home, the lesson of the day is the second tab (and a compact card on top of the Quran tab).
 */
enum class MainTab(
    /** The `logScreen` name while the tab is on screen. */
    val screenName: String,
) {
    Quran("quran"),
    Lesson("home"),
    Progress("progress"),
    ;

    companion object {
        /**
         * The tab that opens first, and where system back leads from the other tabs: always the Quran (the home of the app), whatever
         * goal onboarding chose.
         */
        val Default: MainTab = Quran

        /** The tab saved as an ordinal (a rotation, a process death), or [default] when nothing valid was saved. */
        fun restore(saved: Int?, default: MainTab): MainTab = saved?.let { entries.getOrNull(it) } ?: default

        /**
         * What system back does on [selected]: the tab it leads to, or null when the app should be left (the default tab is the
         * root of the tabs).
         */
        fun backTarget(selected: MainTab, default: MainTab): MainTab? = default.takeIf { selected != default }
    }
}
