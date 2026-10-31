package tj.app.quran_todo.common.similar

import tj.app.quran_todo.common.mushaf.AyahKey

/** A similar ayah: where it is and how alike it is, [score] 0-100 (the share of the words of the shorter ayah that match). */
data class SimilarAyah(val ref: AyahKey, val score: Int)

/**
 * The index of similar ayahs (mutashabihat, task F6): which ayahs of the Quran look like which. It is made by
 * `scripts/build_mutashabihat.py` from the bundled text and ships as `files/quran/similar.tsv` (format: see the script). Pure data, no
 * text: the words to compare come from the text that is drawn ([wordDiff]).
 *
 * - [similar]: up to 10 similar ayahs of an ayah, best first. A pair is symmetric: if B is in the list of A, A is in the list of B.
 * - [repeats]: the ayahs that have exactly the same words as this one, when there are three or more of them (a refrain: "Which of the
 *   favours of your Lord will you deny?" 31 times in Ar-Rahman); they are not listed in [similar] of each other.
 */
class SimilarIndex private constructor(
    private val similarOf: Map<AyahKey, List<SimilarAyah>>,
    private val groupOf: Map<AyahKey, List<AyahKey>>,
) {
    /** The similar ayahs of [ref], best first; empty when there are none. */
    fun similar(ref: AyahKey): List<SimilarAyah> = similarOf[ref].orEmpty()

    /** Every place of the refrain [ref] belongs to, [ref] itself among them, in the order of the Quran; null when it is no refrain. */
    fun repeats(ref: AyahKey): List<AyahKey>? = groupOf[ref]

    /** How many other places look like [ref]: the similar ayahs and the other places of its refrain. */
    fun count(ref: AyahKey): Int = similar(ref).size + (repeats(ref)?.let { it.size - 1 } ?: 0)

    /** [ref] has anything to show in the sheet of similar ayahs. */
    fun has(ref: AyahKey): Boolean = similarOf.containsKey(ref) || groupOf.containsKey(ref)

    /** Every ayah that has similar ayahs (not the ones that only repeat), in the order of the Quran. */
    val ayahs: List<AyahKey> get() = similarOf.keys.sortedWith(compareBy({ it.surah }, { it.ayah }))

    /** How many pairs the index lists (each pair once). */
    val pairCount: Int get() = similarOf.values.sumOf { it.size } / 2

    companion object {
        val Empty: SimilarIndex = SimilarIndex(emptyMap(), emptyMap())

        /**
         * Reads the file. Lines that start with '#' and empty lines are skipped, and so is a line or an entry that is not understood: a
         * damaged line costs its own ayah, never the whole index.
         *
         *     R <count> <ayah> <ayah> ...      "surah:ayah" each, a refrain
         *     P <ayah> <ayah>=<score> ...      the similar ayahs of one ayah
         */
        fun parse(text: String): SimilarIndex {
            val similar = HashMap<AyahKey, List<SimilarAyah>>()
            val groups = HashMap<AyahKey, List<AyahKey>>()
            for (line in text.lineSequence()) {
                if (line.isEmpty() || line.startsWith('#')) continue
                val fields = line.split('\t')
                when (fields[0]) {
                    "R" -> {
                        val members = fields.drop(2).mapNotNull { AyahKey.parse(it) }
                        if (members.size >= 2) members.forEach { groups[it] = members }
                    }
                    "P" -> {
                        val ref = AyahKey.parse(fields.getOrNull(1)) ?: continue
                        val entries = fields.drop(2).mapNotNull { entry ->
                            val at = entry.indexOf('=')
                            if (at < 0) return@mapNotNull null
                            val other = AyahKey.parse(entry.substring(0, at)) ?: return@mapNotNull null
                            val score = entry.substring(at + 1).toIntOrNull()?.coerceIn(0, 100) ?: return@mapNotNull null
                            SimilarAyah(other, score)
                        }
                        if (entries.isNotEmpty()) similar[ref] = entries.sortedByDescending { it.score }
                    }
                }
            }
            return SimilarIndex(similar, groups)
        }
    }
}
