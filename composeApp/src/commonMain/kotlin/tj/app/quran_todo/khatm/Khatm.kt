package tj.app.quran_todo.khatm

import tj.app.quran_todo.common.mushaf.MushafPages

/**
 * The reading plan of a khatm (task F5b): read the pages [startPage]..[endPage] of the Madani mushaf from [startEpochDay] to [targetEpochDay]
 * (both days included). It is separate from memorization: reading by this plan writes nothing to the review log and changes neither the
 * lesson, the streak nor the retention.
 */
data class KhatmPlan(
    val startEpochDay: Int,
    val targetEpochDay: Int,
    val startPage: Int = 1,
    val endPage: Int = MushafPages.COUNT,
) {
    /** How many pages the plan holds. */
    val totalPages: Int get() = endPage - startPage + 1

    /** How many days the plan has, the first and the last included (at least 1). */
    val totalDays: Int get() = (targetEpochDay - startEpochDay + 1).coerceAtLeast(1)
}

/** What was read: [lastPage] is the last page read ([KhatmPlan.startPage] - 1 for nothing yet), [readDays] the days a portion was marked as read. */
data class KhatmProgress(
    val lastPage: Int,
    val readDays: Set<Int> = emptySet(),
)

/** The stored state of a khatm: the plan and the progress. */
data class KhatmState(val plan: KhatmPlan, val progress: KhatmProgress)

/** The portion of one day: the pages [firstPage]..[lastPage]. */
data class KhatmPortion(val firstPage: Int, val lastPage: Int) {
    val pages: Int get() = lastPage - firstPage + 1
}

/** What the card of the home screen says about today. */
sealed interface KhatmToday {
    /** There is a portion to read today. */
    data class Pending(val portion: KhatmPortion, val minutes: Int) : KhatmToday

    /** Today's portion is marked as read; [next] is tomorrow's portion (null when the plan is finished). */
    data class DoneToday(val next: KhatmPortion?) : KhatmToday

    /**
     * The end day of the plan has passed and pages are left: there is no portion of the day (it would be the whole rest), only the page to
     * go on from ([nextPage]) and the choice of a new end day.
     */
    data class Overdue(val nextPage: Int) : KhatmToday

    /** Every page of the plan is read. */
    data object Complete : KhatmToday
}

/** The state of one day of the calendar of the plan. */
enum class KhatmDayStatus { Read, Missed, Today, Future }

data class KhatmCalendarDay(val epochDay: Int, val status: KhatmDayStatus)

/** The calculations of the reading plan. Pure; the tests feed them any plan and any day. */
object KhatmMath {
    /** Presets of the length of a plan, in days. */
    val PRESET_DAYS: List<Int> = listOf(7, 15, 30, 60)

    /** A page of the mushaf takes about a minute and a quarter to read calmly: 20 pages are about 25 minutes. */
    fun minutesFor(pages: Int): Int = if (pages <= 0) 0 else (pages * 5 + 3) / 4

    /** A plan of [days] days that starts [today] on page [fromPage] and ends at the end of the mushaf. */
    fun planForDays(days: Int, today: Int, fromPage: Int = 1): KhatmPlan =
        KhatmPlan(
            startEpochDay = today,
            targetEpochDay = today + days.coerceAtLeast(1) - 1,
            startPage = fromPage.coerceIn(1, MushafPages.COUNT),
        )

    /** A plan that ends on [targetEpochDay] (not before [today]). */
    fun planToDate(targetEpochDay: Int, today: Int, fromPage: Int = 1): KhatmPlan =
        KhatmPlan(
            startEpochDay = today,
            targetEpochDay = targetEpochDay.coerceAtLeast(today),
            startPage = fromPage.coerceIn(1, MushafPages.COUNT),
        )

    /** The plan with another end day (not before [today]); the start and the progress stay. */
    fun withTarget(plan: KhatmPlan, targetEpochDay: Int, today: Int): KhatmPlan =
        plan.copy(targetEpochDay = targetEpochDay.coerceAtLeast(maxOf(today, plan.startEpochDay)))

    /** The progress of a new plan: nothing read. */
    fun freshProgress(plan: KhatmPlan): KhatmProgress = KhatmProgress(lastPage = plan.startPage - 1)

    fun pagesRead(state: KhatmState): Int =
        (state.progress.lastPage - state.plan.startPage + 1).coerceIn(0, state.plan.totalPages)

    fun pagesLeft(state: KhatmState): Int = state.plan.totalPages - pagesRead(state)

    fun isComplete(state: KhatmState): Boolean = pagesLeft(state) <= 0

    /** Share of the plan that is read, 0..1. */
    fun fraction(state: KhatmState): Float = pagesRead(state).toFloat() / state.plan.totalPages.coerceAtLeast(1)

    /** Days from [today] to the end of the plan, both included, never less than 1 (a plan that is over asks for the rest at once). */
    fun daysLeft(plan: KhatmPlan, today: Int): Int = (plan.targetEpochDay - maxOf(today, plan.startEpochDay) + 1).coerceAtLeast(1)

    /** Pages a day: what is left divided by the days that are left, rounded up. */
    fun dailyPages(state: KhatmState, today: Int): Int {
        val left = pagesLeft(state)
        if (left <= 0) return 0
        val days = daysLeft(state.plan, today)
        return (left + days - 1) / days
    }

    /** The portion of [today] (the next pages after the last read one), null when the plan is finished. */
    fun portion(state: KhatmState, today: Int): KhatmPortion? {
        val pages = dailyPages(state, today)
        if (pages <= 0) return null
        val first = (state.progress.lastPage + 1).coerceAtLeast(state.plan.startPage)
        return KhatmPortion(first, minOf(state.plan.endPage, first + pages - 1))
    }

    /** The end day is behind [today] and the plan is not finished. */
    fun isOverdue(state: KhatmState, today: Int): Boolean = !isComplete(state) && today > state.plan.targetEpochDay

    /** What the card says: the portion of today, "read today" with the portion of tomorrow, "overdue", or "complete". */
    fun status(state: KhatmState, today: Int): KhatmToday {
        if (isComplete(state)) return KhatmToday.Complete
        if (isOverdue(state, today)) return KhatmToday.Overdue((state.progress.lastPage + 1).coerceAtLeast(state.plan.startPage))
        if (today in state.progress.readDays) return KhatmToday.DoneToday(portion(state, today + 1))
        val portion = portion(state, today) ?: return KhatmToday.Complete
        return KhatmToday.Pending(portion, minutesFor(portion.pages))
    }

    /**
     * Pages ahead of the plan (positive) or behind it (negative) at [today]: what is read minus what the plan wanted to be read by the
     * start of today (by the end of today once today's portion is marked).
     */
    fun deltaPages(state: KhatmState, today: Int): Int {
        val plan = state.plan
        val days = plan.totalDays
        val elapsed = ((today - plan.startEpochDay) + if (today in state.progress.readDays) 1 else 0).coerceIn(0, days)
        val expected = (plan.totalPages.toLong() * elapsed / days).toInt()
        return pagesRead(state) - expected
    }

    /** Days between [today] and the end day of the plan (0 on the last day, negative once it is over). */
    fun daysToEnd(plan: KhatmPlan, today: Int): Int = plan.targetEpochDay - today

    /** The calendar of the plan: every day from the start to the end with its state, at most [limit] days. */
    fun calendar(state: KhatmState, today: Int, limit: Int = 400): List<KhatmCalendarDay> {
        val plan = state.plan
        val last = minOf(plan.targetEpochDay, plan.startEpochDay + limit - 1)
        return (plan.startEpochDay..last).map { day ->
            val status = when {
                day in state.progress.readDays -> KhatmDayStatus.Read
                day == today -> KhatmDayStatus.Today
                day < today -> KhatmDayStatus.Missed
                else -> KhatmDayStatus.Future
            }
            KhatmCalendarDay(day, status)
        }
    }

    /** The portion of [today] is read: the last page is the end of the portion and [today] is marked. Null when there is no portion. */
    fun markPortionRead(state: KhatmState, today: Int): KhatmProgress? {
        if (isOverdue(state, today)) return null
        val portion = portion(state, today) ?: return null
        return KhatmProgress(portion.lastPage, state.progress.readDays + today)
    }

    /**
     * The manual correction "Mark up to page N": the last read page becomes [page] (forward or back, inside the plan). [today] is marked as
     * a reading day when the page moves forward.
     */
    fun markUpTo(state: KhatmState, page: Int, today: Int): KhatmProgress {
        val plan = state.plan
        val clamped = page.coerceIn(plan.startPage - 1, plan.endPage)
        val days = if (clamped > state.progress.lastPage) state.progress.readDays + today else state.progress.readDays
        return KhatmProgress(clamped, days)
    }

    /**
     * The person has read in the mushaf up to [readPage] (the last page they were on). True when that is the end of today's pending
     * portion, so the card may softly ask "Mark as read?". Never decides anything by itself.
     */
    fun portionReachedBy(state: KhatmState, today: Int, readPage: Int?): Boolean {
        if (readPage == null) return false
        val pending = status(state, today) as? KhatmToday.Pending ?: return false
        // Only the last page of the portion (or the one after it): an old reading position far ahead says nothing about today.
        return readPage in pending.portion.lastPage..pending.portion.lastPage + 1
    }
}

/** Stores a [KhatmState] as text values of `hifz_meta`: the keys all start with `khatm_`, so the backup file carries them without a change. */
object KhatmStorage {
    const val PLAN_KEY = "khatm_plan"
    const val PAGE_KEY = "khatm_page"
    const val DAYS_KEY = "khatm_days"

    val KEYS: List<String> = listOf(PLAN_KEY, PAGE_KEY, DAYS_KEY)

    /** The days kept: a plan is at most a few hundred days long. */
    private const val MAX_DAYS = 800

    fun encodePlan(plan: KhatmPlan): String = "${plan.startEpochDay},${plan.targetEpochDay},${plan.startPage},${plan.endPage}"

    fun decodePlan(text: String?): KhatmPlan? {
        val parts = text?.split(',')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
        if (parts.size != 4) return null
        val (start, target, first, last) = parts
        if (first !in 1..MushafPages.COUNT || last !in first..MushafPages.COUNT || target < start) return null
        return KhatmPlan(start, target, first, last)
    }

    fun encodeDays(days: Set<Int>): String = days.sorted().takeLast(MAX_DAYS).joinToString(",")

    fun decodeDays(text: String?): Set<Int> =
        text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** The state from the stored values; null when there is no valid plan. A page outside the plan is cut back into it. */
    fun decode(plan: String?, page: String?, days: String?): KhatmState? {
        val decoded = decodePlan(plan) ?: return null
        val last = (page?.trim()?.toIntOrNull() ?: (decoded.startPage - 1)).coerceIn(decoded.startPage - 1, decoded.endPage)
        return KhatmState(decoded, KhatmProgress(last, decodeDays(days)))
    }
}
