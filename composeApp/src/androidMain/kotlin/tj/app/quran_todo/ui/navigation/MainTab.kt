package tj.app.quran_todo.ui.navigation

import tj.app.quran_todo.ui.home.HomeStateBuilder

/**
 * The three top-level tabs of the bottom bar (UX task U14), all living in the one destination [Dest.Home]. The order is the
 * order of the bar.
 */
enum class MainTab(
    /** The `logScreen` name while the tab is on screen. */
    val screenName: String,
) {
    Lesson("home"),
    Quran("quran"),
    Progress("progress"),
    ;

    companion object {
        /**
         * The tab that opens first, and where system back leads from the other tabs: the lesson for the goal "memorize" or no goal
         * (the same rule that puts the lesson card above the "Continue" hero used to), the Quran for "read" / "listen".
         */
        fun defaultFor(goals: Set<String>): MainTab = if (HomeStateBuilder.lessonFirst(goals)) Lesson else Quran

        /** The tab saved as an ordinal (a rotation, a process death), or [default] when nothing valid was saved. */
        fun restore(saved: Int?, default: MainTab): MainTab = saved?.let { entries.getOrNull(it) } ?: default

        /**
         * What system back does on [selected]: the tab it leads to, or null when the app should be left (the default tab is the
         * root of the tabs).
         */
        fun backTarget(selected: MainTab, default: MainTab): MainTab? = default.takeIf { selected != default }
    }
}
