package tj.app.quran_todo.ui.plan

import tj.app.quran_todo.hifz.HifzPresets
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.NewPassagePlanner
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.hifz.schedule.TextMetrics
import tj.app.quran_todo.ui.home.QuranMeta
import tj.app.quran_todo.ui.home.SurahRowUi

/** How the pace of the plan is given: ayahs a day, or the day the person wants to be done by. */
internal enum class PaceMode { PerDay, ByDate }

/**
 * What the plan screen edits: a copy of the plan that is saved only by "Save". The queue of a preset
 * scope is the preset itself ([HifzPresets]); the queue of [HifzScope.CUSTOM] is [customQueue], which is
 * remembered while the person looks at a preset, so switching back does not lose it.
 *
 * All the editing is pure functions on this value, so the screen model is testable without Compose.
 */
internal data class PlanDraft(
    val scope: HifzScope,
    val customQueue: List<Int>,
    val mode: PaceMode,
    /** Ayahs a day when [mode] is [PaceMode.PerDay]; also kept while the person looks at a date. */
    val perDay: Int,
    /** The day to finish by when [mode] is [PaceMode.ByDate]; null until one is chosen. */
    val targetDay: Int?,
    val manzilDays: Int,
) {
    /** The surahs in the order they are learned. */
    val queue: List<Int> get() = if (scope == HifzScope.CUSTOM) customQueue else HifzPresets.order(scope)

    val isCustom: Boolean get() = scope == HifzScope.CUSTOM

    /** Picks a preset, or "my own surahs": the first time that list starts as a copy of what is shown now. */
    fun withScope(next: HifzScope): PlanDraft = when {
        next == scope -> this
        next == HifzScope.CUSTOM -> copy(scope = next, customQueue = customQueue.ifEmpty { queue })
        else -> copy(scope = next)
    }

    /** "Change the order": a preset becomes the person's own list with the same surahs. */
    fun editOrder(): PlanDraft = if (isCustom) this else copy(scope = HifzScope.CUSTOM, customQueue = queue)

    /** Moves the surah at [from] to the place [to] of the queue (both are positions in the queue). */
    fun move(from: Int, to: Int): PlanDraft {
        val list = queue
        if (from !in list.indices || to !in list.indices || from == to) return this
        val moved = list.toMutableList().apply { add(to, removeAt(from)) }
        return copy(scope = HifzScope.CUSTOM, customQueue = moved)
    }

    fun remove(surah: Int): PlanDraft {
        if (surah !in queue) return this
        return copy(scope = HifzScope.CUSTOM, customQueue = queue - surah)
    }

    /** Appends [surah] to the end of the own list; nothing happens when it is in the queue already. */
    fun add(surah: Int): PlanDraft {
        if (surah !in 1..QuranMeta.SurahCount || surah in queue) return this
        return copy(scope = HifzScope.CUSTOM, customQueue = queue + surah)
    }

    fun withPerDay(value: Int): PlanDraft = copy(perDay = value.coerceIn(HifzConstants.DAILY_NEW_MIN, HifzConstants.DAILY_NEW_MAX))

    /** Switches between "ayahs a day" and "finish by a date"; a date is suggested ([suggestedTarget]) if none was chosen yet. */
    fun withMode(next: PaceMode, suggestedTarget: Int): PlanDraft = when (next) {
        PaceMode.PerDay -> copy(mode = next)
        PaceMode.ByDate -> copy(mode = next, targetDay = targetDay ?: suggestedTarget)
    }

    fun withTarget(day: Int): PlanDraft = copy(mode = PaceMode.ByDate, targetDay = day)

    fun withManzilDays(days: Int): PlanDraft =
        copy(manzilDays = days.coerceIn(HifzConstants.MANZIL_DAYS_MIN, HifzConstants.MANZIL_DAYS_MAX))

    /** The target the plan is saved with: only in the date mode. */
    val savedTarget: Int? get() = if (mode == PaceMode.ByDate) targetDay else null

    /** The plan as it would be saved. [startedEpochDay] and the pace come from the caller (see [PlanMath.startDay], [PlanMath.effectivePerDay]). */
    fun toPlan(startedEpochDay: Int, perDayToSave: Int): HifzPlan = HifzPlan(
        scope = scope,
        surahOrder = queue,
        dailyNewAyahs = perDayToSave.coerceIn(HifzConstants.DAILY_NEW_MIN, HifzConstants.DAILY_NEW_MAX),
        targetEpochDay = savedTarget,
        startedEpochDay = startedEpochDay,
        manzilDays = manzilDays,
    )

    /**
     * Differs from [saved] in something the person chose. In the date mode the daily amount follows the
     * covered ayahs on its own, so it is not compared; nothing is saved yet when [saved] is null.
     */
    fun differsFrom(saved: HifzPlan?): Boolean {
        if (saved == null) return true
        if (queue != saved.surahOrder || scope != saved.scope || manzilDays != saved.manzilDays) return true
        return when (mode) {
            PaceMode.PerDay -> saved.targetEpochDay != null || perDay != saved.dailyNewAyahs
            PaceMode.ByDate -> saved.targetEpochDay != targetDay
        }
    }

    companion object {
        /** A first plan: the last juz (the shortest surahs first), five ayahs a day. */
        fun initial(): PlanDraft = PlanDraft(
            scope = HifzScope.JUZ_30,
            customQueue = emptyList(),
            mode = PaceMode.PerDay,
            perDay = HifzConstants.DAILY_NEW_DEFAULT,
            targetDay = null,
            manzilDays = HifzConstants.MANZIL_DAYS_DEFAULT,
        )

        /** The draft that shows [plan] (a preset only if the saved queue is exactly that preset). */
        fun of(plan: HifzPlan?): PlanDraft {
            plan ?: return initial()
            val isPreset = plan.scope != HifzScope.CUSTOM && plan.surahOrder == HifzPresets.order(plan.scope)
            return PlanDraft(
                scope = if (isPreset) plan.scope else HifzScope.CUSTOM,
                customQueue = if (isPreset) emptyList() else plan.surahOrder,
                mode = if (plan.targetEpochDay != null) PaceMode.ByDate else PaceMode.PerDay,
                perDay = plan.dailyNewAyahs,
                targetDay = plan.targetEpochDay,
                manzilDays = plan.manzilDays,
            )
        }
    }
}

/** The numbers of the plan screen that follow from the covered ayahs. Pure. */
internal object PlanMath {
    /** The first surah of [queue] that still has ayahs to learn, like `HifzStatuses.currentSurah`. */
    fun currentSurah(queue: List<Int>, covered: Map<Int, Int>): Int? =
        queue.firstOrNull { (covered[it] ?: 0) < QuranMeta.ayahCount(it) }

    /** Ayahs of the surahs of [queue] that are not covered yet (each surah counts once, like `Coverage.remaining`). */
    fun remaining(queue: List<Int>, covered: Map<Int, Int>): Int =
        queue.distinct().sumOf { (QuranMeta.ayahCount(it) - (covered[it] ?: 0)).coerceAtLeast(0) }

    fun total(queue: List<Int>): Int = queue.distinct().sumOf { QuranMeta.ayahCount(it) }

    /** Ayahs a day needed to learn [remaining] by [targetDay] (at least one day, like `Forecast.compute`). */
    fun requiredPerDay(remaining: Int, today: Int, targetDay: Int): Int {
        val daysLeft = (targetDay - today).coerceAtLeast(1)
        return (remaining + daysLeft - 1) / daysLeft
    }

    /** The amount a day the plan is saved with: the typed one, or the one the date needs (at most 20). */
    fun effectivePerDay(draft: PlanDraft, covered: Map<Int, Int>, today: Int): Int = when (draft.mode) {
        PaceMode.PerDay -> draft.perDay
        PaceMode.ByDate -> {
            val target = draft.targetDay
            if (target == null) {
                draft.perDay
            } else {
                requiredPerDay(remaining(draft.queue, covered), today, target)
                    .coerceIn(HifzConstants.DAILY_NEW_MIN, HifzConstants.DAILY_NEW_MAX)
            }
        }
    }

    /** The date needs more ayahs a day than the plan allows. */
    fun isTooFast(draft: PlanDraft, covered: Map<Int, Int>, today: Int): Boolean {
        val target = draft.targetDay ?: return false
        if (draft.mode != PaceMode.ByDate) return false
        return requiredPerDay(remaining(draft.queue, covered), today, target) > HifzConstants.DAILY_NEW_MAX
    }

    /** The day counting starts from, like `setPlan`: kept when only the pace changes, today when the queue changes. */
    fun startDay(draft: PlanDraft, saved: HifzPlan?, today: Int): Int =
        if (saved != null && saved.surahOrder == draft.queue) saved.startedEpochDay else today

    /** A date to offer when the person switches to "finish by": about when the current pace would finish, at least a week away. */
    fun suggestedTarget(remaining: Int, perDay: Int, today: Int): Int {
        val days = if (remaining <= 0) 60 else (remaining + perDay - 1) / perDay.coerceAtLeast(1)
        return today + days.coerceAtLeast(7)
    }

    /** Pages of the mushaf to go over each day to repeat all the OLD passages in [days] days; null when nothing is OLD. */
    fun oldPagesPerDay(oldWeight: Int, days: Int): Float? =
        if (oldWeight <= 0) null else oldWeight.toFloat() / HifzConstants.CHARS_PER_PAGE / days.coerceAtLeast(1)

    /**
     * The new passage [daily] ayahs would make of [surah], by the rule of the scheduler (`NewPassagePlanner`); [passages] are
     * the stored passages of that surah. Null when the whole surah is covered.
     */
    fun nextPassage(surah: Int, ayahCount: Int, passages: List<Passage>, daily: Int): AyahRange? {
        if (ayahCount <= 0) return null
        // The planner only reads the number of ayahs of the surah from the metrics.
        val metrics = TextMetrics((1..ayahCount).map { AyahMetric(surah, it, 1, 1) })
        val plan = HifzPlan(HifzScope.CUSTOM, listOf(surah), daily, null, 0, HifzConstants.MANZIL_DAYS_DEFAULT)
        return NewPassagePlanner.next(plan, Coverage(passages, metrics), metrics)
    }
}

/** What the forecast card says; the screen turns it into words. */
internal sealed interface ForecastHeadline {
    /** There are no surahs in the queue. */
    data object Empty : ForecastHeadline

    /** Every surah of the queue is covered. */
    data object Finished : ForecastHeadline

    data class FinishOn(val epochDay: Int) : ForecastHeadline
}

internal data class ForecastSummary(
    val headline: ForecastHeadline,
    val remainingAyahs: Int,
    val totalAyahs: Int,
    /** Ayahs a day the chosen date needs; null in the "ayahs a day" mode. */
    val requiredPerDay: Int?,
    /** The date needs more than the 20 ayahs a day a plan can have. */
    val tooFast: Boolean,
    /** Ayahs behind the straight line to the date; 0 when on time. */
    val behindAyahs: Int,
) {
    companion object {
        fun of(draft: PlanDraft, forecast: PlanForecast?, covered: Map<Int, Int>, today: Int): ForecastSummary? {
            val queue = draft.queue
            if (queue.isEmpty()) return ForecastSummary(ForecastHeadline.Empty, 0, 0, null, false, 0)
            forecast ?: return null
            val byDate = draft.mode == PaceMode.ByDate && draft.targetDay != null
            val headline = when {
                forecast.finished -> ForecastHeadline.Finished
                forecast.finishEpochDay != null -> ForecastHeadline.FinishOn(forecast.finishEpochDay)
                else -> ForecastHeadline.Finished
            }
            return ForecastSummary(
                headline = headline,
                remainingAyahs = forecast.remainingAyahs,
                totalAyahs = forecast.totalAyahs,
                requiredPerDay = if (byDate) forecast.requiredPerDay else null,
                tooFast = PlanMath.isTooFast(draft, covered, today),
                behindAyahs = if (byDate) forecast.behindAyahs else 0,
            )
        }
    }
}

/** A line of the queue list. */
internal data class QueueRowUi(
    val surah: Int,
    val name: String,
    val arabicName: String,
    val ayahCount: Int,
    /** Fully memorized: shown gray with a checkmark. */
    val learned: Boolean,
    /** The surah the plan is learning now. */
    val current: Boolean,
)

/** The time one day of the new passage takes: the passage of the current surah and the estimate of the lesson. */
internal data class PaceEstimate(val surah: Int, val startAyah: Int, val endAyah: Int, val minutes: Int) {
    /** More than a whole lesson is meant to take. */
    val overBudget: Boolean get() = minutes > HifzConstants.LESSON_BUDGET_MINUTES
}

/** Everything the plan screen draws. */
internal data class PlanUi(
    /** The saved plan has been read; until then the screen shows a quiet placeholder. */
    val loaded: Boolean = false,
    val draft: PlanDraft = PlanDraft.initial(),
    val today: Int = 0,
    val queue: List<QueueRowUi> = emptyList(),
    /** All 114 surahs, for the search of "add a surah". */
    val catalog: List<SurahRowUi> = emptyList(),
    val hasSavedPlan: Boolean = false,
    /** The draft differs from what is saved (always true while there is no plan). */
    val dirty: Boolean = false,
    /** The person changed something: leaving the screen asks whether to save. */
    val edited: Boolean = false,
    val canSave: Boolean = false,
    val summary: ForecastSummary? = null,
    /** Ayahs a day the plan will be saved with (the typed amount, or what the date needs). */
    val perDay: Int = HifzConstants.DAILY_NEW_DEFAULT,
    val estimate: PaceEstimate? = null,
    /** Pages of the mushaf per day of the manzil circle; null when nothing is memorized. */
    val oldPagesPerDay: Float? = null,
    /** A date to offer when the person switches to "finish by". */
    val suggestedTarget: Int = 0,
)

internal object PlanUiBuilder {
    fun build(
        draft: PlanDraft,
        saved: HifzPlan?,
        covered: Map<Int, Int>,
        statuses: Map<Int, SurahHifzStatus>,
        catalog: List<SurahRowUi>,
        forecast: PlanForecast?,
        estimate: PaceEstimate?,
        oldWeight: Int,
        today: Int,
    ): PlanUi {
        val byNumber = catalog.associateBy { it.number }
        val queue = draft.queue
        val current = PlanMath.currentSurah(queue, covered)
        val rows = queue.map { surah ->
            val row = byNumber[surah]
            QueueRowUi(
                surah = surah,
                name = row?.name ?: "Surah $surah",
                arabicName = row?.arabicName.orEmpty(),
                ayahCount = row?.ayahCount ?: QuranMeta.ayahCount(surah),
                learned = statuses[surah] == SurahHifzStatus.LEARNED,
                current = surah == current,
            )
        }
        return PlanUi(
            loaded = true,
            draft = draft,
            today = today,
            queue = rows,
            catalog = catalog,
            hasSavedPlan = saved != null,
            dirty = draft.differsFrom(saved),
            edited = if (saved == null) draft != PlanDraft.initial() else draft.differsFrom(saved),
            canSave = queue.isNotEmpty() && (draft.mode == PaceMode.PerDay || draft.targetDay != null),
            summary = ForecastSummary.of(draft, forecast, covered, today),
            perDay = PlanMath.effectivePerDay(draft, covered, today),
            estimate = estimate.takeIf { queue.isNotEmpty() },
            oldPagesPerDay = PlanMath.oldPagesPerDay(oldWeight, draft.manzilDays),
            suggestedTarget = PlanMath.suggestedTarget(PlanMath.remaining(queue, covered), draft.perDay, today),
        )
    }
}
