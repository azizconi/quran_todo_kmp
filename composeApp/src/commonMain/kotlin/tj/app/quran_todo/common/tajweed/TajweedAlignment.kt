package tj.app.quran_todo.common.tajweed

/**
 * Moves the rules of the tajweed markup onto the bundled text of the app (task F3b).
 *
 * The markup comes with a text of its own, and that text is not ours: after the markup is taken off, only 774 of the 6236 ayahs equal the
 * bundled `quran-uthmani` text, and 1278 after the two commonest differences (tatweel, U+0672 for U+0670) are ignored. The rest differs
 * in spelling details: our text has the small signs U+06ED and U+06E2 that theirs lacks, U+06DF where theirs has the sukun U+0652, a space
 * before a pause sign where theirs has U+200C, an extra `ا` here and there, `ء` where theirs has `أ`, `ى` where theirs has `ٮ`. The colours
 * must land on OUR letters (the text of the Quran is never replaced), so the two texts are aligned letter by letter:
 *
 * 1. **Letters.** A text is cut into letter units ([LetterUnits]): a letter (the dagger alif and the small waw/yeh count as letters) with the marks
 *    that follow it (harakat, Quranic signs, maddah). Spaces, U+200C and tatweel are not letters; a tatweel stays with the letter it
 *    stretches. A rule that colours a mark and a tatweel only (`ـٰ`, the madd of `رَحْمَٰن`) colours the dagger alif, which is a unit.
 * 2. **Alignment.** The letters of the two texts are matched in order, the way a diff matches lines: the pairing that matches the most
 *    (and the most exact) letters wins. Letters that are the same letter in another spelling match each other ([score]); a letter that has
 *    no partner (our extra `ا`) is skipped.
 * 3. **Transfer.** A rule of theirs covers some of their letters; its range in our text starts where the first of their matched partners
 *    starts and ends where the last one ends, marks and all. A rule none of whose letters has a partner (and a "rule" that holds no letter at
 *    all, such as the stop mark that is left of a rule cut in two by a rule inside it) is dropped.
 *
 * The result never overlaps and is in reading order. On all 6236 ayahs of the bundled text 59 876 of the 59 877 rules of the markup reach our
 * text (99.998 %); the one that does not is the madd of 47:31, which holds a lone fatha. 27 pieces of rules that another rule cut in two hold
 * no letter (a stop mark, the fatha) and are dropped; 456 letters of our text (mostly the extra alefs) and 2 of theirs have no partner; and the
 * letters under every moved range equal the letters of the rule in their text, by skeleton, with no exception
 * (`scripts/check_tajweed_alignment.py`, 250 ms for the whole Quran).
 */
object TajweedAlignment {
    /**
     * The rules of [theirs] on the text [ours] (what the screen draws: after `splitBismillah` and `hideRubSign`). The ranges are indices
     * into [ours]; [TajweedSpan.source] is kept.
     */
    fun align(theirs: TajweedAyah, ours: String): List<TajweedSpan> = alignDetailed(theirs, ours).map { it.to }

    /** A rule piece of the markup ([from], a range of their text) and where it landed in our text ([to]). */
    internal class Moved(val from: TajweedSpan, val to: TajweedSpan)

    /** [align] with the piece of their text that every moved range comes from (what the check of the whole Quran compares). */
    internal fun alignDetailed(theirs: TajweedAyah, ours: String): List<Moved> {
        if (theirs.spans.isEmpty() || ours.isEmpty()) return emptyList()
        val from = LetterUnits.of(theirs.plain)
        val to = LetterUnits.of(ours)
        if (from.size == 0 || to.size == 0) return emptyList()
        val partner = match(from.letters, to.letters) ?: return emptyList()

        val result = ArrayList<Moved>(theirs.spans.size)
        var unit = 0
        for (span in theirs.spans) {
            while (unit < from.size && from.cores[unit] < span.range.first) unit++
            var first = -1
            var last = -1
            var next = unit
            while (next < from.size && from.cores[next] <= span.range.last) {
                val mate = partner[next]
                if (mate >= 0) {
                    if (first < 0) first = mate
                    last = mate
                }
                next++
            }
            if (first < 0) continue
            result += Moved(span, TajweedSpan(span.rule, to.starts[first] until to.ends[last], span.source))
        }
        return result
    }

    /** For every letter of [a] the index of its partner in [b], or -1. Null when the texts are too long to align (never for an ayah). */
    internal fun match(a: CharArray, b: CharArray): IntArray? {
        val partner = IntArray(a.size) { -1 }
        // Most ayahs have the same letters in both texts, spelled the same or in an equivalent way: no search needed.
        if (a.size == b.size && a.indices.all { sameLetter(a[it], b[it]) }) {
            for (index in a.indices) partner[index] = index
            return partner
        }
        val rows = a.size + 1
        val columns = b.size + 1
        if (rows.toLong() * columns > MAX_CELLS) return null

        // best[i * columns + j]: the best total score of aligning a[i..] with b[j..].
        val best = IntArray(rows * columns)
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                var value = maxOf(best[(i + 1) * columns + j], best[i * columns + j + 1])
                val gain = score(a[i], b[j])
                if (gain > 0) value = maxOf(value, best[(i + 1) * columns + j + 1] + gain)
                best[i * columns + j] = value
            }
        }
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            val gain = score(a[i], b[j])
            when {
                gain > 0 && best[i * columns + j] == best[(i + 1) * columns + j + 1] + gain -> {
                    partner[i] = j
                    i++
                    j++
                }
                best[(i + 1) * columns + j] >= best[i * columns + j + 1] -> i++
                else -> j++
            }
        }
        return partner
    }

    private fun sameLetter(x: Char, y: Char): Boolean = x == y || (family(x) != 0 && family(x) == family(y))

    /** How well [x] of one spelling matches [y] of the other: 4 the same letter, 3 a known spelling difference, 2 the same family of letters, 0 not at all. */
    internal fun score(x: Char, y: Char): Int {
        if (x == y) return 4
        val family = family(x)
        if (family == 0 || family != family(y)) return 0
        return if (isKnownVariant(x, y)) 3 else 2
    }

    /**
     * Letters that stand for one another in the two texts. The alef family holds the alef, alef wasla, the alefs with a hamza or a maddah,
     * the bare hamza and both dagger alifs; the yeh family the yeh, alef maksura and the dotless beh that the markup uses for it, the yeh
     * with a hamza; the waw family waw and waw with a hamza; the small yeh family the two small yehs.
     */
    private fun family(c: Char): Int = when (c) {
        '\u0627', '\u0671', '\u0623', '\u0625', '\u0622', '\u0621', '\u0670', '\u0672', '\u0673' -> 1
        '\u0649', '\u064A', '\u066E', '\u0626', '\u06CC' -> 2
        '\u0648', '\u0624' -> 3
        '\u06E6', '\u06E7' -> 4
        else -> 0
    }

    /** The differences the two texts really have (counted on the whole Quran), so they win over a looser pairing of the same family. */
    private fun isKnownVariant(x: Char, y: Char): Boolean =
        KNOWN_VARIANTS.any { (x == it.first && y == it.second) || (x == it.second && y == it.first) }

    private val KNOWN_VARIANTS = listOf(
        '\u0621' to '\u0623', '\u0621' to '\u0625', '\u0670' to '\u0672', '\u0649' to '\u066E', '\u06E6' to '\u06E7', '\u0649' to '\u0626', '\u0648' to '\u0624',
    )

    /** The alignment table of a longest ayah (about 600 letters each side) is 360 000 cells; anything near the limit is not an ayah. */
    private const val MAX_CELLS = 4_000_000L
}

/**
 * The letter units of a text: [letters] the letter of each unit (what is matched), [cores] its index in the text, [starts] and [ends] the
 * stretch of the text the unit owns (a tatweel before the letter and the marks after it belong to it; [ends] is exclusive).
 */
internal class LetterUnits(
    val letters: CharArray,
    val cores: IntArray,
    val starts: IntArray,
    val ends: IntArray,
) {
    val size: Int get() = letters.size

    companion object {
        private const val TATWEEL = '\u0640'

        fun of(text: String): LetterUnits {
            val letters = CharArray(text.length)
            val cores = IntArray(text.length)
            val starts = IntArray(text.length)
            val ends = IntArray(text.length)
            var count = 0
            // Where a run of tatweel that has not met its letter yet starts, or -1.
            var pending = -1
            for (index in text.indices) {
                val char = text[index]
                when {
                    isSeparator(char) -> {
                        // A tatweel at the end of a word stretches the letter before it.
                        if (pending >= 0 && count > 0 && ends[count - 1] == pending) ends[count - 1] = index
                        pending = -1
                    }
                    char == TATWEEL -> if (pending < 0) pending = index
                    isMark(char) -> if (pending < 0 && count > 0 && ends[count - 1] == index) ends[count - 1] = index + 1
                    else -> {
                        letters[count] = char
                        cores[count] = index
                        starts[count] = if (pending >= 0) pending else index
                        ends[count] = index + 1
                        count++
                        pending = -1
                    }
                }
            }
            if (pending >= 0 && count > 0 && ends[count - 1] == pending) ends[count - 1] = text.length
            return LetterUnits(letters.copyOf(count), cores.copyOf(count), starts.copyOf(count), ends.copyOf(count))
        }

        private fun isSeparator(c: Char): Boolean = c.isWhitespace() || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF'

        /**
         * Marks go with the letter before them: harakat and maddah (064B-065F), the small high signs (0610-061A), the Quranic annotation signs
         * (06D6-06E4, 06E8-06ED, among them the pause signs, the rub el hizb 06DE and the sajda sign 06E9). The dagger alif (0670) and the small
         * waw and yeh (06E5-06E7) are letters of their own: a rule can sit on them alone (`ـٰ`).
         */
        private fun isMark(c: Char): Boolean {
            val code = c.code
            return code in 0x064B..0x065F ||
                code in 0x0610..0x061A ||
                code in 0x06D6..0x06E4 ||
                code in 0x06E8..0x06ED
        }
    }
}
