package tj.app.quran_todo.common.voice

/**
 * The phonetic recitation check (task F7, prototype): the same rules as `scripts/voice_phonetic_ref.py`; keep the two in step (the
 * tests read cases made by the script).
 *
 * The model (Hisnlabs/tajweed-v2) writes the recitation in the Quran phonetic script, one sign per sound, a long sound as a repeated
 * sign. Both the expected script and the heard one are "collapsed" (a run of one sign becomes one sign): the length of a madd or a
 * ghunnah is not judged here. The words are given as their collapsed scripts; "-" is a word without a sound of its own.
 */
object PhoneticCheck {
    /** A word is wrong when this share of its sounds is wrong. */
    const val Threshold = 0.34

    /** So many foreign sounds between two words are an extra word. */
    const val ExtraMin = 2

    /** A wrong word is "missed" when this share of its sounds is simply absent ... */
    const val MissedShare = 0.6

    /** ... and fewer than this many foreign sounds stand in its place. */
    const val ReplacedMin = 2

    /** Live: the last sounds of the heard script may still change, a word ending this close to them is not shown yet. */
    const val Guard = 2

    /** Foreign signs heard before the word number [gap] (0 = before the first word; the word count = after the last one). */
    class Extra(val gap: Int, val signs: Int)

    /** The final verdict on every word of the passage and the extra sounds between them. */
    class Judgement(val verdicts: List<WordVerdict>, val extras: List<Extra>) {
        val missed: Int get() = verdicts.count { it == WordVerdict.Missed }
        val replaced: Int get() = verdicts.count { it == WordVerdict.Replaced }
    }

    /** Live: how many words the reciter has reached and the verdicts on them. */
    class Progress(val shown: Int, val verdicts: List<WordVerdict>)

    /** The expected script and the span [start, stop) of every word in it. */
    private class Joined(val text: String, val starts: IntArray, val stops: IntArray)

    private class Trace(val bad: IntArray, val ins: IntArray)

    /** A run of one sign becomes one sign. */
    fun collapse(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) if (out.isEmpty() || out[out.length - 1] != c) out.append(c)
        return out.toString()
    }

    /**
     * Collapsed words -> the expected script. A word that starts with the sign the script ends with shares that sign (the script stays
     * collapsed).
     */
    private fun join(words: List<String>): Joined {
        val text = StringBuilder()
        val starts = IntArray(words.size)
        val stops = IntArray(words.size)
        words.forEachIndexed { index, word ->
            if (word == "-" || word.isEmpty()) {
                starts[index] = text.length
                stops[index] = text.length
            } else if (text.isNotEmpty() && text[text.length - 1] == word[0]) {
                starts[index] = text.length - 1
                stops[index] = text.length + word.length - 1
                text.append(word, 1, word.length)
            } else {
                starts[index] = text.length
                stops[index] = text.length + word.length
                text.append(word)
            }
        }
        return Joined(text.toString(), starts, stops)
    }

    /** The edit-distance table, row-major: `(expected.length + 1)` rows of `(heard.length + 1)` columns. */
    private fun costTable(expected: String, heard: String): IntArray {
        val n = expected.length
        val m = heard.length
        val w = m + 1
        val cost = IntArray((n + 1) * w)
        for (j in 1..m) cost[j] = j
        for (i in 1..n) {
            val row = i * w
            val above = row - w
            val e = expected[i - 1]
            cost[row] = i
            for (j in 1..m) {
                var best = cost[above + j - 1] + (if (e != heard[j - 1]) 1 else 0)
                val up = cost[above + j] + 1
                if (up < best) best = up
                val left = cost[row + j - 1] + 1
                if (left < best) best = left
                cost[row + j] = best
            }
        }
        return cost
    }

    /**
     * Walks back from (end, heard.length): the diagonal step first, then a missing expected sign, then a foreign heard one.
     * bad[i] (0 right, 1 another sign, 2 absent) for i < end, ins[k] = foreign signs before expected position k (k <= end).
     */
    private fun trace(cost: IntArray, expected: String, heard: String, end: Int): Trace {
        val w = heard.length + 1
        val bad = IntArray(end)
        val ins = IntArray(end + 1)
        var i = end
        var j = heard.length
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 &&
                cost[i * w + j] == cost[(i - 1) * w + j - 1] + (if (expected[i - 1] != heard[j - 1]) 1 else 0)
            ) {
                bad[i - 1] = if (expected[i - 1] != heard[j - 1]) 1 else 0
                i--
                j--
            } else if (i > 0 && cost[i * w + j] == cost[(i - 1) * w + j] + 1) {
                bad[i - 1] = 2
                i--
            } else {
                ins[i]++
                j--
            }
        }
        return Trace(bad, ins)
    }

    /** The words whose span ends at or before [end] -> their verdicts and the extras. */
    private fun verdictsOf(
        joined: Joined,
        trace: Trace,
        end: Int,
        threshold: Double,
        extraMin: Int,
        withExtras: Boolean,
    ): Judgement {
        val bad = trace.bad
        val ins = trace.ins
        val verdicts = ArrayList<WordVerdict>()
        val used = HashSet<Int>()
        for (index in joined.starts.indices) {
            val start = joined.starts[index]
            val stop = joined.stops[index]
            if (stop > end) break
            val length = maxOf(1, stop - start)
            var subs = 0
            var dels = 0
            for (k in start until stop) {
                if (bad[k] == 1) subs++ else if (bad[k] == 2) dels++
            }
            var inside = 0
            for (k in start + 1 until stop) inside += ins[k]
            if ((subs + dels + inside).toDouble() / length < threshold) {
                verdicts += WordVerdict.Correct
                continue
            }
            val around = ins[start] + inside + ins[stop]
            if (dels >= MissedShare * length && around < ReplacedMin) {
                verdicts += WordVerdict.Missed
            } else {
                verdicts += WordVerdict.Replaced
                used += start
                used += stop
            }
        }
        val extras = ArrayList<Extra>()
        if (withExtras) {
            val count = verdicts.size
            val first = HashMap<Int, Int>()
            for (index in count - 1 downTo 0) first[joined.starts[index]] = index
            for (k in 0..end) {
                if (ins[k] >= extraMin && k !in used && (k in first || k == end)) extras += Extra(first[k] ?: count, ins[k])
            }
        }
        return Judgement(verdicts, extras)
    }

    /** The whole passage [words] against the whole [heard] script (already collapsed). */
    fun judge(words: List<String>, heard: String, threshold: Double = Threshold, extraMin: Int = ExtraMin): Judgement {
        val joined = join(words)
        val end = joined.text.length
        val trace = trace(costTable(joined.text, heard), joined.text, heard, end)
        return verdictsOf(joined, trace, end, threshold, extraMin, withExtras = true)
    }

    /**
     * Live: how far the reciter has come. The [heard] script (collapsed) is matched with the beginning of the expected one: the
     * beginning that costs the least (the shortest one of equals).
     */
    fun follow(words: List<String>, heard: String, guard: Int = Guard): Progress {
        if (heard.isEmpty()) return Progress(0, emptyList())
        val joined = join(words)
        val cost = costTable(joined.text, heard)
        val w = heard.length + 1
        val m = heard.length
        var end = 0
        var least = cost[m]
        for (i in 1..joined.text.length) {
            val value = cost[i * w + m]
            if (value < least) {
                least = value
                end = i
            }
        }
        val trace = trace(cost, joined.text, heard, end)
        val limit = if (end == joined.text.length) end else end - guard
        val verdicts = verdictsOf(joined, trace, maxOf(0, limit), Threshold, ExtraMin, withExtras = false).verdicts
        return Progress(verdicts.size, verdicts)
    }
}
