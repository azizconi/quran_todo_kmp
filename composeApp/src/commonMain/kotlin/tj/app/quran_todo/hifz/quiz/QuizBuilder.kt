package tj.app.quran_todo.hifz.quiz

import tj.app.quran_todo.data.bundled.QuranFacts
import kotlin.random.Random

/**
 * Builds the questions of the quiz "Check" (task F2b) from what is covered by passages: a pure function of its arguments, the same
 * `random` seed gives the same quiz.
 *
 * - The pool is the covered ayahs only (`HifzRepository.coveredAyahs()`); the quiz is offered from [MIN_POOL] of them.
 * - Five kinds ([QuizKind]), each with its own condition of fitness; a kind that no ayah of the pool fits for is never chosen.
 * - In a quiz of five there are at least three different kinds if the pool allows; different surahs where possible; one ayah is in
 *   at most one question (what a question shows or gives away counts: [QuizQuestion.ayahsUsed]). A pool that is too small for five
 *   questions gives fewer.
 * - The words of an ayah are counted by the caller (`words`: the display words of the reader, the Bismillah cut off), so the builder
 *   does not know the text.
 */
object QuizBuilder {
    /** The quiz is offered from this many covered ayahs. */
    const val MIN_POOL = 5

    /** The default length of a quiz. */
    const val DEFAULT_COUNT = 5

    /** An ayah needs at least this many words to be continued ([QuizKind.CONTINUE]). */
    const val MIN_WORDS_TO_CONTINUE = 4

    /** How many times a quiz is drawn; the best draw (most questions, then most kinds, then most surahs) is kept. */
    private const val ATTEMPTS = 24

    /** The most ayahs in a question of [QuizKind.ORDER] and the fewest. */
    private const val ORDER_MAX = 4
    private const val ORDER_MIN = 3

    /** How many windows of a run of covered ayahs are tried for [QuizKind.ORDER] before the run is given up. */
    private const val WINDOW_TRIES = 12

    /** How many other surahs a question of [QuizKind.WHERE] offers next to the right one. */
    private const val OTHER_OPTIONS = 3

    /** The covered ayahs are enough for the quiz. */
    fun isAvailable(covered: Map<Int, Set<Int>>): Boolean = covered.values.sumOf { it.size } >= MIN_POOL

    /** How many words of an ayah of [totalWords] words are shown in [QuizKind.CONTINUE]: a third, rounded up. */
    fun shownWords(totalWords: Int): Int = (totalWords + 2) / 3

    /**
     * [covered]: surah -> its covered ayahs (numbers in the surah). [words]: how many words the ayah has on the screen. [count]: how
     * many questions. Empty when the pool is empty or [count] is not positive.
     */
    fun build(
        covered: Map<Int, Set<Int>>,
        words: (surah: Int, ayah: Int) -> Int,
        count: Int = DEFAULT_COUNT,
        random: Random,
    ): List<QuizQuestion> {
        val pool = Pool(covered)
        if (pool.ayahs.isEmpty() || count <= 0) return emptyList()

        // The kinds that fit something in the pool. A throwaway random keeps the caller's sequence for the real draws.
        val probe = Random(0)
        val available = QuizKind.entries.filter { kind -> sample(kind, pool, words, Used(), probe, fresh = false) != null }
        if (available.isEmpty()) return emptyList()
        val wantedKinds = minOf(3, count, available.size)

        var best: List<QuizQuestion> = emptyList()
        repeat(ATTEMPTS) {
            val attempt = draw(pool, words, count, available, random)
            if (isBetter(attempt, best)) best = attempt
            if (best.size >= count && best.map { it.kind }.distinct().size >= wantedKinds) return best
        }
        return best
    }

    private fun draw(pool: Pool, words: (Int, Int) -> Int, count: Int, available: List<QuizKind>, random: Random): List<QuizQuestion> {
        // Different kinds first, so that a short quiz is as varied as the pool lets it be; what is left over repeats a kind.
        val firsts = available.shuffled(random)
        val used = Used()
        val drawn = LinkedHashSet<QuizKind>()
        val result = ArrayList<QuizQuestion>(count)
        for (slot in 0 until count) {
            // A small pool must not be eaten up by the first questions: every question still to come keeps at least one ayah.
            val room = pool.ayahs.size - used.count
            val slotsAfter = count - slot - 1
            val affordable = available.filter { minAyahs(it) + slotsAfter <= room }
                .ifEmpty { available.filter { minAyahs(it) <= room } }
            if (affordable.isEmpty()) break
            val wanted = if (slot < firsts.size) firsts[slot] else available[random.nextInt(available.size)]
            val rest = affordable.filter { it != wanted }
            val order = listOfNotNull(wanted.takeIf { it in affordable }) +
                rest.filter { it !in drawn }.shuffled(random) +
                rest.filter { it in drawn }.shuffled(random)
            val question = order.firstNotNullOfOrNull { kind -> sample(kind, pool, words, used, random, fresh = true) } ?: break
            used.add(question)
            drawn += question.kind
            result += question
        }
        return result
    }

    /** The fewest ayahs a question of [kind] takes. */
    private fun minAyahs(kind: QuizKind): Int = when (kind) {
        QuizKind.CONTINUE, QuizKind.START, QuizKind.WHERE -> 1
        QuizKind.NEXT -> 2
        QuizKind.ORDER -> ORDER_MIN
    }

    private fun isBetter(candidate: List<QuizQuestion>, best: List<QuizQuestion>): Boolean {
        if (candidate.size != best.size) return candidate.size > best.size
        val kinds = candidate.map { it.kind }.distinct().size
        val bestKinds = best.map { it.kind }.distinct().size
        if (kinds != bestKinds) return kinds > bestKinds
        return candidate.map { it.surah }.distinct().size > best.map { it.surah }.distinct().size
    }

    /** One question of [kind] that uses no ayah of [used]; with [fresh] a surah that [used] has not met yet is looked for first. */
    private fun sample(kind: QuizKind, pool: Pool, words: (Int, Int) -> Int, used: Used, random: Random, fresh: Boolean): QuizQuestion? {
        val passes = if (fresh) listOf(true, false) else listOf(false)
        for (onlyFresh in passes) {
            val question = when (kind) {
                QuizKind.CONTINUE -> pickAyah(pool, used, random, onlyFresh) { surah, ayah ->
                    val total = words(surah, ayah)
                    if (total >= MIN_WORDS_TO_CONTINUE) ContinueQuestion(surah, ayah, shownWords(total), total) else null
                }
                QuizKind.NEXT -> pickAyah(pool, used, random, onlyFresh, extra = { s, a -> used.free(s, a + 1) }) { surah, ayah ->
                    if (pool.isCovered(surah, ayah + 1)) NextQuestion(surah, ayah) else null
                }
                QuizKind.START -> pickStart(pool, used, random, onlyFresh)
                QuizKind.ORDER -> pickOrder(pool, used, random, onlyFresh)
                QuizKind.WHERE -> pickAyah(pool, used, random, onlyFresh) { surah, ayah ->
                    if (ayah >= 2) WhereQuestion(surah, ayah, whereOptions(surah, pool.surahs, random)) else null
                }
            }
            if (question != null) return question
        }
        return null
    }

    /** Walks the covered ayahs from a random place round the whole pool and returns the first question [make] builds. */
    private inline fun pickAyah(
        pool: Pool,
        used: Used,
        random: Random,
        onlyFresh: Boolean,
        extra: (Int, Int) -> Boolean = { _, _ -> true },
        make: (surah: Int, ayah: Int) -> QuizQuestion?,
    ): QuizQuestion? {
        val size = pool.ayahs.size
        val offset = random.nextInt(size)
        for (step in 0 until size) {
            val key = pool.ayahs[(offset + step) % size]
            val surah = key.first
            val ayah = key.second
            if (onlyFresh && used.hasSurah(surah)) continue
            if (!used.free(surah, ayah) || !extra(surah, ayah)) continue
            make(surah, ayah)?.let { return it }
        }
        return null
    }

    private fun pickStart(pool: Pool, used: Used, random: Random, onlyFresh: Boolean): QuizQuestion? {
        val withFirst = pool.surahs.filter { pool.isCovered(it, 1) }
        if (withFirst.isEmpty()) return null
        val offset = random.nextInt(withFirst.size)
        for (step in withFirst.indices) {
            val surah = withFirst[(offset + step) % withFirst.size]
            if (onlyFresh && used.hasSurah(surah)) continue
            if (used.free(surah, 1)) return StartQuestion(surah)
        }
        return null
    }

    private fun pickOrder(pool: Pool, used: Used, random: Random, onlyFresh: Boolean): QuizQuestion? {
        val runs = pool.runs
        if (runs.isEmpty()) return null
        val offset = random.nextInt(runs.size)
        for (step in runs.indices) {
            val run = runs[(offset + step) % runs.size]
            if (onlyFresh && used.hasSurah(run.surah)) continue
            val length = run.end - run.start + 1
            val sizes = if (length >= ORDER_MAX && random.nextBoolean()) listOf(ORDER_MAX, ORDER_MIN) else listOf(ORDER_MIN)
            for (size in sizes) {
                val starts = (run.start..run.end - size + 1).toList()
                val tries = if (starts.size <= WINDOW_TRIES) starts.shuffled(random) else List(WINDOW_TRIES) { starts[random.nextInt(starts.size)] }
                for (from in tries) {
                    val ayahs = (from until from + size).toList()
                    if (ayahs.all { used.free(run.surah, it) }) return OrderQuestion(run.surah, ayahs, shuffledNotInOrder(ayahs, random))
                }
            }
        }
        return null
    }

    /** [ayahs] in an order that is never the original one (a rotation by one when the shuffle happens to give it back). */
    internal fun shuffledNotInOrder(ayahs: List<Int>, random: Random): List<Int> {
        val shuffled = ayahs.shuffled(random)
        return if (shuffled == ayahs) ayahs.drop(1) + ayahs.first() else shuffled
    }

    /**
     * Four numbers of surahs for "Where is this ayah": the right one and three others, in a random order. The others are covered
     * surahs; with fewer than three of them the nearest surahs by number fill the gap (the one before first, then the one after).
     */
    internal fun whereOptions(correct: Int, coveredSurahs: List<Int>, random: Random): List<Int> {
        val others = coveredSurahs.filter { it != correct }.shuffled(random).take(OTHER_OPTIONS).toMutableList()
        var distance = 1
        while (others.size < OTHER_OPTIONS && distance < QuranFacts.SURAH_COUNT) {
            for (candidate in listOf(correct - distance, correct + distance)) {
                if (others.size < OTHER_OPTIONS && candidate in 1..QuranFacts.SURAH_COUNT && candidate != correct && candidate !in others) {
                    others += candidate
                }
            }
            distance++
        }
        return (others + correct).shuffled(random)
    }

    /** The ayahs and the surahs the questions of a draw have used so far. */
    private class Used {
        private val ayahs = HashSet<Long>()
        private val surahs = HashSet<Int>()

        /** How many ayahs are used. */
        val count: Int get() = ayahs.size

        fun free(surah: Int, ayah: Int): Boolean = key(surah, ayah) !in ayahs

        fun hasSurah(surah: Int): Boolean = surah in surahs

        fun add(question: QuizQuestion) {
            surahs += question.surah
            question.ayahsUsed.forEach { ayahs += key(question.surah, it) }
        }

        private fun key(surah: Int, ayah: Int): Long = surah * 1000L + ayah
    }

    /** A run of covered ayahs of one surah that follow each other, at least [ORDER_MIN] long. */
    private class Run(val surah: Int, val start: Int, val end: Int)

    /** The covered ayahs as a sorted list of `(surah, ayah)` and the runs of them. */
    private class Pool(covered: Map<Int, Set<Int>>) {
        private val bySurah: Map<Int, Set<Int>> = covered.filterValues { it.isNotEmpty() }
        val surahs: List<Int> = bySurah.keys.sorted()
        val ayahs: List<Pair<Int, Int>> = surahs.flatMap { surah -> bySurah.getValue(surah).sorted().map { surah to it } }
        val runs: List<Run> = surahs.flatMap { surah ->
            val sorted = bySurah.getValue(surah).sorted()
            val found = ArrayList<Run>()
            var from = 0
            for (index in 1..sorted.size) {
                if (index == sorted.size || sorted[index] != sorted[index - 1] + 1) {
                    if (index - from >= ORDER_MIN) found += Run(surah, sorted[from], sorted[index - 1])
                    from = index
                }
            }
            found
        }

        fun isCovered(surah: Int, ayah: Int): Boolean = bySurah[surah]?.contains(ayah) == true
    }
}
