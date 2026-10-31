package tj.app.quran_todo.ui.downloads

/**
 * Size of the mushaf pages before they are downloaded (task F3a): a page is its font (KFGQPC, `p{page}.ttf`) and the compact copy of its
 * layout, and the estimate is a number of bytes per page that depends on where the page is ([bytesOfPage]).
 *
 * **How the numbers were measured** (2026-10-31): the size of the font of every one of the 604 pages from `verses.quran.com` (the length
 * of the body, which matched `Content-Length` of all 604 answers) and the size of the compact copy of every layout (what
 * `MushafLayoutFormat.encode` writes) from the answers of `api.quran.com/api/v4/verses/by_page` of all 604 pages. The raw answer of
 * quran.com is 42 KB per page on average (59.8 KB at most); the app keeps the compact copy, 2.7 KB on average (0.7 to 3.6 KB).
 *
 * | what | per page, bytes |
 * |---|---|
 * | the font, mean of the 604 pages (27 236 B on page 1, 176 744 B at most) | 157 063 |
 * | the compact copy of the layout, mean | 2 703 |
 * | pages 1 and 2 (8 lines, small fonts: 27 236 and 83 428 B), mean of font and copy | 56 046 |
 * | pages 3 to 581, mean of font and copy | 160 767 |
 * | pages 582 to 604 (Juz 30: short surahs, fewer glyphs), mean of font and copy | 143 585 |
 *
 * All 604 pages are 96 498 614 B (92.0 MiB). Estimated by the three rates above, a juz is off by 1.6% on average and by 4.6% at most
 * (Juz 27); a juz of 20 pages is about 3.0 MiB, Juz 30 (23 pages) 3.1 MiB. One rate for all pages would be off by 11% for Juz 1 and Juz 30
 * (pages 1 and 2 and the pages of Juz 30 have smaller fonts), which is why these two have their own.
 */
internal object PagesSize {
    private const val FIRST_TWO_PAGES_BYTES = 56_046L
    private const val JUZ_AMMA_PAGES_BYTES = 143_585L
    private const val OTHER_PAGES_BYTES = 160_767L

    /** The first page of Juz 30. */
    private const val FIRST_PAGE_OF_JUZ_AMMA = 582

    /** The estimated bytes of page [page] (1..604): its font and its layout copy. */
    fun bytesOfPage(page: Int): Long = when {
        page <= 2 -> FIRST_TWO_PAGES_BYTES
        page >= FIRST_PAGE_OF_JUZ_AMMA -> JUZ_AMMA_PAGES_BYTES
        else -> OTHER_PAGES_BYTES
    }

    /** The estimated bytes of [pages]. */
    fun estimateBytes(pages: Collection<Int>): Long = pages.sumOf(::bytesOfPage)

    /**
     * The estimated bytes still to download for the pages requests among [requests]: every page that is not downloaded yet, once, even
     * when "All 604 pages" and "Pages of juz 30" are both asked for. [have] are the downloaded files (the keys of the pages are
     * [PageKeys]).
     */
    fun remainingBytes(requests: List<DownloadRequest>, have: Map<String, Long>): Long {
        val counted = HashSet<Int>()
        var total = 0L
        for (request in requests) {
            if (request.kind != DownloadKind.PAGES) continue
            for (page in request.pages) {
                if (PageKeys.of(page) in have || !counted.add(page)) continue
                total += bytesOfPage(page)
            }
        }
        return total
    }
}
