package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus

/** Which ayahs are covered by passages. Statuses of ayahs and surahs are derived from it. */
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

object HifzStatuses {
    /** The surah the plan is learning now: the first of the queue that still has uncovered ayahs. */
    fun currentSurah(plan: HifzPlan?, coverage: Coverage): Int? =
        plan?.surahOrder?.firstOrNull { coverage.uncoveredCount(it) > 0 }

    /**
     * `LEARNED` when every ayah is covered; `LEARNING` when some are, or when this is the current surah of the plan;
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

    /** The statuses of all surahs that have one. */
    fun all(passages: List<Passage>, plan: HifzPlan?, metrics: TextMetrics): Map<Int, SurahHifzStatus> {
        val coverage = Coverage(passages, metrics)
        val current = currentSurah(plan, coverage)
        val result = LinkedHashMap<Int, SurahHifzStatus>()
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            surahStatus(coverage, metrics, surah, current)?.let { result[surah] = it }
        }
        return result
    }

    fun surahCoverage(surah: Int, passages: List<Passage>, plan: HifzPlan?, metrics: TextMetrics): SurahCoverage {
        val coverage = Coverage(passages, metrics)
        val current = currentSurah(plan, coverage)
        val own = passages.filter { it.surah == surah }.sortedBy { it.startAyah }
        return SurahCoverage(
            surah = surah,
            ayahCount = metrics.ayahCount(surah),
            coveredAyahs = coverage.coveredAyahs(surah),
            hardAyahs = own.flatMapTo(LinkedHashSet()) { it.hard.keys },
            passages = own,
            isCurrent = surah == current,
            status = surahStatus(coverage, metrics, surah, current),
        )
    }
}
