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

    /**
     * The mushaf as pages (task F3a): the page of the Madani mushaf [page] (1..604) in the layout of quran.com. [ayahKey] ("112:3") is the
     * ayah to mark for a moment, the one a search result or "continue" pointed at.
     */
    @Serializable
    data class Mushaf(
        val page: Int,
        val ayahKey: String? = null,
    ) : Dest

    /** Today's lesson: new, recent and old passages, then the summary (task B2). */
    @Serializable
    data object Lesson : Dest

    /** "My plan": what is memorized, in which order and at what pace (task B3). */
    @Serializable
    data object Plan : Dest

    /** The quiz "Check": five questions on what is memorized; nothing goes to the review log (task F2b). */
    @Serializable
    data object Quiz : Dest

    @Serializable
    data object Progress : Dest

    @Serializable
    data object Settings : Dest

    /** "Downloads and storage": audio and translations on the phone, the download queue, the listening cache; opened from the settings (task D3). */
    @Serializable
    data object Downloads : Dest

    /** The drill of the difficult ayahs: the ayahs marked as hard, one by one, at most 20 at a time (task F2a); opened from "Progress" and the end of the lesson. */
    @Serializable
    data object HardDrill : Dest

    /** Short privacy summary, opened from the settings. */
    @Serializable
    data object Privacy : Dest

    /** Sources and licenses of the text, translations, audio, fonts and libraries; opened from the settings. */
    @Serializable
    data object Sources : Dest

    /**
     * The commentary (tafsir) of one ayah as a full screen (task F4b), opened from the ayah sheet of the text reader and of the pages.
     * [tafsirId] is a `TafsirSource.key` to open on (null: the one the person chose last, else the one of the language of the app).
     */
    @Serializable
    data class Tafsir(
        val surah: Int,
        val ayah: Int,
        val tafsirId: String? = null,
    ) : Dest

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
