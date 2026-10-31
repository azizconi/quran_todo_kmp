package tj.app.quran_todo.ui.progress.report

import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.ui.progress.HeroUi
import tj.app.quran_todo.ui.progress.PlanLineUi
import tj.app.quran_todo.ui.progress.ProgressBuilder
import tj.app.quran_todo.ui.progress.ProgressSources

/** The period of the report for the teacher: the last [days] days up to and including today. */
internal enum class ReportPeriod(val days: Int, val analyticsName: String) {
    Week(7, "week"),
    Month(30, "month"),

    /** A range chosen with the date picker; [days] is only the length used when no range is given. */
    Custom(30, "custom"),
}

/** A continuous range of ayahs of one surah (numbers in the surah) learned in the period. */
internal data class ReportRange(val surah: Int, val startAyah: Int, val endAyah: Int) {
    val ayahCount: Int get() = endAyah - startAyah + 1
}

/** A surah with difficult ayahs (numbers in the surah, ascending). */
internal data class ReportHard(val surah: Int, val ayahs: List<Int>)

/**
 * What the report says, as numbers and surah numbers (no words: [ReportComposer] puts the language in). Built from the same
 * [ProgressSources] as the screen, so the report and the screen never disagree.
 */
internal data class ProgressReport(
    val period: ReportPeriod,
    val fromDay: Int,
    val toDay: Int,
    /** The ring: everything memorized so far. */
    val hero: HeroUi,
    /** What was learned for the first time in the period, per surah in the order it was learned, neighbours merged. */
    val newRanges: List<ReportRange>,
    val newAyahs: Int,
    /** Distinct ayahs repeated in the period (RECENT, OLD and CHECK records). */
    val reviewedAyahs: Int,
    /** How many repetitions (records) that was. */
    val repetitions: Int,
    /** The surahs repeated, ascending. */
    val reviewedSurahs: List<Int>,
    /** Share of the repetitions of the period that were not forgotten; null without repetitions. */
    val retentionPercent: Int?,
    /** Difficult ayahs now, the most affected surah first. */
    val hard: List<ReportHard>,
    val hardAyahs: Int,
    val streak: Int,
    /** Days of the period with at least one record. */
    val activeDays: Int,
    val plan: PlanLineUi,
    /**
     * Records of the studying per day of the period, oldest first (the heat strip of the report); empty for a period longer than
     * [ReportBuilder.MaxStripDays] days, which would not fit as a strip.
     */
    val strip: List<Int> = emptyList(),
) {
    val dayCount: Int get() = toDay - fromDay + 1
}

/** The pure function "review log + coverage + plan -> report", covered by unit tests. */
internal object ReportBuilder {
    /** A strip of more days than this is left out of the report (a year of cells does not fit on a card). */
    const val MaxStripDays = 93

    /**
     * The report of [period]. The days are the last `period.days` up to today unless [fromDay] and [toDay] say otherwise (a range of
     * the date picker); [log] is the review log that covers them (the sources hold only the last 30 days).
     */
    fun build(
        s: ProgressSources,
        period: ReportPeriod,
        fromDay: Int = (s.today - period.days + 1),
        toDay: Int = s.today,
        log: List<ReviewLogEntry> = s.log,
    ): ProgressReport {
        val to = toDay
        val from = fromDay
        // "Listened" records are not part of the report of the studying (task F1b).
        val inPeriod = log.filter { it.epochDay in from..to && it.kind.countsAsStudy }
        val learned = inPeriod.filter { it.kind == ReviewKind.NEW }
        val repeated = inPeriod.filter { it.kind != ReviewKind.NEW }

        val ranges = mergedRanges(learned)
        val reviewedAyahs = HashSet<Int>()
        for (entry in repeated) for (ayah in entry.startAyah..entry.endAyah) reviewedAyahs.add(entry.surah * 1000 + ayah)

        val coveredCount = s.covered.values.sumOf { it.size }
        val hard = ProgressBuilder.hardSpots(s).map { ReportHard(it.surah, it.ayahs) }
        return ProgressReport(
            period = period,
            fromDay = from,
            toDay = to,
            hero = ProgressBuilder.hero(
                coveredAyahs = coveredCount,
                learnedSurahs = s.statuses.values.count { it == SurahHifzStatus.LEARNED },
                learningSurahs = s.statuses.values.count { it == SurahHifzStatus.LEARNING },
            ),
            newRanges = ranges,
            newAyahs = ranges.sumOf { it.ayahCount },
            reviewedAyahs = reviewedAyahs.size,
            repetitions = repeated.size,
            reviewedSurahs = repeated.map { it.surah }.distinct().sorted(),
            retentionPercent = ProgressBuilder.retention(inPeriod, from, to).percent,
            hard = hard,
            hardAyahs = hard.sumOf { it.ayahs.size },
            streak = s.streak,
            activeDays = ProgressBuilder.activeDays(inPeriod, from, to),
            plan = ProgressBuilder.planLine(s.plan, s.forecast),
            strip = strip(inPeriod, from, to),
        )
    }

    /** Records of the studying per day of [fromDay]..[toDay]; empty when the period is longer than [MaxStripDays]. */
    fun strip(study: List<ReviewLogEntry>, fromDay: Int, toDay: Int): List<Int> {
        if (toDay < fromDay || toDay - fromDay + 1 > MaxStripDays) return emptyList()
        val counts = IntArray(toDay - fromDay + 1)
        for (entry in study) if (entry.kind.countsAsStudy && entry.epochDay in fromDay..toDay) counts[entry.epochDay - fromDay]++
        return counts.toList()
    }

    /**
     * The ranges learned by [newRecords]: per surah (in the order the surahs were first learned) the ayahs of all the records,
     * sorted, with overlapping and neighbouring ranges merged ("1-3" and "4-5" learned on two days read "1-5").
     */
    fun mergedRanges(newRecords: List<ReviewLogEntry>): List<ReportRange> {
        val bySurah = LinkedHashMap<Int, MutableList<IntRange>>()
        for (entry in newRecords.sortedWith(compareBy<ReviewLogEntry> { it.epochDay }.thenBy { it.id })) {
            if (entry.endAyah < entry.startAyah) continue
            bySurah.getOrPut(entry.surah) { ArrayList() }.add(entry.startAyah..entry.endAyah)
        }
        return bySurah.flatMap { (surah, ranges) ->
            val merged = ArrayList<IntRange>()
            for (range in ranges.sortedBy { it.first }) {
                val last = merged.lastOrNull()
                if (last != null && range.first <= last.last + 1) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    merged.add(range)
                }
            }
            merged.map { ReportRange(surah, it.first, it.last) }
        }
    }
}
