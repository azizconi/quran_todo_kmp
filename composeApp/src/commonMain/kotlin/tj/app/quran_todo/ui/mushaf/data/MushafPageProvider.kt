package tj.app.quran_todo.ui.mushaf.data

import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.ui.downloads.PinnedPages

/** A page that can be drawn: its layout and the path of the file of its font (KFGQPC, one font per page; iOS registers it with CoreText). */
class LoadedPage(val page: MushafPage, val fontPath: String)

/**
 * Where the pages of the mushaf come from: memory, the pinned copy, the cache, the network (see `MushafPageStore`).
 * iOS (S2): the same order with files in Application Support (pinned) and Caches; the Swift side registers the font files with
 * `CTFontManagerRegisterFontsForURL` from [LoadedPage.fontPath].
 */
interface MushafPageProvider : PinnedPages {
    /** The layout and the font of [page] (1..604), or null when neither on the phone nor reachable. [force]: retry after a failure. */
    suspend fun load(page: Int, force: Boolean = false): LoadedPage?

    /** Fetches pages that are not on the device yet, one after the other (a quiet look ahead). */
    suspend fun prefetch(pages: Collection<Int>)
}
