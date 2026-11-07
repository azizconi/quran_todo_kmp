package tj.app.quran_todo.ui.downloads

import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.ui.common.JuzIndex

/** What a row of the screen means in ayahs: the pure part of "choose, then list the ayahs to fetch". */
internal object DownloadSelection {
    /** The whole surah. */
    fun surah(surah: Int): List<Segment> {
        val count = QuranFacts.ayahCount(surah)
        require(count > 0) { "surah $surah" }
        return listOf(Segment(surah, 1, count))
    }

    /** Juz [juz] (1..30): the first and the last surah can be partial. */
    fun juz(juz: Int): List<Segment> {
        val range = JuzIndex.range(juz)
        return range.surahs.map { surah ->
            val ayahs = checkNotNull(range.ayahsOf(surah))
            Segment(surah, ayahs.first, ayahs.last)
        }
    }

    /** Whole surahs in the given order, each once. */
    fun surahs(surahs: Iterable<Int>): List<Segment> = surahs.distinct().filter { it in 1..QuranFacts.SURAH_COUNT }.map { surah(it).single() }

    /**
     * The surahs of "My plan": the queue of the plan that are being learned or are learned (the learned ones are repeated), and the
     * first surah of the queue that is not learned yet, so that a plan that has just been made offers its first surah. A surah that
     * is not in the queue of the plan is not in the list. Empty when [plan] is null.
     */
    fun planSurahs(plan: HifzPlan?, statuses: Map<Int, SurahHifzStatus>): List<Int> {
        if (plan == null) return emptyList()
        val next = plan.surahOrder.firstOrNull { statuses[it] != SurahHifzStatus.LEARNED }
        return plan.surahOrder.distinct().filter { it == next || statuses[it] != null }
    }

    /** The cache keys of [segments] for [reciter], in reading order. */
    fun keys(segments: List<Segment>, reciter: Reciter): List<String> = segments.flatMap { segment ->
        (segment.first..segment.last).map { ayah -> reciter.cacheKey(segment.surah, ayah) }
    }

    /**
     * Every ayah of [requests] once: a juz and a surah inside it, or a plan that overlaps a juz, ask for the same files, which are
     * fetched (and counted as "left") only once.
     */
    fun uniqueKeys(requests: List<DownloadRequest>): LinkedHashSet<String> {
        val all = LinkedHashSet<String>()
        requests.forEach { all.addAll(it.keys()) }
        return all
    }
}
