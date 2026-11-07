package tj.app.quran_todo.common.voice

import tj.app.quran_todo.common.similar.normalizedWords

/**
 * Word by word comparison of what a person said with the text they had to say (task F7, prototype). The same rules as
 * `scripts/voice_eval.py`, which measures them on recordings of reciters: keep the two in step.
 *
 * Only WORDS are checked. The marks (harakat, tajweed) are not: the model that writes down the speech gives marks by its own taste, so
 * both texts are normalized first (`normalizeWord` of F6) and then compared by a skeleton, see [sameWord].
 */
enum class WordVerdict { Correct, Missed, Replaced }

/** How strictly a heard word has to look like the word of the text. */
enum class WordMatching {
    /** The same skeleton (alef, hamza and doubled letters do not count). */
    Strict,

    /** The same skeleton, or a skeleton of 4+ letters that is one letter away (a letter the model hears wrong is not the reciter's error). */
    Fuzzy,
}

/** The verdict on one word of the text; [heard] is what was heard instead (for [WordVerdict.Replaced]). */
class WordCheck(val verdict: WordVerdict, val heard: String? = null)

/** A heard word that has no place in the text; [gap] is the number of words of the text before it (0 = before the first). */
class ExtraWord(val gap: Int, val word: String)

/** The result of [alignRecitation]: [words] one to one with the words of the text, [extras] in the order they were said. */
class RecitationCheck(val words: List<WordCheck>, val extras: List<ExtraWord>) {
    val missed: Int get() = words.count { it.verdict == WordVerdict.Missed }
    val replaced: Int get() = words.count { it.verdict == WordVerdict.Replaced }
    val correct: Int get() = words.count { it.verdict == WordVerdict.Correct }

    /** Everything that is not right: missed, replaced and extra words. */
    val errors: Int get() = missed + replaced + extras.size
}

/** The normalized words of the recognized [text] (what the model wrote, with or without marks). */
fun heardWords(text: String): List<String> = normalizedWords(text).words

/**
 * What is left of a normalized word when the spellings that the model chooses freely go: alef and hamza (written in many ways) and a
 * doubled letter (the shadda is gone from the normalized text, and the model may or may not write the letter twice).
 */
fun skeletonOf(word: String): String {
    val out = StringBuilder(word.length)
    for (c in word) {
        if (c == 'ا' || c == 'ء') continue
        if (out.isNotEmpty() && out[out.length - 1] == c) continue
        out.append(c)
    }
    return out.toString()
}

private const val FuzzyMinLength = 4

/** Are [reference] (a word of the text) and [heard] the same word for the check. Both are normalized words. */
fun sameWord(reference: String, heard: String, matching: WordMatching = WordMatching.Fuzzy): Boolean {
    if (reference == heard) return true
    val a = skeletonOf(reference)
    val b = skeletonOf(heard)
    if (a == b) return true
    return matching == WordMatching.Fuzzy && a.length >= FuzzyMinLength && b.length >= FuzzyMinLength && editDistanceAtMostOne(a, b)
}

private fun editDistanceAtMostOne(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            i++
            j++
            continue
        }
        if (++edits > 1) return false
        when {
            a.length == b.length -> { i++; j++ }
            a.length > b.length -> i++
            else -> j++
        }
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}

/**
 * Aligns the [heard] words with the [reference] words by edit distance (a word of the text that has a partner costs nothing; a word
 * that is missing, a word that is said instead of another, a word said too much: 1 each). Both lists are normalized words.
 *
 * Two more steps cost nothing, because the model often leaves out the space (and the speech of one ayah runs into the next): two words
 * of the text for one heard word, and one word of the text for two heard words. They need the same skeleton, with no letter of
 * tolerance: with it a short word would be swallowed by its neighbour.
 *
 * Between two alignments of the same cost the one that pairs words ("replaced") is preferred to one that drops one and adds one.
 */
fun alignRecitation(reference: List<String>, heard: List<String>, matching: WordMatching = WordMatching.Fuzzy): RecitationCheck {
    val n = reference.size
    val m = heard.size
    val inf = Int.MAX_VALUE / 2
    val cost = Array(n + 1) { IntArray(m + 1) { inf } }
    cost[0][0] = 0
    for (i in 0..n) {
        for (j in 0..m) {
            if (i == 0 && j == 0) continue
            var best = inf
            if (i > 0 && j > 0) best = cost[i - 1][j - 1] + if (sameWord(reference[i - 1], heard[j - 1], matching)) 0 else 1
            if (i > 1 && j > 0 && sameWord(reference[i - 2] + reference[i - 1], heard[j - 1], WordMatching.Strict)) best = minOf(best, cost[i - 2][j - 1])
            if (i > 0 && j > 1 && sameWord(reference[i - 1], heard[j - 2] + heard[j - 1], WordMatching.Strict)) best = minOf(best, cost[i - 1][j - 2])
            if (i > 0) best = minOf(best, cost[i - 1][j] + 1)
            if (j > 0) best = minOf(best, cost[i][j - 1] + 1)
            cost[i][j] = best
        }
    }
    val verdicts = Array(n) { WordCheck(WordVerdict.Missed) }
    val extras = ArrayList<ExtraWord>()
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0) {
            val step = if (sameWord(reference[i - 1], heard[j - 1], matching)) 0 else 1
            if (cost[i][j] == cost[i - 1][j - 1] + step) {
                verdicts[i - 1] = if (step == 0) WordCheck(WordVerdict.Correct) else WordCheck(WordVerdict.Replaced, heard[j - 1])
                i--
                j--
                continue
            }
        }
        if (i > 1 && j > 0 && sameWord(reference[i - 2] + reference[i - 1], heard[j - 1], WordMatching.Strict) && cost[i][j] == cost[i - 2][j - 1]) {
            verdicts[i - 1] = WordCheck(WordVerdict.Correct)
            verdicts[i - 2] = WordCheck(WordVerdict.Correct)
            i -= 2
            j--
            continue
        }
        if (i > 0 && j > 1 && sameWord(reference[i - 1], heard[j - 2] + heard[j - 1], WordMatching.Strict) && cost[i][j] == cost[i - 1][j - 2]) {
            verdicts[i - 1] = WordCheck(WordVerdict.Correct)
            i--
            j -= 2
            continue
        }
        if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
            verdicts[i - 1] = WordCheck(WordVerdict.Missed)
            i--
            continue
        }
        extras += ExtraWord(i, heard[j - 1])
        j--
    }
    extras.reverse()
    return RecitationCheck(verdicts.toList(), extras)
}
