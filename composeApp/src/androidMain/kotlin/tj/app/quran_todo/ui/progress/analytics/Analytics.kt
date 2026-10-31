package tj.app.quran_todo.ui.progress.analytics

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.ui.common.JuzIndex

/** Retention of one surah or one juz: [kept] of [repetitions] repetitions were not graded "Forgot". [id] is the surah or the juz number. */
@Immutable
data class RetentionRow(val id: Int, val repetitions: Int, val kept: Int) {
    /** Whole percent: never 100 while a repetition was forgotten, never 0 while one was kept. */
    val percent: Int get() = Analytics.percentOf(kept, repetitions)
    val fraction: Float get() = if (repetitions == 0) 0f else kept.toFloat() / repetitions
}

/**
 * One day of the year heat map. [count] is the number of records of the studying (new, repeated, checked) of the day, the
 * number the colour says; [listened] is told apart ("Listen to the old" is not studying and does not colour the cell). [paused] is a
 * day of the plan pause that had no studying.
 */
@Immutable
data class HeatDay(
    val epochDay: Int,
    val count: Int,
    val newCount: Int,
    val reviewedCount: Int,
    val listened: Int,
    val paused: Boolean,
)

/**
 * One calendar [year] as weeks of seven cells, Monday first. A cell is null outside the year and after today. [max] is the busiest
 * day of the year, [activeDays] the days with studying.
 */
@Immutable
data class HeatYear(
    val year: Int,
    val weeks: List<List<HeatDay?>>,
    val max: Int,
    val activeDays: Int,
)

/** Where a surah of the plan stands. */
enum class QueueState { Learned, Current, Queued }

/** A surah of the plan queue with the day the pace of the plan finishes it ([finishEpochDay] is null when there is no pace to read). */
@Immutable
data class SurahForecast(
    val surah: Int,
    val remainingAyahs: Int,
    val totalAyahs: Int,
    val state: QueueState,
    val finishEpochDay: Int?,
)

/** A difficult place: [misses] is how many times it was graded "Hard" or "Forgot" lately, [current] is a mark that is set now. */
@Immutable
data class HardPlace(val surah: Int, val ayah: Int, val misses: Int, val current: Boolean)

/** The pure arithmetic of the detailed analytics (no storage, no clock), covered by unit tests. */
object Analytics {
    /** The window of the retention and the difficult places. */
    const val WindowDays = 90

    /** A surah or juz needs this many repetitions in the window to be listed: one forgotten repetition is not a trend. */
    const val MinRepetitions = 3

    const val MaxHardPlaces = 10

    /** A range of more ayahs than this, graded Hard, is not blamed on its ayahs one by one (it says nothing about a single place). */
    private const val BlameLimit = 5

    /** The share of kept repetitions, with the honest edges of the retention tile on the screen. */
    fun percentOf(kept: Int, total: Int): Int = when {
        total <= 0 -> 0
        kept >= total -> 100
        else -> (kept * 100f / total).roundToInt().coerceIn(if (kept > 0) 1 else 0, 99)
    }

    private fun repetitions(log: List<ReviewLogEntry>, today: Int): List<ReviewLogEntry> {
        val from = today - WindowDays + 1
        return log.filter { it.kind != ReviewKind.NEW && it.kind.countsAsStudy && it.epochDay in from..today }
    }

    private fun rows(grouped: Map<Int, List<ReviewLogEntry>>): List<RetentionRow> =
        grouped.map { (id, entries) ->
            RetentionRow(id, repetitions = entries.size, kept = entries.count { it.grade != ReviewGrade.FORGOT })
        }
            .filter { it.repetitions >= MinRepetitions }
            .sortedWith(compareBy<RetentionRow> { it.percent }.thenByDescending { it.repetitions }.thenBy { it.id })

    /** Surahs with at least [MinRepetitions] repetitions in the last [WindowDays] days, the weakest first. */
    fun retentionBySurah(log: List<ReviewLogEntry>, today: Int): List<RetentionRow> =
        rows(repetitions(log, today).groupBy { it.surah })

    /** The same per juz; a record belongs to the juz its first ayah is in. */
    fun retentionByJuz(log: List<ReviewLogEntry>, today: Int): List<RetentionRow> =
        rows(repetitions(log, today).groupBy { JuzIndex.juzOf(it.surah, it.startAyah) })

    // -- Heat map -------------------------------------------------------------------------------------

    /** Years the heat map can show, ascending: from the first year with a record up to the year of [today]. */
    fun years(log: List<ReviewLogEntry>, listenLog: List<ReviewLogEntry>, today: Int): List<Int> {
        val current = LocalDate.fromEpochDays(today).year
        val first = (log.asSequence() + listenLog.asSequence())
            .filter { it.epochDay <= today }
            .minOfOrNull { it.epochDay }
            ?.let { LocalDate.fromEpochDays(it).year }
            ?.coerceAtMost(current)
            ?: current
        return (first..current).toList()
    }

    /** The colour step of a cell, 0 (no studying) to 4: the busiest day of the year is the darkest. */
    fun level(count: Int, max: Int): Int {
        if (count <= 0 || max <= 0) return 0
        return ceil(count * 4.0 / max).toInt().coerceIn(1, 4)
    }

    /** The year [year] from the records, the "listened" records and the pause (the pause that is known: a past pause is not stored). */
    fun heatmap(year: Int, today: Int, log: List<ReviewLogEntry>, listenLog: List<ReviewLogEntry>, pause: PlanPause?): HeatYear {
        val first = LocalDate(year, 1, 1)
        val firstDay = first.toEpochDays()
        val lastDay = LocalDate(year, 12, 31).toEpochDays()
        val newCount = HashMap<Int, Int>()
        val reviewed = HashMap<Int, Int>()
        for (entry in log) {
            if (!entry.kind.countsAsStudy || entry.epochDay !in firstDay..lastDay) continue
            val bucket = if (entry.kind == ReviewKind.NEW) newCount else reviewed
            bucket[entry.epochDay] = (bucket[entry.epochDay] ?: 0) + 1
        }
        val listened = HashMap<Int, Int>()
        for (entry in listenLog) {
            if (entry.kind != ReviewKind.LISTEN || entry.epochDay !in firstDay..lastDay) continue
            listened[entry.epochDay] = (listened[entry.epochDay] ?: 0) + 1
        }

        // Monday is 0: the enum starts with it.
        val offset = first.dayOfWeek.ordinal
        val days = lastDay - firstDay + 1
        val weekCount = (offset + days + 6) / 7
        var max = 0
        var active = 0
        val weeks = List(weekCount) { week ->
            List(7) { weekday ->
                val day = firstDay + week * 7 + weekday - offset
                if (day !in firstDay..lastDay || day > today) {
                    null
                } else {
                    val n = newCount[day] ?: 0
                    val r = reviewed[day] ?: 0
                    val count = n + r
                    if (count > max) max = count
                    if (count > 0) active++
                    HeatDay(
                        epochDay = day,
                        count = count,
                        newCount = n,
                        reviewedCount = r,
                        listened = listened[day] ?: 0,
                        paused = count == 0 && pause != null && day in pause,
                    )
                }
            }
        }
        return HeatYear(year, weeks, max, active)
    }

    // -- Forecast by surah ----------------------------------------------------------------------------

    /**
     * The surahs of the plan queue in the plan's order, each with when the pace finishes it. The dates follow `Forecast`: the day the
     * queue starts is read from [forecast] (`finishEpochDay - ceil(remaining / daily)`, so a pause and "today's new passage is done"
     * are already in it) and a surah is finished after `ceil(ayahs left up to and including it / daily)` days, so the last surah gets
     * exactly the finish day of the plan line. A learned surah has no date; with no plan the list is empty.
     */
    fun surahForecasts(plan: HifzPlan?, forecast: PlanForecast?, covered: Map<Int, Set<Int>>): List<SurahForecast> {
        plan ?: return emptyList()
        val daily = (forecast?.dailyNewAyahs ?: plan.dailyNewAyahs).coerceAtLeast(1)
        val remainingOf = { surah: Int ->
            (QuranFacts.ayahCount(surah) - (covered[surah]?.size ?: 0)).coerceAtLeast(0)
        }
        val totalRemaining = plan.surahOrder.sumOf(remainingOf)
        val finish = forecast?.finishEpochDay
        val start = if (finish != null && !forecast.finished) finish - (totalRemaining + daily - 1) / daily else null
        var cumulative = 0
        var currentSeen = false
        return plan.surahOrder.map { surah ->
            val remaining = remainingOf(surah)
            if (remaining == 0) {
                SurahForecast(surah, 0, QuranFacts.ayahCount(surah), QueueState.Learned, null)
            } else {
                cumulative += remaining
                val state = if (!currentSeen) QueueState.Current else QueueState.Queued
                currentSeen = true
                SurahForecast(
                    surah = surah,
                    remainingAyahs = remaining,
                    totalAyahs = QuranFacts.ayahCount(surah),
                    state = state,
                    finishEpochDay = start?.plus((cumulative + daily - 1) / daily),
                )
            }
        }
    }

    // -- Hard places ----------------------------------------------------------------------------------

    /**
     * Up to [limit] ayahs with the most "Hard" or "Forgot" grades in the last [WindowDays] days, or marked difficult now
     * (`hardAyahs()`); the most missed first, a mark that is set now before one that is not, then in the order of the mushaf.
     * A single-ayah record blames its ayah. A longer record blames the ayahs of its range that are marked now, and only when none is
     * marked and the range is at most [BlameLimit] ayahs long it blames all of them: a Hard grade for a long passage does not say
     * which place it was.
     */
    fun hardPlaces(
        log: List<ReviewLogEntry>,
        hard: Map<Int, Set<Int>>,
        today: Int,
        limit: Int = MaxHardPlaces,
    ): List<HardPlace> {
        val from = today - WindowDays + 1
        val misses = HashMap<Int, Int>()
        fun key(surah: Int, ayah: Int) = surah * 1000 + ayah
        for (entry in log) {
            if (!entry.kind.countsAsStudy || entry.epochDay !in from..today) continue
            if (entry.grade != ReviewGrade.HARD && entry.grade != ReviewGrade.FORGOT) continue
            val range = entry.startAyah..entry.endAyah
            if (range.isEmpty()) continue
            val marked = hard[entry.surah].orEmpty()
            val blamed = when {
                range.count() == 1 -> range.toList()
                else -> range.filter { it in marked }.ifEmpty { if (range.count() <= BlameLimit) range.toList() else emptyList() }
            }
            for (ayah in blamed) misses[key(entry.surah, ayah)] = (misses[key(entry.surah, ayah)] ?: 0) + 1
        }
        val keys = HashSet(misses.keys)
        for ((surah, ayahs) in hard) for (ayah in ayahs) keys.add(key(surah, ayah))
        return keys.map { k ->
            val surah = k / 1000
            val ayah = k % 1000
            HardPlace(surah, ayah, misses[k] ?: 0, current = ayah in hard[surah].orEmpty())
        }
            .sortedWith(
                compareByDescending<HardPlace> { it.misses }.thenByDescending { it.current }.thenBy { it.surah }.thenBy { it.ayah },
            )
            .take(limit)
    }
}
