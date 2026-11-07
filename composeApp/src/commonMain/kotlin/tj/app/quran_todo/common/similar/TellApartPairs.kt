package tj.app.quran_todo.common.similar

import tj.app.quran_todo.common.mushaf.AyahKey
import kotlin.random.Random

/**
 * Two similar places of the question "Tell apart" (task F6): [first] and [second] are both memorized (or being learned), [shownWords] is how
 * many words they begin with in common (at least one, and always fewer than the words of the shorter ayah): that much is shown, the rest is
 * hidden until the person has recalled both.
 */
data class TellApartPair(
    val first: AyahKey,
    val second: AyahKey,
    val shownWords: Int,
    val score: Int,
) {
    /** Both places, first one first. */
    val places: List<AyahKey> get() = listOf(first, second)
}

/**
 * Picks the pairs for "Tell apart" (task F6) from the index of similar ayahs and what the person has memorized. Pure: the same arguments
 * and the same [Random] seed give the same pairs.
 *
 * A pair is fit when both ayahs are covered (`HifzRepository.coveredAyahs()`), the score is at least [MIN_SCORE] (a stronger likeness
 * confuses; a weaker one is a shared phrase), the ayahs have words that differ (otherwise there is nothing to tell apart), and neither is
 * only the beginning of the other (otherwise one of them has nothing to recall). Pairs with a difficult ayah come first.
 */
object TellApartPairs {
    /** A pair with a lower score is not offered by the quiz (the sheet of one ayah offers every pair of it). */
    const val MIN_SCORE = 40

    /** The pairs are drawn from this many of the best candidates, so that two quizzes in a row are not the same. */
    private const val POOL_FACTOR = 3

    /**
     * How many fit pairs there are, by the index alone (no texts, so it is cheap enough for a screen): both ayahs covered, the score at
     * least [MIN_SCORE]. The real quiz may drop a few more (no differing words); the number is for "N pairs".
     */
    fun count(index: SimilarIndex, covered: Map<Int, Set<Int>>): Int {
        var total = 0
        for (ref in index.ayahs) {
            if (!covered.has(ref)) continue
            for (other in index.similar(ref)) {
                if (other.score >= MIN_SCORE && isAfter(other.ref, ref) && covered.has(other.ref)) total++
            }
        }
        return total
    }

    /**
     * Up to [count] pairs, no ayah twice. [text] is the ayah as the screens draw it (null when it is not there). [hard]: surah -> the
     * ayahs marked difficult.
     */
    fun pick(
        index: SimilarIndex,
        covered: Map<Int, Set<Int>>,
        hard: Map<Int, Set<Int>>,
        text: (AyahKey) -> String?,
        count: Int,
        random: Random,
    ): List<TellApartPair> {
        if (count <= 0) return emptyList()
        val candidates = ArrayList<Candidate>()
        for (ref in index.ayahs) {
            if (!covered.has(ref)) continue
            for (other in index.similar(ref)) {
                if (other.score < MIN_SCORE || !isAfter(other.ref, ref) || !covered.has(other.ref)) continue
                candidates += Candidate(ref, other.ref, other.score, hard.has(ref) || hard.has(other.ref))
            }
        }
        return choose(candidates, text = text, count = count, random = random, wanted = null)
    }

    /**
     * The pairs of one group (the sheet of [ref]): [ref] with each of its similar ayahs that is covered, whatever the score. Empty when
     * [ref] is not covered or none of its similar ayahs is. Up to [count] pairs; each of them has [ref] as its first place.
     */
    fun forAyah(
        index: SimilarIndex,
        ref: AyahKey,
        covered: Map<Int, Set<Int>>,
        hard: Map<Int, Set<Int>>,
        text: (AyahKey) -> String?,
        count: Int,
        random: Random,
    ): List<TellApartPair> {
        if (!covered.has(ref)) return emptyList()
        val candidates = index.similar(ref)
            .filter { covered.has(it.ref) }
            .map { Candidate(ref, it.ref, it.score, hard.has(ref) || hard.has(it.ref)) }
        // Every question of a group is about the same ayah, so it may repeat: the others differ.
        return choose(candidates, text = text, count = count, random = random, wanted = ref)
    }

    private fun choose(
        candidates: List<Candidate>,
        text: (AyahKey) -> String?,
        count: Int,
        random: Random,
        wanted: AyahKey?,
    ): List<TellApartPair> {
        // Difficult pairs first, then the likest; the top of each tier is shuffled so the quiz changes.
        val tiers = candidates.groupBy { it.hasHard }
        val ordered = listOf(true, false).flatMap { tier ->
            val sorted = tiers[tier].orEmpty().sortedByDescending { it.score }
            val head = sorted.take(count * POOL_FACTOR).shuffled(random)
            head + sorted.drop(head.size)
        }
        val used = HashSet<AyahKey>()
        val result = ArrayList<TellApartPair>()
        for (candidate in ordered) {
            if (result.size >= count) break
            // The ayah of a group question is the same one every time; any other ayah is in at most one question.
            if (candidate.first != wanted && candidate.first in used) continue
            if (candidate.second in used) continue
            val a = text(candidate.first) ?: continue
            val b = text(candidate.second) ?: continue
            val pair = pairOf(candidate, a, b) ?: continue
            if (wanted == null) used += candidate.first
            used += candidate.second
            result += pair
        }
        return result
    }

    /** The pair if there is something to tell apart: words that differ, and a beginning shorter than either ayah. */
    private fun pairOf(candidate: Candidate, textA: String, textB: String): TellApartPair? {
        val diff = wordDiff(textA, textB)
        if (diff.identical) return null
        val wordsA = diff.a.ranges.size
        val wordsB = diff.b.ranges.size
        val shortest = minOf(wordsA, wordsB)
        val prefix = commonPrefixWords(textA, textB)
        // The whole of one ayah is the beginning of the other: nothing of it is left to recall.
        if (shortest < 2 || prefix >= shortest) return null
        return TellApartPair(candidate.first, candidate.second, shownWords = maxOf(1, prefix), score = candidate.score)
    }

    private class Candidate(val first: AyahKey, val second: AyahKey, val score: Int, val hasHard: Boolean)

    /** The order of the Quran, so that each pair is looked at once. */
    private fun isAfter(a: AyahKey, b: AyahKey): Boolean = a.surah > b.surah || (a.surah == b.surah && a.ayah > b.ayah)

    private fun Map<Int, Set<Int>>.has(ref: AyahKey): Boolean = this[ref.surah]?.contains(ref.ayah) == true
}
