package tj.app.quran_todo.common.mushaf

/** An ayah by the surah and its number in the surah; the text form "112:3" is what `Dest.Mushaf.ayahKey` carries. */
data class AyahKey(val surah: Int, val ayah: Int) {
    /** "112:3". */
    val text: String get() = "$surah:$ayah"

    companion object {
        /** Reads "112:3"; null for anything else (a missing key, a non-number, a surah or ayah that does not exist). */
        fun parse(text: String?): AyahKey? {
            val parts = text?.split(':') ?: return null
            if (parts.size != 2) return null
            val surah = parts[0].toIntOrNull() ?: return null
            val ayah = parts[1].toIntOrNull() ?: return null
            return AyahKey(surah, ayah).takeIf { MushafPages.isAyah(it.surah, it.ayah) }
        }
    }
}

/**
 * One word of a page: [glyph] is what the font of the page draws for it (the `code_v1` of quran.com: one private-use character, a few
 * words have two to four), [position] counts from 1 in the words of the ayah. The sign that closes an ayah ([isAyahEnd]) is the
 * word after the last one, its glyph is the round marker with the ayah number.
 */
data class MushafWord(
    val surah: Int,
    val ayah: Int,
    val position: Int,
    val glyph: String,
    val isAyahEnd: Boolean,
) {
    val key: AyahKey get() = AyahKey(surah, ayah)
}

/**
 * A line of a page. [number] is its place on the page, 1..[MushafPage.lineCount]: lines of one page are never renumbered, so a
 * place that holds nothing (it does not happen in the printed mushaf, but a damaged copy could have one) simply stays empty.
 */
sealed interface MushafLine {
    val number: Int

    /** A line of text: the words in reading order, the first one is read first and stands at the right. */
    data class Words(override val number: Int, val words: List<MushafWord>) : MushafLine

    /** The frame with the name of the surah that starts here ("Surah Al-Baqarah"). */
    data class SurahHeader(override val number: Int, val surah: Int) : MushafLine

    /** The Bismillah that opens a surah (not drawn for Al-Fatihah, where it is the first ayah, and At-Tawbah, which has none). */
    data class Bismillah(override val number: Int, val surah: Int) : MushafLine
}

/** A part of one surah on a page: the ayahs [first]..[last] of [surah] that are on it (the unit "Check yourself" works with). */
data class SurahPart(val surah: Int, val first: Int, val last: Int) {
    val count: Int get() = last - first + 1
}

/**
 * One page of the Madani mushaf (604 pages, 15 lines; the first two pages have 8) in the layout of quran.com: the lines in order,
 * the words of the lines, the headers and the Bismillahs of the surahs that start on the page.
 *
 * In the printed mushaf an ayah never goes over to the next page (every page ends where an ayah ends; checked on all 604 pages),
 * so every ayah is whole on the page where it starts.
 */
class MushafPage(val number: Int, val lines: List<MushafLine>) {
    /** The lines of text only, top to bottom. */
    val wordLines: List<MushafLine.Words> = lines.filterIsInstance<MushafLine.Words>()

    /** The ayahs on the page in reading order, each once. */
    val ayahs: List<AyahKey> = buildList {
        var last: AyahKey? = null
        for (line in wordLines) for (word in line.words) {
            val key = word.key
            if (key != last) {
                add(key)
                last = key
            }
        }
    }

    /** The first ayah on the page, null for a page without words. */
    val firstAyah: AyahKey? get() = ayahs.firstOrNull()

    /** The surahs of the page in order, with the first and the last ayah of each that are on the page. */
    val parts: List<SurahPart> = buildList {
        for (key in ayahs) {
            val previous = lastOrNull()
            if (previous != null && previous.surah == key.surah) {
                this[lastIndex] = previous.copy(last = key.ayah)
            } else {
                add(SurahPart(key.surah, key.ayah, key.ayah))
            }
        }
    }

    /** The surah of the first line of text: what the top margin names. */
    val firstSurah: Int? get() = parts.firstOrNull()?.surah

    /** Number of line places on the page: 8 on pages 1 and 2, otherwise 15. */
    val lineCount: Int get() = MushafLayoutRules.lineCount(number)

    override fun equals(other: Any?): Boolean = other is MushafPage && other.number == number && other.lines == lines

    override fun hashCode(): Int = number * 31 + lines.hashCode()

    override fun toString(): String = "MushafPage($number, ${lines.size} lines)"
}
