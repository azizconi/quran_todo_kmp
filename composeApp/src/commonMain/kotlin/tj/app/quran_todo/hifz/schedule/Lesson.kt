package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
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
 * - [old]: the portion of the manzil circle, in the order of the mushaf, done or not.
 * - [newBlock]: null when there is nothing to learn (no plan, or the plan is finished and nothing was learned today).
 * - Time is the estimate (see [HifzConstants]); [totalMinutes] counts what is in the lesson (done and to do),
 *   [remainingMinutes] what is still to do. Both are rounded up.
 * - What did not fit into [HifzConstants.LESSON_BUDGET_MINUTES] is moved to the next days: [deferredCount] items
 *   (passages and the new block), [catchingUp] is true when there are any. The new block is deferred only behind a cut
 *   repetition; without a backlog it is in the lesson even if the total goes past the budget.
 * - [isComplete]: there is something in the lesson and all of it is done (deferred items do not count).
 * - [isEmpty]: nothing at all in the lesson (nothing due, no portion, nothing new).
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
)

class LessonInput(
    val today: Int,
    val plan: HifzPlan?,
    val passages: List<Passage>,
    /** The review log of [today] only. */
    val todayLog: List<ReviewLogEntry>,
    val metrics: TextMetrics,
)

/**
 * Builds the lesson from the state: pure, the same input gives the same lesson.
 *
 * The budget ([HifzConstants.LESSON_BUDGET_MINUTES]) caps the **repetitions**: Recent (the most overdue first) -> Old (the
 * manzil portion, the oldest first). A repetition that does not fit is moved to the next days, and so is everything after
 * it. What is done today is always in the lesson and takes its time from the budget first, so finishing a card never pulls
 * a deferred one in and never throws one out. The first repetition always fits: a lesson is not empty because of the budget.
 *
 * The new passage is moved to the next day **only when repetitions were cut** (a real backlog: the person has to catch up
 * first, whole passage deferred). When no repetition was cut it is in the lesson even if it takes the lesson past the
 * budget: the person chose that pace (a high `dailyNewAyahs` must not stop the learning for good).
 */
object LessonBuilder {
    fun build(input: LessonInput): LessonPlan {
        val metrics = input.metrics
        val today = input.today
        val byId = input.passages.associateBy { it.id }
        val coverage = Coverage(input.passages, metrics)
        val plan = input.plan

        // --- Repetitions done today (the last record of a passage wins) ---
        val lastReviewEntry = LinkedHashMap<Long, ReviewLogEntry>()
        for (entry in input.todayLog) {
            if ((entry.kind == ReviewKind.RECENT || entry.kind == ReviewKind.OLD) && entry.passageId != null) {
                lastReviewEntry.remove(entry.passageId)
                lastReviewEntry[entry.passageId] = entry
            }
        }

        // --- Old: the manzil portion ---
        val portion = Manzil.portion(input.passages, input.todayLog, plan?.manzilDays ?: HifzConstants.MANZIL_DAYS_DEFAULT, metrics, today)
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
        val newToday = input.todayLog.lastOrNull { it.kind == ReviewKind.NEW }
        val next = plan?.let { NewPassagePlanner.next(it, coverage, metrics) }
        val planFinished = plan != null && HifzStatuses.currentSurah(plan, coverage) == null

        // --- Budget ---
        fun reviewEstimate(p: Passage) = HifzConstants.reviewSeconds(metrics.weight(p))

        var running = recentDone.sumOf { (p, _) -> reviewEstimate(p) } +
            portion.items.filter { it.done }.sumOf { reviewEstimate(it.passage) }
        var included = recentDone.size + portion.items.count { it.done }
        var newBlock: LessonNew? = null
        if (newToday != null) {
            val estimate = HifzConstants.newSeconds(metrics.chars(newToday.surah, newToday.startAyah, newToday.endAyah))
            running += estimate
            included++
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
        var remainingSeconds = 0
        var deferred = 0
        var cutoff = false

        fun accept(estimate: Int): Boolean {
            if (!cutoff && (included == 0 || running + estimate <= HifzConstants.LESSON_BUDGET_SECONDS)) {
                running += estimate
                remainingSeconds += estimate
                included++
                return true
            }
            cutoff = true
            deferred++
            return false
        }

        val recentAccepted = recentPending.filter { accept(reviewEstimate(it)) }
        val oldPending = portion.items.filter { !it.done }.sortedWith(Manzil.ITEM_PRIORITY)
        val oldAcceptedIds = oldPending.filter { accept(reviewEstimate(it.passage)) }.map { it.passage.id }.toSet()

        if (newToday == null && next != null) {
            val estimate = HifzConstants.newSeconds(metrics.chars(next.surah, next.startAyah, next.endAyah))
            // Deferred only behind a cut repetition (a backlog); otherwise it is in, whatever it does to the budget.
            val fits = !cutoff
            if (fits) {
                running += estimate
                remainingSeconds += estimate
                included++
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
        )

        val recentItems = recentDone.map { (p, grade) -> item(p, done = true, grade = grade) } +
            recentAccepted.map { item(it, done = false, grade = null) }
        val oldItems = portion.items
            .filter { it.done || it.passage.id in oldAcceptedIds }
            .map { item(it.passage, it.done, it.grade) }

        val pendingNew = newBlock?.state == LessonNewState.PENDING
        val isEmpty = recentItems.isEmpty() && oldItems.isEmpty() &&
            (newBlock == null || newBlock.state == LessonNewState.DEFERRED)
        val isComplete = !isEmpty && recentItems.all { it.done } && oldItems.all { it.done } && !pendingNew

        val tomorrowLadder = input.passages.count { it.stage != PassageStage.OLD && (it.nextDueEpochDay ?: Int.MAX_VALUE) <= today + 1 }
        val tomorrowOld = Manzil.portion(input.passages, emptyList(), plan?.manzilDays ?: HifzConstants.MANZIL_DAYS_DEFAULT, metrics, today + 1).items.size
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

}
