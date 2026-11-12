package tj.app.quran_todo.ui.onboarding

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.HifzPresets
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.NewPassagePlanner
import tj.app.quran_todo.hifz.schedule.TextMetrics
import tj.app.quran_todo.ui.plan.PaceMode
import tj.app.quran_todo.ui.plan.PlanDraft
import tj.app.quran_todo.ui.plan.PlanMath

/** What the person learns: one of the presets of the plan, or their own list of surahs. */
internal enum class PresetChoice(val scope: HifzScope, val analyticsName: String) {
    JuzAmma(HifzScope.JUZ_30, "juz_amma"),
    JuzTabarak(HifzScope.JUZ_29, "juz_tabarak"),
    FromStart(HifzScope.FROM_START, "from_start"),
    Custom(HifzScope.CUSTOM, "custom"),
}

/**
 * Everything the person has chosen on the plan steps; nothing of it is written until the last screen. All the numbers of the
 * steps (the first lesson, the pace, the forecast) are pure functions of it, so they are computed without touching the
 * repository: the covered ayahs of the repository are still empty at this point, the "known" surahs only live here.
 */
internal data class PlanSelection(
    /** Surahs the person knows by heart, ascending, without duplicates. */
    val known: List<Int> = emptyList(),
    val preset: PresetChoice? = PresetChoice.JuzAmma,
    /** The surahs of "Choose surahs", in the order they were chosen. */
    val customQueue: List<Int> = emptyList(),
    val mode: PaceMode = PaceMode.PerDay,
    val perDay: Int = HifzConstants.DAILY_NEW_DEFAULT,
    val targetDay: Int? = null,
) {
    val queue: List<Int> get() = OnboardingPlanMath.queueOf(preset, customQueue)
}

/** The plan as it is written by the last screen. */
internal data class PlanToSave(
    val scope: HifzScope,
    val surahOrder: List<Int>,
    val dailyNewAyahs: Int,
    val targetEpochDay: Int?,
    val manzilDays: Int,
)

/** What a preset card says. */
internal data class PresetInfo(
    val preset: PresetChoice,
    val surahCount: Int,
    val ayahCount: Int,
    /** The first new passage of this preset with the known surahs skipped; null when nothing is left to learn (or the list is empty). */
    val firstLesson: AyahRange?,
    /** The list is not empty and every surah of it is known. */
    val allKnown: Boolean,
) {
    /** The card can be chosen. */
    val selectable: Boolean get() = !allKnown
}

internal data class OnboardingForecast(
    val remainingAyahs: Int,
    val totalAyahs: Int,
    /** Ayahs a day the plan will have (the chosen amount, or what the date needs, at most 20). */
    val perDay: Int,
    /** The day the last ayah of the plan is learned; null when there is nothing left to learn. */
    val finishEpochDay: Int?,
    /** Ayahs a day the chosen date needs, only in the date mode. */
    val requiredPerDay: Int?,
    /** The date needs more than the 20 ayahs a day a plan allows. */
    val tooFast: Boolean,
)

/** A length of time as people say it: "3 weeks", "4 months". */
internal enum class SpanUnit { Day, Week, Month, Year }

internal data class PaceSpan(val unit: SpanUnit, val count: Int)

/** The pure math of the plan steps. The pace and the date use [PlanDraft] and [PlanMath], the first lesson [NewPassagePlanner]. */
internal object OnboardingPlanMath {
    /** The days of the forecast: [days] from today is "about N weeks/months". */
    fun paceSpan(days: Int): PaceSpan {
        val d = days.coerceAtLeast(1)
        return when {
            d < 14 -> PaceSpan(SpanUnit.Day, d)
            d < 60 -> PaceSpan(SpanUnit.Week, Math.round(d / 7f))
            d < 730 -> PaceSpan(SpanUnit.Month, Math.round(d / 30.44f).coerceAtLeast(2))
            else -> PaceSpan(SpanUnit.Year, Math.round(d / 365f))
        }
    }

    /** The bucket the `onboarding_completed` event carries for the number of known surahs. */
    fun knownBucket(count: Int): String = when {
        count <= 0 -> "0"
        count <= 3 -> "1-3"
        count <= 10 -> "4-10"
        else -> "11+"
    }

    fun queueOf(preset: PresetChoice?, customQueue: List<Int>): List<Int> = when (preset) {
        null -> emptyList()
        PresetChoice.Custom -> customQueue
        else -> HifzPresets.order(preset.scope)
    }

    /** The metrics the planner needs: only the number of ayahs of every surah matters, so every ayah weighs one. */
    private val StandardMetrics: TextMetrics by lazy {
        TextMetrics(
            (1..QuranFacts.SURAH_COUNT).flatMap { surah ->
                (1..QuranFacts.ayahCount(surah)).map { ayah -> AyahMetric(surah, ayah, page = 1, chars = 1) }
            },
        )
    }

    /** The known surahs as the passages `markKnown` would make of them (whole surahs, OLD): only their coverage is used. */
    private fun coverageOf(known: Collection<Int>): Coverage = Coverage(
        passages = known.distinct().filter { it in 1..QuranFacts.SURAH_COUNT }.map { surah ->
            Passage(
                id = 0,
                surah = surah,
                startAyah = 1,
                endAyah = QuranFacts.ayahCount(surah),
                learnedEpochDay = 0,
                stage = PassageStage.OLD,
                nextDueEpochDay = null,
                lastReviewedEpochDay = null,
                lapses = 0,
            )
        },
        metrics = StandardMetrics,
    )

    /** The covered ayahs per surah that [known] makes (the shape [PlanMath] takes). */
    fun coveredOf(known: Collection<Int>): Map<Int, Int> =
        known.distinct().filter { it in 1..QuranFacts.SURAH_COUNT }.associateWith { QuranFacts.ayahCount(it) }

    /**
     * The first new passage of [queue] at [perDay] ayahs a day when [known] surahs are covered: the scheduler's own rule
     * (`NewPassagePlanner`), so it never lands in a known surah. Null when everything is known or the queue is empty.
     */
    fun firstLesson(queue: List<Int>, known: Collection<Int>, perDay: Int): AyahRange? {
        if (queue.isEmpty()) return null
        val plan = HifzPlan(HifzScope.CUSTOM, queue, perDay.coerceIn(HifzConstants.DAILY_NEW_MIN, HifzConstants.DAILY_NEW_MAX), null, 0, HifzConstants.MANZIL_DAYS_DEFAULT)
        return NewPassagePlanner.next(plan, coverageOf(known), StandardMetrics)
    }

    /** The first lesson of the selection at the pace it will be saved with (the chosen amount, or what the date needs). */
    fun firstLesson(selection: PlanSelection, today: Int): AyahRange? =
        firstLesson(selection.queue, selection.known, effectivePerDay(selection, today))

    /** The info for each preset card with the amount a day the person has chosen so far. [customQueue] fills the "Choose surahs" card. */
    fun presetInfo(preset: PresetChoice, customQueue: List<Int>, known: Collection<Int>, perDay: Int): PresetInfo {
        val queue = queueOf(preset, customQueue)
        return PresetInfo(
            preset = preset,
            surahCount = queue.distinct().size,
            ayahCount = queue.distinct().sumOf { QuranFacts.ayahCount(it) },
            firstLesson = firstLesson(queue, known, perDay),
            allKnown = queue.isNotEmpty() && queue.all { it in known },
        )
    }

    private fun draftOf(selection: PlanSelection): PlanDraft = PlanDraft(
        scope = HifzScope.CUSTOM,
        customQueue = selection.queue,
        mode = selection.mode,
        perDay = selection.perDay,
        targetDay = selection.targetDay,
        manzilDays = HifzConstants.MANZIL_DAYS_DEFAULT,
    )

    /** The amount a day the plan is saved with: the chosen one, or what the date needs (1..20). */
    fun effectivePerDay(selection: PlanSelection, today: Int): Int =
        PlanMath.effectivePerDay(draftOf(selection), coveredOf(selection.known), today)

    /** A date to offer when the person turns on "I want it by a date": about when the current pace finishes, at least a week away. */
    fun suggestedTarget(selection: PlanSelection, today: Int): Int =
        PlanMath.suggestedTarget(PlanMath.remaining(selection.queue, coveredOf(selection.known)), selection.perDay, today)

    /** The pace, the finish day and the warnings, by the same functions the plan screen uses; the forecast is `Forecast.compute` over the known surahs. */
    fun forecast(selection: PlanSelection, today: Int): OnboardingForecast {
        val draft = draftOf(selection)
        val covered = coveredOf(selection.known)
        val perDay = PlanMath.effectivePerDay(draft, covered, today)
        val plan = draft.toPlan(startedEpochDay = today, perDayToSave = perDay)
        val computed = Forecast.compute(plan, coverageOf(selection.known), newLogSinceStart = emptyList(), today = today)
        val byDate = selection.mode == PaceMode.ByDate && selection.targetDay != null
        return OnboardingForecast(
            remainingAyahs = computed.remainingAyahs,
            totalAyahs = computed.totalAyahs,
            perDay = perDay,
            finishEpochDay = computed.finishEpochDay,
            requiredPerDay = if (byDate) computed.requiredPerDay else null,
            tooFast = PlanMath.isTooFast(draft, covered, today),
        )
    }

    /** The plan the last screen writes; null when there is nothing to learn (an empty or fully known queue). */
    fun planToSave(selection: PlanSelection, today: Int): PlanToSave? {
        val queue = selection.queue
        val preset = selection.preset ?: return null
        if (queue.isEmpty()) return null
        val draft = draftOf(selection)
        return PlanToSave(
            scope = preset.scope,
            surahOrder = queue,
            dailyNewAyahs = PlanMath.effectivePerDay(draft, coveredOf(selection.known), today),
            targetEpochDay = draft.savedTarget,
            manzilDays = HifzConstants.MANZIL_DAYS_DEFAULT,
        )
    }

    /** The queue is learnable: not empty and something in it is not known yet. */
    fun canLearn(selection: PlanSelection): Boolean {
        val queue = selection.queue
        return queue.isNotEmpty() && queue.any { it !in selection.known }
    }

    /**
     * The "known" step marked every surah of a chosen, non-empty list: nothing is left to learn, so the step says so and does not
     * go on. False for a missing preset or an empty list (that is the preset step's business).
     */
    fun nothingToLearn(selection: PlanSelection): Boolean =
        selection.preset != null && selection.queue.isNotEmpty() && !canLearn(selection)
}
