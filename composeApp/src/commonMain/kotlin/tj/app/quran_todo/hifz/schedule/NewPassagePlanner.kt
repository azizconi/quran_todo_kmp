package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan

/**
 * The next new passage of the plan.
 *
 * - The plan is a queue of surahs. The current surah is the first one that has uncovered ayahs.
 * - The passage starts at the first uncovered ayah and takes `dailyNewAyahs` ayahs, never crossing a covered ayah or the
 *   end of the surah.
 * - A surah with nothing covered whose length is at most `dailyNewAyahs * 1.5` is taken whole.
 * - If after the passage at most `dailyNewAyahs / 2` uncovered ayahs are left to the end of the surah, they are taken too.
 *
 * Null when the plan is finished (every surah of the queue is covered).
 */
object NewPassagePlanner {
    fun next(plan: HifzPlan, coverage: Coverage, metrics: TextMetrics): AyahRange? {
        val surah = HifzStatuses.currentSurah(plan, coverage) ?: return null
        val daily = plan.dailyNewAyahs
        val count = metrics.ayahCount(surah)
        val first = coverage.firstUncovered(surah) ?: return null

        if (coverage.coveredCount(surah) == 0 &&
            count * HifzConstants.WHOLE_SURAH_FACTOR_DEN <= daily * HifzConstants.WHOLE_SURAH_FACTOR_NUM
        ) {
            return AyahRange(surah, 1, count)
        }

        var end = minOf(first + daily - 1, count)
        for (ayah in first..end) {
            if (coverage.isCovered(surah, ayah)) {
                end = ayah - 1
                break
            }
        }
        if (end < count) {
            val left = count - end
            val tailIsFree = (end + 1..count).none { coverage.isCovered(surah, it) }
            if (tailIsFree && left * 2 <= daily) end = count
        }
        return AyahRange(surah, first, end)
    }
}
