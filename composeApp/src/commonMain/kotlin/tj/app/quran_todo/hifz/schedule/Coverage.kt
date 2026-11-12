package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus

/**
 * Which ayahs are covered ("gone through", "пройдено") by passages. The plan, the forecast and the quiz pool move by it; what the
 * person is shown as learned comes from [LearnedAyahs].
 */
class Coverage(passages: List<Passage>, private val metrics: TextMetrics) {
    private val covered = arrayOfNulls<BooleanArray>(QuranFacts.SURAH_COUNT + 1)
    private val counts = IntArray(QuranFacts.SURAH_COUNT + 1)

    init {
        for (passage in passages) {
            val surah = passage.surah
            if (surah !in 1..QuranFacts.SURAH_COUNT) continue
            val flags = covered[surah] ?: BooleanArray(metrics.ayahCount(surah) + 1).also { covered[surah] = it }
            for (ayah in passage.startAyah..minOf(passage.endAyah, flags.size - 1)) {
                if (ayah >= 1 && !flags[ayah]) {
                    flags[ayah] = true
                    counts[surah]++
                }
            }
        }
    }

    fun isCovered(surah: Int, ayah: Int): Boolean = covered.getOrNull(surah)?.getOrNull(ayah) == true

    fun coveredCount(surah: Int): Int = counts.getOrElse(surah) { 0 }

    fun uncoveredCount(surah: Int): Int = metrics.ayahCount(surah) - coveredCount(surah)

    fun isFullyCovered(surah: Int): Boolean = metrics.ayahCount(surah) > 0 && uncoveredCount(surah) == 0

    /** The lowest uncovered ayah of [surah], or null when the surah is covered completely. */
    fun firstUncovered(surah: Int): Int? =
        (1..metrics.ayahCount(surah)).firstOrNull { !isCovered(surah, it) }

    fun coveredAyahs(surah: Int): Set<Int> =
        (1..metrics.ayahCount(surah)).filterTo(LinkedHashSet()) { isCovered(surah, it) }

    /** All ayahs of the plan's surahs that are not covered yet. */
    fun remaining(plan: HifzPlan): Int = plan.surahOrder.distinct().sumOf { uncoveredCount(it) }

    /** All ayahs of the plan's surahs. */
    fun total(plan: HifzPlan): Int = plan.surahOrder.distinct().sumOf { metrics.ayahCount(it) }
}

/**
 * Which ayahs are learned ("выучено"): covered by passages that have passed the repetition that comes 7 days after the previous
 * one, that is whose stage is beyond R7 ([isLearnedStage]: R14, R30 or OLD). A passage that falls down the ladder after a
 * "Forgot" answer makes its ayahs "being learned" again. An ayah that more than one passage covers is learned only when all of
 * them are. Everything else that is covered is being learned.
 */
class LearnedAyahs(passages: List<Passage>, private val metrics: TextMetrics) {
    private val learned = arrayOfNulls<BooleanArray>(QuranFacts.SURAH_COUNT + 1)
    private val counts = IntArray(QuranFacts.SURAH_COUNT + 1)

    init {
        val notYet = arrayOfNulls<BooleanArray>(QuranFacts.SURAH_COUNT + 1)
        for (passage in passages) {
            val surah = passage.surah
            if (surah !in 1..QuranFacts.SURAH_COUNT) continue
            val size = metrics.ayahCount(surah) + 1
            val flags = (if (isLearnedStage(passage.stage)) learned else notYet)
                .let { it[surah] ?: BooleanArray(size).also { f -> it[surah] = f } }
            for (ayah in passage.startAyah..minOf(passage.endAyah, size - 1)) if (ayah >= 1) flags[ayah] = true
        }
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            val flags = learned[surah] ?: continue
            val blocked = notYet[surah]
            for (ayah in flags.indices) {
                if (flags[ayah] && blocked?.get(ayah) == true) flags[ayah] = false
                if (flags[ayah]) counts[surah]++
            }
        }
    }

    fun isLearned(surah: Int, ayah: Int): Boolean = learned.getOrNull(surah)?.getOrNull(ayah) == true

    fun learnedCount(surah: Int): Int = counts.getOrElse(surah) { 0 }

    fun learnedAyahs(surah: Int): Set<Int> =
        (1..metrics.ayahCount(surah)).filterTo(LinkedHashSet()) { isLearned(surah, it) }

    fun isFullyLearned(surah: Int): Boolean = metrics.ayahCount(surah) > 0 && learnedCount(surah) == metrics.ayahCount(surah)

    companion object {
        /** Past the 7-day repetition: R14, R30 or OLD. */
        fun isLearnedStage(stage: PassageStage): Boolean = stage.rank > PassageStage.R7.rank
    }
}

object HifzStatuses {
    /** The surah the plan is learning now: the first of the queue that still has uncovered ayahs. */
    fun currentSurah(plan: HifzPlan?, coverage: Coverage): Int? =
        plan?.surahOrder?.firstOrNull { coverage.uncoveredCount(it) > 0 }

    /**
     * What the person is shown: `LEARNED` when every ayah is learned ([LearnedAyahs]); `LEARNING` when some are covered but not all
     * are learned, or when this is the current surah of the plan; otherwise no status.
     */
    fun displayStatus(coverage: Coverage, learned: LearnedAyahs, metrics: TextMetrics, surah: Int, current: Int?): SurahHifzStatus? {
        val total = metrics.ayahCount(surah)
        return when {
            total > 0 && learned.learnedCount(surah) == total -> SurahHifzStatus.LEARNED
            coverage.coveredCount(surah) > 0 || surah == current -> SurahHifzStatus.LEARNING
            else -> null
        }
    }

    /** The displayed statuses of all surahs that have one. */
    fun allDisplay(passages: List<Passage>, plan: HifzPlan?, metrics: TextMetrics): Map<Int, SurahHifzStatus> {
        val coverage = Coverage(passages, metrics)
        val learned = LearnedAyahs(passages, metrics)
        val current = currentSurah(plan, coverage)
        val result = LinkedHashMap<Int, SurahHifzStatus>()
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            displayStatus(coverage, learned, metrics, surah, current)?.let { result[surah] = it }
        }
        return result
    }

    /**
     * The status by coverage: `LEARNED` when every ayah is covered ("gone through"); `LEARNING` when some are, or when this is the current surah of the plan;
     * otherwise no status.
     */
    fun surahStatus(coverage: Coverage, metrics: TextMetrics, surah: Int, current: Int?): SurahHifzStatus? {
        val total = metrics.ayahCount(surah)
        val done = coverage.coveredCount(surah)
        return when {
            total > 0 && done == total -> SurahHifzStatus.LEARNED
            done > 0 || surah == current -> SurahHifzStatus.LEARNING
            else -> null
        }
    }

    /** The statuses by coverage of all surahs that have one (the plan and the legacy mirror follow these). */
    fun all(passages: List<Passage>, plan: HifzPlan?, metrics: TextMetrics): Map<Int, SurahHifzStatus> {
        val coverage = Coverage(passages, metrics)
        val current = currentSurah(plan, coverage)
        val result = LinkedHashMap<Int, SurahHifzStatus>()
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            surahStatus(coverage, metrics, surah, current)?.let { result[surah] = it }
        }
        return result
    }

    /**
     * The difficult ayahs of every surah that has any (numbers in the surah, ascending), the surahs in mushaf order. An ayah
     * marked in more than one passage of a surah is listed once.
     */
    fun hardAyahs(passages: List<Passage>): Map<Int, Set<Int>> =
        passages
            .filter { it.hard.isNotEmpty() }
            .groupBy { it.surah }
            .toList()
            .sortedBy { (surah, _) -> surah }
            .associate { (surah, own) -> surah to own.flatMap { it.hard.keys }.distinct().sorted().toSet() }

    fun surahCoverage(surah: Int, passages: List<Passage>, plan: HifzPlan?, metrics: TextMetrics): SurahCoverage {
        val coverage = Coverage(passages, metrics)
        val learned = LearnedAyahs(passages, metrics)
        val current = currentSurah(plan, coverage)
        val own = passages.filter { it.surah == surah }.sortedBy { it.startAyah }
        return SurahCoverage(
            surah = surah,
            ayahCount = metrics.ayahCount(surah),
            coveredAyahs = coverage.coveredAyahs(surah),
            learnedAyahs = learned.learnedAyahs(surah),
            hardAyahs = own.flatMapTo(LinkedHashSet()) { it.hard.keys },
            passages = own,
            isCurrent = surah == current,
            status = displayStatus(coverage, learned, metrics, surah, current),
        )
    }
}
