package tj.app.quran_todo.common.similar

/**
 * One side of a [WordDiff]: the words of one ayah as it is drawn, with [differs] telling for each word whether the other ayah has no
 * partner for it. [ranges] are character ranges in the text that was given (both ends inclusive), the same ranges as
 * `displayWords` of the reader gives, so they can be used on the text on the screen as they are.
 */
class AyahWordDiff(val ranges: List<IntRange>, val differs: List<Boolean>) {
    /** The ranges of the words that differ, in reading order. */
    val differingRanges: List<IntRange> get() = ranges.filterIndexed { index, _ -> differs[index] }

    /** How many words have a partner in the other ayah. */
    val matched: Int get() = differs.count { !it }

    /** The ayah and the other one have the same words. */
    val identical: Boolean get() = differs.none { it }
}

/** The words of two ayahs side by side: [a] and [b]. */
class WordDiff(val a: AyahWordDiff, val b: AyahWordDiff) {
    /** The two ayahs have the same words (only marks and spelling differ, or nothing). */
    val identical: Boolean get() = a.identical && b.identical
}

/**
 * Compares two ayahs by words (task F6): [textA] and [textB] are the texts as the screens draw them (after `splitBismillah`, so no
 * Bismillah and no rub el hizb sign), the words are normalized ([normalizeWord]) and matched by the longest common subsequence. A word of
 * an ayah that has a partner in the other is [AyahWordDiff.differs] = false; every other word differs.
 */
fun wordDiff(textA: String, textB: String): WordDiff {
    val a = normalizedWords(textA)
    val b = normalizedWords(textB)
    val n = a.size
    val m = b.size
    // lcs[i][j]: the length of the LCS of the words a[i..] and b[j..].
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a.words[i] == b.words[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val differsA = BooleanArray(n) { true }
    val differsB = BooleanArray(m) { true }
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a.words[i] == b.words[j] -> {
                differsA[i] = false
                differsB[j] = false
                i++
                j++
            }
            lcs[i + 1][j] >= lcs[i][j + 1] -> i++
            else -> j++
        }
    }
    return WordDiff(AyahWordDiff(a.ranges, differsA.toList()), AyahWordDiff(b.ranges, differsB.toList()))
}

/**
 * How many words the two ayahs begin with in common (normalized words), the start of the "Tell apart" question: that much is shown, the
 * rest is up to memory.
 */
fun commonPrefixWords(textA: String, textB: String): Int {
    val a = normalizedWords(textA).words
    val b = normalizedWords(textB).words
    var count = 0
    while (count < a.size && count < b.size && a[count] == b[count]) count++
    return count
}
