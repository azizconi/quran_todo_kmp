package tj.app.quran_todo.common.similar

import tj.app.quran_todo.common.audio.timing.wordRanges

/**
 * The word as it is compared for "similar ayahs" (task F6): the SAME rules as `scripts/build_mutashabihat.py` (`normalize_word`), which
 * builds the index of similar ayahs from the bundled text, so a word that the script sees as equal is equal here.
 *
 * Every mark goes (harakat, the dagger alef, Quranic annotation signs, tatweel, joiners, the rub el hizb sign: whatever is not a letter of
 * the Arabic block), and the letters that differ only in spelling become one: alef with hamza or madda and alef wasla - alef; alef
 * maqsura and dotless beh - yeh; teh marbuta - heh; waw and yeh with hamza - hamza. What is left may be empty (a token that is only a sign).
 */
fun normalizeWord(word: String): String {
    val out = StringBuilder(word.length)
    for (original in word) {
        val c = when (original) {
            'آ', 'أ', 'إ', 'ٱ' -> 'ا'
            'ى', 'ٮ' -> 'ي'
            'ة' -> 'ه'
            'ؤ', 'ئ' -> 'ء'
            else -> original
        }
        // hamza..ghain and feh..yeh; harakat 064B-065F, tatweel 0640, the dagger alef 0670 and the signs 06D6-06ED are out.
        if (c.code in 0x0621..0x063A || c.code in 0x0641..0x064A) out.append(c)
    }
    return out.toString()
}

/**
 * The words of the text that is drawn (what `displayWords` of the reader gives: the Bismillah of the first ayah cut off, the rub el hizb
 * sign hidden): [ranges] are the character ranges in that text, [words] the normalized words, one to one. A token without a letter is no
 * word, as in `wordRanges`.
 */
class NormalizedWords(val ranges: List<IntRange>, val words: List<String>) {
    val size: Int get() = words.size
}

/** [NormalizedWords] of [text], the text as the screens draw it. */
fun normalizedWords(text: String): NormalizedWords {
    val ranges = ArrayList<IntRange>()
    val words = ArrayList<String>()
    for (range in wordRanges(text)) {
        val word = normalizeWord(text.substring(range.first, range.last + 1))
        if (word.isEmpty()) continue
        ranges += range
        words += word
    }
    return NormalizedWords(ranges, words)
}
