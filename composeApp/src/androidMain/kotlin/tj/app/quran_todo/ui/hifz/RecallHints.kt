package tj.app.quran_todo.ui.hifz

import tj.app.quran_todo.ui.state.HifzHintMode

/**
 * What a hidden ayah of the "Recall" step leaves on screen, as pure functions (task F2a). Words are the words of
 * `displayWords` (`wordRanges`): the index of a word is its position in reading order, and everything here counts in those indices.
 */
object RecallHints {
    /** "Hide more" goes 25 % -> 50 % -> 75 % -> 100 %: four steps, 0 is the whole ayah. */
    const val ProgressiveSteps = 4

    /** An ayah that was peeked at this many times (a word is one, holding the whole ayah is two) is offered "Hard". */
    const val PeeksSuggestHard = 2

    /**
     * How many words of an ayah of [wordCount] words are hidden at the progressive [step]: the next quarter of the words from the
     * end, rounded up; never all of them before the last step (a word stays to read) and all of them at the last one.
     */
    fun hiddenCount(wordCount: Int, step: Int): Int {
        if (wordCount <= 0) return 0
        val s = step.coerceIn(0, ProgressiveSteps)
        if (s >= ProgressiveSteps) return wordCount
        val quarters = (wordCount * s + ProgressiveSteps - 1) / ProgressiveSteps
        return quarters.coerceAtMost(wordCount - 1).coerceAtLeast(0)
    }

    /**
     * The step "Hide more" goes to from [step]: the next one that hides more words than this one (a short ayah has fewer
     * distinct steps: one word is whole, then hidden), at most [ProgressiveSteps].
     */
    fun nextStep(wordCount: Int, step: Int): Int {
        val now = hiddenCount(wordCount, step)
        for (next in step + 1 until ProgressiveSteps) if (hiddenCount(wordCount, next) > now) return next
        return ProgressiveSteps
    }

    /** True when "Hide more" has anything left to hide (the ayah is not hidden entirely yet). */
    fun canHideMore(wordCount: Int, step: Int): Boolean = hiddenCount(wordCount, step) < wordCount

    /** The share of the words hidden at [step] in percent, as the button says it (25, 50, 75, 100). */
    fun percentAt(step: Int): Int = step.coerceIn(0, ProgressiveSteps) * 100 / ProgressiveSteps

    /**
     * The words (indices) that stay sharp in a hidden ayah of [wordCount] words under [mode]. A single word is never left
     * showing (nothing would be hidden), so a one-word ayah is hidden whole under every level but the progressive start.
     */
    fun shownWords(mode: HifzHintMode, wordCount: Int, step: Int = 0): Set<Int> {
        if (wordCount <= 0) return emptySet()
        return when (mode) {
            HifzHintMode.NONE, HifzHintMode.FIRST_LETTERS -> emptySet()
            HifzHintMode.FIRST_WORD -> if (wordCount >= 2) setOf(0) else emptySet()
            HifzHintMode.EVERY_OTHER -> if (wordCount >= 2) (0 until wordCount step 2).toSet() else emptySet()
            HifzHintMode.PROGRESSIVE -> (0 until wordCount - hiddenCount(wordCount, step)).toSet()
        }
    }

    /** Words that [mode] puts a first letter in the place of (every hidden one) - only the "first letters" level does. */
    fun showsLetters(mode: HifzHintMode): Boolean = mode == HifzHintMode.FIRST_LETTERS

    /**
     * The first letter of a [word] as it is shown alone: the first letter of the word without harakat, reading marks and tatweel
     * (alif wasla becomes a plain alif). Null for a token that has no letter. The letter is drawn on its own, so it takes its
     * isolated form.
     */
    fun firstLetter(word: String): String? {
        for (char in word) {
            if (!char.isBaseLetter()) continue
            return if (char == AlifWasla) AlifPlain.toString() else char.toString()
        }
        return null
    }

    /**
     * One line that shows what [mode] looks like on an ayah ([words] in reading order), for the sheet of the levels: the words that
     * stay readable, three dots where a word is hidden, and the first letters at the "first letters" level. Long ayahs are cut
     * after [limit] words. The progressive level is shown at half of the words that are drawn.
     */
    fun preview(mode: HifzHintMode, words: List<String>, limit: Int = 5): String {
        val cut = words.take(limit)
        if (cut.isEmpty()) return ""
        val shown = shownWords(mode, cut.size, step = 2)
        return cut.indices.joinToString(" ") { index ->
            when {
                index in shown -> cut[index]
                mode == HifzHintMode.FIRST_LETTERS -> firstLetter(cut[index]) ?: Dots
                else -> Dots
            }
        } + if (words.size > cut.size) " $Dots" else ""
    }

    private const val Dots = "···"
    private const val AlifWasla = 'ٱ'
    private const val AlifPlain = 'ا'
    private const val Tatweel = 'ـ'

    /** A letter of the word itself: not a mark, not the stretching tatweel, not a small high letter of the reading marks. */
    private fun Char.isBaseLetter(): Boolean =
        isLetter() && this != Tatweel && this !in 'ۖ'..'ۭ' && this != 'ٰ'
}
