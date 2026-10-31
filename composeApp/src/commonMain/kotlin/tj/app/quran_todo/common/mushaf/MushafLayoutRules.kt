package tj.app.quran_todo.common.mushaf

import tj.app.quran_todo.data.bundled.QuranFacts

/**
 * Where the header of a surah and its Bismillah stand on a page. quran.com gives only the lines that have words; the lines without
 * words are these two (checked on the answers for all 604 pages, 2026-10-31):
 *
 *  - **The rule.** In front of the first ayah of a surah (the line where `verse 1, word 1` is) stand the **header** and then the
 *    **Bismillah**, one line each: the two lines above it. Al-Fatihah (page 1) and At-Tawbah (page 187) have a header only: the
 *    Bismillah of Al-Fatihah is its first ayah, At-Tawbah has none. Pages 1 and 2 have 8 lines, all the others 15.
 *  - **The exception, 21 times.** When a surah starts on the second line of a page, there is room for one line above it, not two:
 *    the header is then on the **last (15th) line of the previous page** and the Bismillah is the first line of the page. The page
 *    before such a surah has nothing on line 15 and ends on line 14. [headerAtBottom] lists these pages; the answers for all the
 *    others agree with the rule, there is no other exception. (Every other empty line of the 604 pages is a header or a Bismillah by
 *    the rule: the unit test `MushafLayoutRulesTest` goes through the shapes of all 604 pages.)
 */
object MushafLayoutRules {
    /** The first two pages have 8 lines. */
    const val SHORT_PAGES = 2
    const val SHORT_LINES = 8
    const val FULL_LINES = 15

    fun lineCount(page: Int): Int = if (page <= SHORT_PAGES) SHORT_LINES else FULL_LINES

    /**
     * The 199 ayahs (numbers in the whole Quran) that start a quarter of a hizb: the bundled text puts the sign U+06DE ("rub el hizb",
     * an eight-pointed star) in front of them. In the glyphs of quran.com the sign is not a word of its own: it is the **first glyph of
     * the first word** of the ayah (`code_v1` of two or three characters, the sign first, a space after it in 195 of them). The app draws
     * no stars (decision D4), so [withoutRubSign] cuts it off; `MushafLayoutRulesTest` checks this list against the signs of the text.
     */
    private val rubAyahNumbers = intArrayOf(
        33, 51, 67, 82, 99, 113, 131, 149, 165, 184, 196, 210, 226, 240, 250, 260,
        270, 279, 290, 308, 326, 345, 368, 386, 406, 426, 446, 464, 479, 505, 517, 529,
        551, 567, 581, 593, 607, 628, 641, 656, 681, 696, 710, 720, 736, 751, 766, 778,
        802, 825, 848, 863, 884, 900, 916, 930, 940, 985, 1001, 1019, 1042, 1071, 1096, 1110,
        1125, 1143, 1182, 1201, 1221, 1254, 1269, 1281, 1295, 1310, 1328, 1346, 1357, 1375, 1390, 1417,
        1435, 1454, 1479, 1497, 1514, 1534, 1557, 1581, 1603, 1626, 1649, 1673, 1697, 1712, 1726, 1742,
        1760, 1778, 1851, 1931, 1952, 1976, 1991, 2012, 2052, 2079, 2099, 2128, 2157, 2172, 2191, 2215,
        2239, 2272, 2309, 2403, 2431, 2459, 2512, 2534, 2566, 2614, 2633, 2655, 2709, 2748, 2812, 2826,
        2844, 2876, 2908, 2984, 3043, 3113, 3186, 3215, 3241, 3264, 3281, 3303, 3328, 3366, 3386, 3440,
        3463, 3491, 3514, 3551, 3564, 3584, 3593, 3616, 3630, 3652, 3675, 3701, 3733, 3765, 3810, 3871,
        3933, 3991, 4022, 4066, 4090, 4111, 4154, 4174, 4199, 4227, 4243, 4265, 4285, 4299, 4323, 4349,
        4382, 4431, 4485, 4531, 4555, 4578, 4601, 4626, 4657, 4706, 4759, 4810, 4855, 5054, 5091, 5118,
        5137, 5157, 5192, 5394, 5495, 5610, 6155,
    ).toSet()

    /** The glyph string of word [position] of [surah]:[ayah] without the star of the rub el hizb that the first word of 199 ayahs carries. */
    fun withoutRubSign(surah: Int, ayah: Int, position: Int, glyph: String): String {
        if (position != 1 || glyph.length < 2) return glyph
        if (QuranFacts.firstAyahNumber(surah) + ayah - 1 !in rubAyahNumbers) return glyph
        return glyph.substring(1).trimStart(' ').ifEmpty { glyph }
    }

    /** The surahs that have no Bismillah line in front of them: Al-Fatihah (it is its first ayah) and At-Tawbah. */
    fun hasBismillahLine(surah: Int): Boolean = surah != 1 && surah != 9

    /**
     * The pages whose last line holds the header of the surah that starts on the next page (page to surah). Al-Fatihah and At-Tawbah
     * are not here: their header is on their own page.
     */
    val headerAtBottom: Map<Int, Int> = mapOf(
        76 to 4, 207 to 10, 331 to 22, 341 to 23, 349 to 24, 366 to 26, 376 to 27, 414 to 32, 417 to 33, 445 to 37, 452 to 38,
        498 to 45, 506 to 47, 525 to 53, 548 to 60, 555 to 64, 557 to 65, 584 to 80, 586 to 82, 590 to 86, 594 to 91,
    )

    /**
     * The lines of [page] from its lines of text [wordLines] (line number to words, in reading order): the same lines plus the header
     * and the Bismillah of every surah that starts on the page, in the places of the rule above, sorted by line number.
     */
    fun arrange(page: Int, wordLines: Map<Int, List<MushafWord>>): List<MushafLine> {
        val count = lineCount(page)
        val lines = HashMap<Int, MushafLine>()
        for ((number, words) in wordLines) lines[number] = MushafLine.Words(number, words)

        fun put(line: MushafLine) {
            if (line.number in 1..count && line.number !in lines) lines[line.number] = line
        }

        for ((number, words) in wordLines) {
            val first = words.firstOrNull() ?: continue
            if (first.ayah != 1 || first.position != 1) continue
            val surah = first.surah
            when {
                !hasBismillahLine(surah) -> put(MushafLine.SurahHeader(number - 1, surah))
                // The header stayed on the last line of the previous page; only the Bismillah is here.
                headerAtBottom[page - 1] == surah -> put(MushafLine.Bismillah(number - 1, surah))
                else -> {
                    put(MushafLine.SurahHeader(number - 2, surah))
                    put(MushafLine.Bismillah(number - 1, surah))
                }
            }
        }
        headerAtBottom[page]?.let { surah -> put(MushafLine.SurahHeader(count, surah)) }
        return lines.values.sortedBy { it.number }
    }
}
