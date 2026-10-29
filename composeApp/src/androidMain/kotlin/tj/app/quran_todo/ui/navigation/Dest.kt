package tj.app.quran_todo.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations of the Android app. See docs/redesign/ANDROID_REDESIGN.md, section 4.1. */
sealed interface Dest {
    @Serializable
    data object Onboarding : Dest

    @Serializable
    data object Home : Dest

    @Serializable
    data class Reader(
        val surah: Int,
        /** Ayah number inside the surah to scroll to and highlight. */
        val ayah: Int? = null,
        /** Where the reader was opened from; part of shared element keys (see SharedKeys). */
        val origin: String = Origin.LIST,
        /** Open directly in memorize mode. */
        val hifz: Boolean = false,
        val rangeStart: Int? = null,
        val rangeEnd: Int? = null,
    ) : Dest

    @Serializable
    data object Progress : Dest

    @Serializable
    data object Settings : Dest

    /** Short privacy summary, opened from the settings. */
    @Serializable
    data object Privacy : Dest

    /** Sources and licenses of the text, translations, audio, fonts and libraries; opened from the settings. */
    @Serializable
    data object Sources : Dest

    /** Debug-only component catalog. */
    @Serializable
    data object Catalog : Dest
}

object Origin {
    const val HERO = "hero"
    const val LIST = "list"
    const val RECENT = "recent"
    const val TODAY = "today"
    const val SEARCH = "search"
    const val MAP = "map"
    const val PLAYER = "player"
    const val NEXT = "next"
}
