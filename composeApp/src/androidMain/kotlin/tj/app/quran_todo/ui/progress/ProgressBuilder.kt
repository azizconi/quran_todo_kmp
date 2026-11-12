package tj.app.quran_todo.ui.progress

import kotlin.math.roundToInt
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.ui.common.JuzIndex
import tj.app.quran_todo.ui.lesson.LessonCards

/**
 * Everything the screen and the report are made of: the answers of [tj.app.quran_todo.hifz.HifzRepository] for one
 * moment, the day they were read for and the names of the surahs in the language of the app. Nothing here comes from the
 * old tables or from the preferences.
 */
internal class ProgressSources(
    /** The epoch day the answers are for (`EpochDayTicker.day`). */
    val today: Int,
    val language: AppLanguage,
    /** The 114 surah names by the one naming rule of the Android UI; index 0 is surah 1. */
    val names: List<String>,
    /** `surahStatuses()`: only surahs with a status; a surah that is not in the map is not started. */
    val statuses: Map<Int, SurahHifzStatus>,
    /** `coveredAyahs()`: covered ("gone through") ayah numbers (in the surah) of every surah that has any. */
    val covered: Map<Int, Set<Int>>,
    /** `learnedAyahs()`: learned ayah numbers (the passage is past the 7-day repetition), a subset of [covered]. */
    val learned: Map<Int, Set<Int>> = emptyMap(),
    /** `hardAyahs()`: difficult ayah numbers (in the surah) of every surah that has any. */
    val hard: Map<Int, Set<Int>>,
    /** `reviewLog(today - 29, today)`: the last [ProgressBuilder.LogDays] days, oldest first. */
    val log: List<ReviewLogEntry>,
    /** `streak(today)`. */
    val streak: Int,
    val lesson: LessonPlan?,
    val plan: HifzPlan?,
    val forecast: PlanForecast?,
    /** `listenLog(today - 29, today)`: the "listened" records of the same days ("Listen to the old"); shown, never counted as study. */
    val listenLog: List<ReviewLogEntry> = emptyList(),
) {
    fun nameOf(surah: Int): String = names.getOrNull(surah - 1) ?: "№ $surah"
}

/** The share of repetitions that were not forgotten, and how many repetitions it is made of. */
internal data class Retention(val percent: Int?, val repetitions: Int)

/** The pure arithmetic of the progress screen (no storage, no clock), covered by unit tests. */
internal object ProgressBuilder {
    /** Days of the activity chart. */
    const val ActivityDays = 30

    /** Days the retention tile looks at. */
    const val RetentionDays = 30

    /** How far back the review log is read: enough for the chart, the retention and the report of a month. */
    const val LogDays = 30

    fun build(s: ProgressSources): ProgressUi {
        val coveredCount = s.covered.values.sumOf { it.size }
        val learnedCount = s.learned.values.sumOf { it.size }
        val learnedSurahs = s.statuses.values.count { it == SurahHifzStatus.LEARNED }
        val learningSurahs = s.statuses.values.count { it == SurahHifzStatus.LEARNING }
        val nothingYet = coveredCount == 0 && s.log.none { it.kind.countsAsStudy }
        val retention = retention(s.log, s.today - RetentionDays + 1, s.today)
        return ProgressUi(
            phase = if (nothingYet) ProgressPhase.Empty else ProgressPhase.Content,
            today = s.today,
            hero = hero(coveredCount, learnedCount, learnedSurahs, learningSurahs, ringOf(s.plan, s.covered, s.learned)),
            cells = cells(s),
            tiles = TilesUi(
                streakDays = s.streak,
                today = today(s.lesson),
                retentionPercent = retention.percent,
                retentionRepetitions = retention.repetitions,
                hardAyahs = s.hard.values.sumOf { it.size },
            ),
            activity = ActivityUi(withListened(activity(s.log, s.today), s.listenLog)),
            juz = juz(s.learned),
            plan = planLine(s.plan, s.forecast),
            hard = hardSpots(s),
        )
    }

    /** The plan's whole as the ring counts it: its distinct surahs, the ayahs covered and learned among them. */
    class PlanRing(val scope: HifzScope, val covered: Int, val learned: Int, val total: Int)

    /** The ring of the plan from the covered and learned ayahs; null without a plan (or with a plan of no surahs). */
    fun ringOf(plan: HifzPlan?, covered: Map<Int, Set<Int>>, learned: Map<Int, Set<Int>>): PlanRing? {
        plan ?: return null
        val surahs = plan.surahOrder.distinct().filter { it in 1..QuranFacts.SURAH_COUNT }
        val total = surahs.sumOf { QuranFacts.ayahCount(it) }
        if (total <= 0) return null
        return PlanRing(
            scope = plan.scope,
            covered = surahs.sumOf { (covered[it]?.size ?: 0).coerceAtMost(QuranFacts.ayahCount(it)) },
            learned = surahs.sumOf { (learned[it]?.size ?: 0).coerceAtMost(QuranFacts.ayahCount(it)) },
            total = total,
        )
    }

    /**
     * Whole percent of [covered] in [total]. Honest at the edges and never "<1": 0 only when nothing is covered, 100 only when
     * everything is, otherwise rounded to the nearest and kept inside 1..99.
     */
    fun percentOf(covered: Int, total: Int): Int = when {
        total <= 0 || covered <= 0 -> 0
        covered >= total -> 100
        else -> (covered * 100f / total).roundToInt().coerceIn(1, 99)
    }

    /**
     * The ring: with a [plan] ring, its covered ayahs of the plan's ayahs; without one the covered ayahs of the whole Quran.
     * The whole Quran numbers stay in the hero for the line under the ring.
     */
    fun hero(
        coveredAyahs: Int,
        learnedAyahs: Int,
        learnedSurahs: Int,
        learningSurahs: Int,
        plan: PlanRing? = null,
    ): HeroUi {
        val total = QuranFacts.AYAH_COUNT
        val ringCovered = plan?.covered ?: coveredAyahs
        val ringTotal = plan?.total ?: total
        return HeroUi(
            coveredAyahs = coveredAyahs,
            learnedAyahs = learnedAyahs,
            totalAyahs = total,
            learnedSurahs = learnedSurahs,
            learningSurahs = learningSurahs,
            percent = percentOf(ringCovered, ringTotal),
            fraction = (ringCovered.toFloat() / ringTotal).coerceIn(0f, 1f),
            planScope = plan?.scope,
            ringCovered = ringCovered,
            ringLearned = plan?.learned ?: learnedAyahs,
            ringTotal = ringTotal,
        )
    }

    /** Days of the week block: the last seven, today included. */
    const val WeekDays = 7

    /** The week block's days (oldest first, today last) out of the 30 days of the activity. */
    fun week(activity: ActivityUi): ActivityUi = ActivityUi(activity.days.takeLast(WeekDays))

    /** The blocks of the page in order; nothing before the first lesson, where the invitation stands alone. */
    fun blocks(ui: ProgressUi): List<ProgressBlock> =
        if (ui.phase == ProgressPhase.Content) ProgressBlock.entries else emptyList()

    /** The 114 cells of the map: the derived status of the surah and how many of its ayahs are learned. */
    fun cells(s: ProgressSources): List<MapCellUi> = List(QuranFacts.SURAH_COUNT) { index ->
        val number = index + 1
        val count = QuranFacts.ayahCount(number)
        val covered = (s.learned[number]?.size ?: 0).coerceAtMost(count)
        MapCellUi(
            number = number,
            name = s.nameOf(number),
            ayahCount = count,
            learnedAyahs = covered,
            state = when (s.statuses[number]) {
                SurahHifzStatus.LEARNED -> MapState.Learned
                SurahHifzStatus.LEARNING -> MapState.Learning
                null -> MapState.NotStarted
            },
        )
    }

    /** "Lesson done" / "2 of 5 left" from the cards of the lesson of the day. */
    fun today(lesson: LessonPlan?): TodayUi {
        lesson ?: return TodayUi.None
        val steps = LessonCards.steps(lesson)
        val total = steps.sumOf { it.total }
        val done = steps.sumOf { it.done }
        return when {
            total == 0 -> TodayUi.None
            done >= total -> TodayUi.Done
            else -> TodayUi.Left(left = total - done, total = total)
        }
    }

    /**
     * Retention over the days [fromDay]..[toDay]: the share of the repetitions (RECENT, OLD and CHECK records; NEW is the first
     * learning, not a repetition) that were not graded "Forgot". It never reads 100 while a repetition was forgotten, and
     * never 0 while one was kept; with no repetition there is no number.
     */
    fun retention(log: List<ReviewLogEntry>, fromDay: Int, toDay: Int): Retention {
        var total = 0
        var kept = 0
        for (entry in log) {
            // A "listened" record is never a repetition (task F1b): it neither counts nor changes the share.
            if (entry.kind == ReviewKind.NEW || !entry.kind.countsAsStudy || entry.epochDay !in fromDay..toDay) continue
            total++
            if (entry.grade != ReviewGrade.FORGOT) kept++
        }
        if (total == 0) return Retention(null, 0)
        val percent = if (kept == total) 100 else (kept * 100f / total).roundToInt().coerceIn(if (kept > 0) 1 else 0, 99)
        return Retention(percent, total)
    }

    /**
     * Per day of the [days] days ending [today] (oldest first, empty days included): the new ayahs (NEW records) and the
     * ayahs repeated (RECENT, OLD and CHECK records). An ayah counts once per kind and day, however many records touched it.
     */
    fun activity(log: List<ReviewLogEntry>, today: Int, days: Int = ActivityDays): List<ActivityDayUi> {
        val first = today - days + 1
        val learned = HashMap<Int, HashSet<Int>>()
        val reviewed = HashMap<Int, HashSet<Int>>()
        for (entry in log) {
            if (entry.epochDay !in first..today || !entry.kind.countsAsStudy) continue
            val bucket = if (entry.kind == ReviewKind.NEW) learned else reviewed
            val ayahs = bucket.getOrPut(entry.epochDay) { HashSet() }
            for (ayah in entry.startAyah..entry.endAyah) ayahs.add(entry.surah * AyahKeyStep + ayah)
        }
        return (first..today).map { day ->
            ActivityDayUi(day, learned = learned[day]?.size ?: 0, reviewed = reviewed[day]?.size ?: 0)
        }
    }

    /** How many of the days [fromDay]..[toDay] have at least one record in the log. */
    fun activeDays(log: List<ReviewLogEntry>, fromDay: Int, toDay: Int): Int =
        log.asSequence().filter { it.kind.countsAsStudy }.map { it.epochDay }.filter { it in fromDay..toDay }.distinct().count()

    /**
     * Per day of the [days] days ending [today] (oldest first): how many different passages were listened to ("Listen to the old").
     * Only `LISTEN` records count; a passage listened to twice in a day is one.
     */
    fun listened(log: List<ReviewLogEntry>, today: Int, days: Int = ActivityDays): Map<Int, Int> {
        val first = today - days + 1
        val seen = HashMap<Int, HashSet<String>>()
        for (entry in log) {
            if (entry.kind != ReviewKind.LISTEN || entry.epochDay !in first..today) continue
            seen.getOrPut(entry.epochDay) { HashSet() }.add(entry.passageId?.toString() ?: "${entry.surah}:${entry.startAyah}-${entry.endAyah}")
        }
        return seen.mapValues { it.value.size }
    }

    /** The days of the chart with their "listened" counts filled in; the bars (learned, reviewed) are untouched. */
    fun withListened(days: List<ActivityDayUi>, log: List<ReviewLogEntry>): List<ActivityDayUi> {
        if (days.isEmpty() || log.isEmpty()) return days
        val counts = listened(log, days.last().epochDay, days.size)
        return days.map { day -> counts[day.epochDay]?.let { day.copy(listened = it) } ?: day }
    }

    /** The 30 juz with the share of their ayahs that are learned (`JuzIndex.coveredCount` over `learnedAyahs()`). */
    fun juz(learned: Map<Int, Set<Int>>): List<JuzUi> = JuzIndex.all.map { range ->
        JuzUi(
            juz = range.juz,
            arabicName = JuzIndex.arabicName(range.juz),
            ayahCount = JuzIndex.ayahCount(range.juz),
            learnedAyahs = JuzIndex.coveredCount(range.juz, learned),
            startSurah = range.startSurah,
            startAyah = range.startAyah,
        )
    }

    /**
     * The plan line from the forecast: behind the line to the target date, ahead of it, on it, too slow for it, or just
     * the finish date when there is no target.
     */
    fun planLine(plan: HifzPlan?, forecast: PlanForecast?): PlanLineUi {
        plan ?: return PlanLineUi(PlanStatus.NoPlan)
        val scope = plan.scope
        forecast ?: return PlanLineUi(PlanStatus.Finish, scope)
        if (forecast.finished) return PlanLineUi(PlanStatus.Finished, scope)
        val finish = forecast.finishEpochDay
        val target = forecast.targetEpochDay
        return when {
            forecast.behindAyahs > 0 -> PlanLineUi(PlanStatus.Behind, scope, finish, forecast.behindAyahs)
            target != null && finish != null && finish < target -> PlanLineUi(PlanStatus.Ahead, scope, finish)
            target != null && finish != null && finish == target -> PlanLineUi(PlanStatus.OnTrack, scope, finish)
            target != null && finish != null ->
                PlanLineUi(PlanStatus.NeedPace, scope, finish, forecast.requiredPerDay ?: 0)

            else -> PlanLineUi(PlanStatus.Finish, scope, finish)
        }
    }

    /** The surahs with difficult ayahs, the most affected first. */
    fun hardSpots(s: ProgressSources): List<HardSpotUi> =
        s.hard
            .filterValues { it.isNotEmpty() }
            .map { (surah, ayahs) -> HardSpotUi(surah, s.nameOf(surah), ayahs.sorted()) }
            .sortedWith(compareByDescending<HardSpotUi> { it.ayahs.size }.thenBy { it.surah })

    /** A surah never has 1000 ayahs, so `surah * 1000 + ayah` names an ayah in one number. */
    private const val AyahKeyStep = 1000
}
