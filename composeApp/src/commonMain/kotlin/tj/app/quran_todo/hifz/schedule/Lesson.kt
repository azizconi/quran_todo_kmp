package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry

/** One card of the "Recent" or "Old" block. */
data class LessonItem(
    val passageId: Long,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val stage: PassageStage,
    /** Ayahs of the passage the person marked as difficult. */
    val hardAyahs: Set<Int>,
    /** Repeated today (by the review log). */
    val done: Boolean,
    /** The grade of today's repetition, null while not done. */
    val grade: ReviewGrade?,
    val estimateSeconds: Int,
    /** For the ladder: how many days after the due day it is; 0 when it is due today (or for OLD). */
    val overdueDays: Int,
    /** Seconds of one calm recitation of the passage (the audio of "Listen to the old", task F1b); display only. */
    val listenSeconds: Int = 0,
)

enum class LessonNewState {
    /** To learn today. */
    PENDING,

    /** Learned today (there is a NEW record in the log). */
    DONE,

    /** Does not fit into the lesson today; it is the first thing tomorrow. */
    DEFERRED,
}

data class LessonNew(
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val state: LessonNewState,
    /** The grade given when it was learned; null while not done. */
    val grade: ReviewGrade?,
    /** The stored passage once it is learned. */
    val passageId: Long?,
    val estimateSeconds: Int,
)

/** What waits tomorrow. */
data class LessonTomorrow(
    /** The next new passage by the plan (the same as today's if today's is not learned), null if there is none. */
    val newPassage: AyahRange?,
    /**
     * Repetitions waiting tomorrow: the passages of the ladder that are due tomorrow or are overdue now, and tomorrow's turn of the
     * manzil circle as it stands now (what was repeated today is not eligible again until its `manzilDays` have passed).
     */
    val dueCount: Int,
)

/**
 * The lesson of a day. The blocks are "New" ([newBlock]), "Recent" ([recent]) and "Old" ([old]).
 *
 * - [recent]: the repetitions done today first (in the order they were done), then the ones to do, the most overdue first.
 * - [old]: the whole portion of the manzil circle, in the order of the mushaf, done or not. The budget never cuts it.
 * - [newBlock]: null when there is nothing to learn (no plan, or the plan is finished and nothing was learned today).
 * - Time is the estimate (see [HifzConstants]); [totalMinutes] counts what is in the lesson (done and to do),
 *   [remainingMinutes] what is still to do. Both are rounded up. [totalMinutes] may be past
 *   [HifzConstants.LESSON_BUDGET_MINUTES]: the budget caps the Recent block only.
 * - What did not fit into [HifzConstants.LESSON_BUDGET_MINUTES] of the **Recent** block is moved to the next days;
 *   [deferredCount] counts those repetitions and the new block when it was deferred behind them, [catchingUp] is true when
 *   there are any. The Old block never counts here. The new block is deferred only behind a cut repetition; without a
 *   backlog it is in the lesson even if the total goes past the budget.
 * - [isComplete]: there is something in the lesson and all of it is done (deferred items do not count).
 * - [isEmpty]: nothing at all in the lesson (nothing due, no portion, nothing new).
 * - [paused]: the plan is on pause today (task F2b): the lesson is empty (`isEmpty`, nothing new, recent or old) and this is the
 *   pause; [tomorrow] then describes the first day with a lesson again. Null on every other day.
 */
data class LessonPlan(
    val today: Int,
    val hasPlan: Boolean,
    /** The plan exists and every surah of it is covered. */
    val planFinished: Boolean,
    val newBlock: LessonNew?,
    val recent: List<LessonItem>,
    val old: List<LessonItem>,
    val totalMinutes: Int,
    val remainingMinutes: Int,
    val catchingUp: Boolean,
    val deferredCount: Int,
    val isComplete: Boolean,
    val isEmpty: Boolean,
    val tomorrow: LessonTomorrow,
    val paused: PlanPause? = null,
)

class LessonInput(
    val today: Int,
    val plan: HifzPlan?,
    val passages: List<Passage>,
    /** The review log of [today] only. */
    val todayLog: List<ReviewLogEntry>,
    val metrics: TextMetrics,
    /** The pause of the plan that [today] is inside of, if any (`PlanPauses.covering`): the lesson of such a day is empty. */
    val pause: PlanPause? = null,
    /** The last day of "a lighter week" (task U17, [LighterWeek]): until then the new passage is planned at half the pace. */
    val lighterUntil: Int? = null,
)

/**
 * Builds the lesson from the state: pure, the same input gives the same lesson.
 *
 * The budget ([HifzConstants.LESSON_BUDGET_MINUTES]) caps the **recent ladder** only (R1...R30): the most overdue
 * repetition first, what does not fit is moved to the next days together with everything after it. What is done today
 * is always in the lesson and takes its time from the budget first, so finishing a card never pulls a deferred one in and
 * never throws one out. The first repetition always fits. The budget does not depend on the Old block or on the new
 * passage, so the Recent block is the same before and after they are done.
 *
 * The **manzil portion** ([Manzil.portion]) goes into the lesson whole, done and not done: it is the promise that nothing
 * learned is forgotten, and the length of the circle (`manzilDays`) is what keeps it small, not the budget.
 *
 * The new passage is moved to the next day **only when a recent repetition was cut** (a real backlog: the person has to catch
 * up first, whole passage deferred). Otherwise it is in the lesson, even if the total goes past the budget: the person chose
 * that pace, and an old circle that is long must not stop the learning for good.
 */
object LessonBuilder {
    fun build(input: LessonInput): LessonPlan {
        val metrics = input.metrics
        val today = input.today
        input.pause?.takeIf { today in it }?.let { return paused(input, it) }
        // "Listen to the old" (LISTEN) is a help, not a repetition: it must not change anything of the lesson.
        val todayLog = input.todayLog.filter { it.kind.countsAsStudy }
        val byId = input.passages.associateBy { it.id }
        val coverage = Coverage(input.passages, metrics)
        val plan = input.plan

        // --- Repetitions done today (the last record of a passage wins) ---
        val lastReviewEntry = LinkedHashMap<Long, ReviewLogEntry>()
        for (entry in todayLog) {
            if ((entry.kind == ReviewKind.RECENT || entry.kind == ReviewKind.OLD) && entry.passageId != null) {
                lastReviewEntry.remove(entry.passageId)
                lastReviewEntry[entry.passageId] = entry
            }
        }

        // --- Old: the manzil portion ---
        // Without a plan nobody chose the circle length: it is suggested from the weight of the old passages, as onboarding does.
        val manzilDays = plan?.manzilDays
            ?: Manzil.suggestedDays(input.passages.filter { it.stage == PassageStage.OLD }.sumOf { metrics.weight(it) })
        val portion = Manzil.portion(input.passages, todayLog, manzilDays, metrics, today)
        val portionIds = portion.items.map { it.passage.id }.toSet()

        // --- Recent: done today + due ---
        val recentDone = lastReviewEntry.values
            .filter { it.kind == ReviewKind.RECENT && it.passageId !in portionIds }
            .mapNotNull { entry -> byId[entry.passageId]?.let { it to entry.grade } }
        val doneIds = lastReviewEntry.keys
        val recentPending = input.passages
            .filter { p -> p.stage != PassageStage.OLD && p.id !in doneIds && (p.nextDueEpochDay ?: Int.MAX_VALUE) <= today }
            .sortedWith(compareBy({ it.nextDueEpochDay }, { it.surah }, { it.startAyah }))

        // --- New ---
        val newToday = todayLog.lastOrNull { it.kind == ReviewKind.NEW }
        // A lighter week (task U17) halves the daily amount here, the one place the lesson reads it; the stored plan keeps its pace.
        val next = plan?.let { NewPassagePlanner.next(LighterWeek.applied(it, input.lighterUntil, today), coverage, metrics) }
        val planFinished = plan != null && HifzStatuses.currentSurah(plan, coverage) == null

        // --- Budget: caps the Recent block only; Old goes in whole, New is deferred only behind a cut repetition ---
        fun reviewEstimate(p: Passage) = HifzConstants.reviewSeconds(metrics.weight(p))

        // Everything in the lesson (for the total) and what is still to do (for the remaining time).
        var running = recentDone.sumOf { (p, _) -> reviewEstimate(p) } +
            portion.items.sumOf { reviewEstimate(it.passage) }
        var remainingSeconds = portion.items.filter { !it.done }.sumOf { reviewEstimate(it.passage) }

        var newBlock: LessonNew? = null
        if (newToday != null) {
            val estimate = HifzConstants.newSeconds(metrics.chars(newToday.surah, newToday.startAyah, newToday.endAyah))
            running += estimate
            newBlock = LessonNew(
                surah = newToday.surah,
                startAyah = newToday.startAyah,
                endAyah = newToday.endAyah,
                state = LessonNewState.DONE,
                grade = newToday.grade,
                passageId = newToday.passageId,
                estimateSeconds = estimate,
            )
        }

        // The ladder: the time of its own cards (done ones first) against the budget, nothing else counts here.
        var recentSeconds = recentDone.sumOf { (p, _) -> reviewEstimate(p) }
        var recentIncluded = recentDone.size
        var deferred = 0
        var cutoff = false
        val recentAccepted = recentPending.filter { passage ->
            val estimate = reviewEstimate(passage)
            if (!cutoff && (recentIncluded == 0 || recentSeconds + estimate <= HifzConstants.LESSON_BUDGET_SECONDS)) {
                recentSeconds += estimate
                running += estimate
                remainingSeconds += estimate
                recentIncluded++
                true
            } else {
                cutoff = true
                deferred++
                false
            }
        }

        if (newToday == null && next != null) {
            val estimate = HifzConstants.newSeconds(metrics.chars(next.surah, next.startAyah, next.endAyah))
            // Deferred only behind a cut repetition (a backlog); otherwise it is in, whatever it does to the total.
            val fits = !cutoff
            if (fits) {
                running += estimate
                remainingSeconds += estimate
            } else {
                deferred++
            }
            newBlock = LessonNew(
                surah = next.surah,
                startAyah = next.startAyah,
                endAyah = next.endAyah,
                state = if (fits) LessonNewState.PENDING else LessonNewState.DEFERRED,
                grade = null,
                passageId = null,
                estimateSeconds = estimate,
            )
        }

        // --- Items ---
        fun item(p: Passage, done: Boolean, grade: ReviewGrade?) = LessonItem(
            passageId = p.id,
            surah = p.surah,
            startAyah = p.startAyah,
            endAyah = p.endAyah,
            stage = p.stage,
            hardAyahs = p.hard.keys.sorted().toSet(),
            done = done,
            grade = grade,
            estimateSeconds = reviewEstimate(p),
            overdueDays = if (done) 0 else maxOf(0, today - (p.nextDueEpochDay ?: today)),
            listenSeconds = HifzConstants.listenSeconds(metrics.weight(p)),
        )

        val recentItems = recentDone.map { (p, grade) -> item(p, done = true, grade = grade) } +
            recentAccepted.map { item(it, done = false, grade = null) }
        val oldItems = portion.items.map { item(it.passage, it.done, it.grade) }

        val pendingNew = newBlock?.state == LessonNewState.PENDING
        val isEmpty = recentItems.isEmpty() && oldItems.isEmpty() &&
            (newBlock == null || newBlock.state == LessonNewState.DEFERRED)
        val isComplete = !isEmpty && recentItems.all { it.done } && oldItems.all { it.done } && !pendingNew

        val tomorrowLadder = input.passages.count { it.stage != PassageStage.OLD && (it.nextDueEpochDay ?: Int.MAX_VALUE) <= today + 1 }
        val tomorrowOld = Manzil.portion(input.passages, emptyList(), manzilDays, metrics, today + 1).items.size
        val tomorrowDue = tomorrowLadder + tomorrowOld

        return LessonPlan(
            today = today,
            hasPlan = plan != null,
            planFinished = planFinished,
            newBlock = newBlock,
            recent = recentItems,
            old = oldItems,
            totalMinutes = HifzConstants.minutes(running),
            remainingMinutes = HifzConstants.minutes(remainingSeconds),
            catchingUp = deferred > 0,
            deferredCount = deferred,
            isComplete = isComplete,
            isEmpty = isEmpty,
            tomorrow = LessonTomorrow(newPassage = next, dueCount = tomorrowDue),
        )
    }

    /**
     * The lesson of a day inside a pause (task F2b): nothing new, recent or old, no minutes, `isEmpty`. The plan itself is read as on
     * any day (whether it exists, whether it is finished), and "tomorrow" is what waits on the first day after the pause (the
     * calendar of the plan has been moved by the pause, so these are the passages that were waiting).
     */
    private fun paused(input: LessonInput, pause: PlanPause): LessonPlan {
        val metrics = input.metrics
        val plan = input.plan
        val coverage = Coverage(input.passages, metrics)
        val manzilDays = plan?.manzilDays
            ?: Manzil.suggestedDays(input.passages.filter { it.stage == PassageStage.OLD }.sumOf { metrics.weight(it) })
        val back = pause.resumeEpochDay
        val dueLadder = input.passages.count { it.stage != PassageStage.OLD && (it.nextDueEpochDay ?: Int.MAX_VALUE) <= back }
        val dueOld = Manzil.portion(input.passages, emptyList(), manzilDays, metrics, back).items.size
        return LessonPlan(
            today = input.today,
            hasPlan = plan != null,
            planFinished = plan != null && HifzStatuses.currentSurah(plan, coverage) == null,
            newBlock = null,
            recent = emptyList(),
            old = emptyList(),
            totalMinutes = 0,
            remainingMinutes = 0,
            catchingUp = false,
            deferredCount = 0,
            isComplete = false,
            isEmpty = true,
            tomorrow = LessonTomorrow(
                newPassage = plan?.let { NewPassagePlanner.next(it, coverage, metrics) },
                dueCount = dueLadder + dueOld,
            ),
            paused = pause,
        )
    }
}
