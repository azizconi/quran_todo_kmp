package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry

/** One passage of the manzil portion of the day. */
class ManzilItem(val passage: Passage, val done: Boolean, val grade: ReviewGrade?)

/** The portion of the manzil circle for one day: [quota] is the weight (characters) the day has to cover. */
class ManzilPortion(val quota: Int, val items: List<ManzilItem>)

/**
 * The manzil: the circle of the OLD passages. There is no cursor, so a new OLD passage in the middle of the
 * circle breaks nothing:
 *
 * - weight of a passage = characters of its text;
 * - the quota of a day = `ceil(total weight of all OLD / manzilDays)`;
 * - a passage may be picked for a day only if it is **eligible**: it was never reviewed, or its last repetition was at
 *   least `manzilDays` days ago (`today - lastReviewed >= manzilDays`). So a passage comes back about once every
 *   `manzilDays` days and never more often - a single OLD passage is repeated once in `manzilDays` days, not every day;
 *   a ladder passage that has just become OLD (lastReviewed = today) is not picked again today;
 * - the portion of a day: first what was already repeated today as OLD (it counts into the quota), then the eligible
 *   OLD passages by priority - `lastReviewed` ascending (never reviewed = the oldest), then the order of the mushaf -
 *   while the quota is not reached (the passage that crosses the quota is taken whole). The items of the portion are
 *   listed in the order of the mushaf. A day with nothing eligible and nothing done has no Old block.
 *
 * The quota spreads a backlog over the following days: right after the import every passage is "never reviewed", so
 * about `1 / manzilDays` of the circle is offered a day until everyone has had a turn; after that the passages come
 * back in the same order, one `manzilDays` later. The portion does not jump during the day: a passage that was repeated
 * is "done" and keeps its place. An OLD passage that was repeated today and was sent back to the ladder (HARD, FORGOT)
 * stays in the portion as done and counts into the quota and into the total weight, otherwise the quota would change
 * under the person.
 */
object Manzil {
    fun quota(totalWeight: Int, manzilDays: Int): Int {
        if (totalWeight <= 0) return 0
        val days = manzilDays.coerceAtLeast(1)
        return (totalWeight + days - 1) / days
    }

    fun portion(
        passages: List<Passage>,
        todayLog: List<ReviewLogEntry>,
        manzilDays: Int,
        metrics: TextMetrics,
        today: Int,
    ): ManzilPortion {
        val byId = passages.associateBy { it.id }
        // The last OLD record of today per passage.
        val doneGrades = LinkedHashMap<Long, ReviewGrade>()
        for (entry in todayLog) {
            if (entry.kind == ReviewKind.OLD && entry.passageId != null) doneGrades[entry.passageId] = entry.grade
        }
        val donePassages = doneGrades.keys.mapNotNull { byId[it] }
        val old = passages.filter { it.stage == PassageStage.OLD }
        val leftTheCircle = donePassages.filter { it.stage != PassageStage.OLD }

        val totalWeight = old.sumOf { metrics.weight(it) } + leftTheCircle.sumOf { metrics.weight(it) }
        val quota = quota(totalWeight, manzilDays)
        var covered = donePassages.sumOf { metrics.weight(it) }

        val picks = ArrayList<Passage>()
        val queue = old.filter { it.id !in doneGrades && isEligible(it, today, manzilDays) }.sortedWith(PRIORITY)
        for (passage in queue) {
            if (covered >= quota) break
            picks += passage
            covered += metrics.weight(passage)
        }

        val items = (donePassages.map { ManzilItem(it, done = true, grade = doneGrades[it.id]) } +
            picks.map { ManzilItem(it, done = false, grade = null) })
            .sortedWith(compareBy({ it.passage.surah }, { it.passage.startAyah }))
        return ManzilPortion(quota, items)
    }

    /**
     * Never reviewed, or the last repetition was [manzilDays] or more days ago. A last repetition "in the future" (the clock
     * was set back) counts as eligible so that the circle cannot stall.
     */
    fun isEligible(passage: Passage, today: Int, manzilDays: Int): Boolean {
        val last = passage.lastReviewedEpochDay ?: return true
        return last > today || today - last >= manzilDays
    }

    /** The picks of the day in the order the circle goes: the oldest first (never reviewed = the oldest), then the mushaf. */
    val PRIORITY: Comparator<Passage> = compareBy<Passage>(
        { it.lastReviewedEpochDay ?: Int.MIN_VALUE },
        { it.surah },
        { it.startAyah },
    )

    val ITEM_PRIORITY: Comparator<ManzilItem> = Comparator { a, b -> PRIORITY.compare(a.passage, b.passage) }
}
