package tj.app.quran_todo.ui.navigation

import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.ReaderLayout

/**
 * The single point that opens the reader (task F3a). Every entrance of the app (the home screen, "Continue", the juz, the search, the
 * reminder, "Open in the reader" of the quiz, the progress map, the mini player) asks for a [Dest.Reader] and hands it to
 * `NavController.openReader`, which sends it through [route]: with the reader in the mode "Pages" ([ReaderLayout.MUSHAF]) the same
 * request opens [Dest.Mushaf] on the page of the ayah, in the mode "Text" it stays what it was.
 *
 * Two requests stay in the text reader whatever the mode is: "Check yourself" of the lesson (`hifz = true`, which hides the ayahs of a
 * passage in the text) and a request for a range of ayahs (`rangeStart` / `rangeEnd`, the passages of the lesson and of "Listen to the
 * old"): both need the text.
 */
object ReaderRouting {
    /** A request for the reader that the mode "Pages" may show as a page: not the check of the lesson and not a passage. */
    fun canOpenAsPage(dest: Dest.Reader): Boolean = !dest.hifz && dest.rangeStart == null && dest.rangeEnd == null

    /**
     * Where [dest] goes in the mode [layout]. [lastRead] is where the person stopped reading: a surah opened with no ayah in the mode
     * "Pages" opens on the page of the place where they stopped in that surah (the text reader does the same), otherwise on the first
     * page of the surah. The ayah that was asked for is marked for a moment on the page (`ayahKey`).
     */
    fun route(dest: Dest.Reader, layout: ReaderLayout, lastRead: LastReadPosition? = null): Dest {
        if (layout != ReaderLayout.MUSHAF || !canOpenAsPage(dest)) return dest
        val resumed = lastRead?.takeIf { it.surah == dest.surah }?.ayahInSurah
        val ayah = (dest.ayah ?: resumed ?: 1).coerceAtLeast(1)
        if (!MushafPages.isAyah(dest.surah, ayah)) return dest
        return Dest.Mushaf(
            page = MushafPages.pageOf(dest.surah, ayah),
            ayahKey = dest.ayah?.let { "${dest.surah}:$it" },
        )
    }

    /**
     * Origin of a text reader opened from the pages: it matches no shared element of a list row or a card, so the reader simply slides
     * in (the way the reminder's and the onboarding's do).
     */
    const val FROM_PAGES = "mushaf"

    /** Origin of a reader opened by "Go to" from the title of another surah: it matches no shared element, so it simply slides in. */
    const val FROM_GOTO = "goto"

    /** The text reader on [ayah] of [surah]: what "Go to" asks for when the surah is another one (the mode is the text already). */
    fun goTo(surah: Int, ayah: Int): Dest.Reader = Dest.Reader(surah = surah, ayah = ayah, origin = FROM_GOTO)

    /** The text reader on the first ayah of [page]: what the switch "Pages to Text" and "Open as text" open. */
    fun textFor(page: Int): Dest.Reader {
        val first = MushafPages.firstAyahOf(page)
        return Dest.Reader(surah = first.surah, ayah = first.ayah, origin = FROM_PAGES)
    }

    /** The page on the first ayah that is on screen in the text reader: what the switch "Text to Pages" opens. */
    fun pagesFor(surah: Int, ayah: Int): Dest.Mushaf = Dest.Mushaf(page = MushafPages.pageOf(surah, ayah))
}
