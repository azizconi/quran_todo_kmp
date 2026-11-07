package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind

/**
 * Cutting a range of ayahs into pieces that follow the pages of the mushaf: a piece is a run of ayahs that start on
 * the same page (an ayah belongs to the page where it starts). A short surah that lies on one page is one piece.
 */
object Pagination {
    fun cutByPage(surah: Int, startAyah: Int, endAyah: Int, metrics: TextMetrics): List<AyahRange> {
        if (endAyah < startAyah) return emptyList()
        val pieces = ArrayList<AyahRange>()
        var pieceStart = startAyah
        var page = metrics.page(surah, startAyah)
        for (ayah in startAyah + 1..endAyah) {
            val ayahPage = metrics.page(surah, ayah)
            if (ayahPage != page) {
                pieces += AyahRange(surah, pieceStart, ayah - 1)
                pieceStart = ayah
                page = ayahPage
            }
        }
        pieces += AyahRange(surah, pieceStart, endAyah)
        return pieces
    }
}

/**
 * The operations on the list of passages as pure functions: every one takes the whole list and returns the new
 * one. A passage with `id == 0` is new (it gets its id when it is stored). The repository diffs the lists and
 * writes the difference in one transaction.
 */
object PassageOps {
    /** The result of a repetition: the new list of all passages and what the log record needs. */
    class ReviewOutcome(
        val passages: List<Passage>,
        /** The passage as it was before the repetition (its range goes to the log). */
        val before: Passage,
        /** RECENT for a passage of the ladder, OLD for a passage of the manzil circle. */
        val kind: ReviewKind,
    )

    fun overlaps(passages: List<Passage>, surah: Int, startAyah: Int, endAyah: Int): Boolean =
        passages.any { it.surah == surah && it.startAyah <= endAyah && startAyah <= it.endAyah }

    /** A passage that was learned today: R1, due tomorrow, with the ayahs the person marked as difficult. */
    fun newPassage(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        marks: Set<Int>,
        today: Int,
    ): Passage {
        val step = Ladder.firstStep(today)
        return Passage(
            id = 0,
            surah = surah,
            startAyah = startAyah,
            endAyah = endAyah,
            learnedEpochDay = today,
            stage = step.stage,
            nextDueEpochDay = step.nextDueEpochDay,
            lastReviewedEpochDay = today,
            lapses = step.lapses,
            hard = marks.filter { it in startAyah..endAyah }.associateWith { HifzConstants.HARD_CLEARS_AFTER },
        )
    }

    /**
     * One repetition of the passage [id]: the ladder or, for an OLD passage, the manzil rule ([Ladder.afterReview]);
     * the difficult ayahs ([HardAyahs.afterReview]); a passage that has just become OLD is merged with its OLD
     * neighbours ([mergeOld]). Null when there is no such passage.
     */
    fun review(
        passages: List<Passage>,
        id: Long,
        grade: ReviewGrade,
        marks: Set<Int>,
        today: Int,
        metrics: TextMetrics,
    ): ReviewOutcome? {
        val before = passages.firstOrNull { it.id == id } ?: return null
        val step = Ladder.afterReview(before.stage, grade, today, before.lapses)
        val after = before.copy(
            stage = step.stage,
            nextDueEpochDay = step.nextDueEpochDay,
            lastReviewedEpochDay = today,
            lapses = step.lapses,
            hard = HardAyahs.afterReview(before.hard, grade, marks, before.startAyah, before.endAyah),
        )
        var list = passages.map { if (it.id == id) after else it }
        if (after.stage == PassageStage.OLD && before.stage != PassageStage.OLD) {
            list = mergeOld(list, id, metrics)
        }
        return ReviewOutcome(
            passages = list,
            before = before,
            kind = if (before.stage == PassageStage.OLD) ReviewKind.OLD else ReviewKind.RECENT,
        )
    }

    /**
     * Merges the OLD passage [id] with OLD passages of the same surah that touch it (adjacent ranges) while the merged
     * weight stays within [HifzConstants.MAX_OLD_PASSAGE_CHARS]; the lighter neighbour first, the left one on a tie. The
     * result keeps [id], learned day = the earliest, lastReviewed = the smaller one (a never reviewed part counts as
     * the smallest, so the merged passage is as overdue as its most overdue part), lapses add up, the difficult ayahs
     * move over.
     */
    fun mergeOld(passages: List<Passage>, id: Long, metrics: TextMetrics): List<Passage> {
        var list = passages
        var current = list.firstOrNull { it.id == id && it.stage == PassageStage.OLD } ?: return passages
        while (true) {
            val weight = metrics.weight(current)
            val left = list.firstOrNull {
                it.id != current.id && it.stage == PassageStage.OLD && it.surah == current.surah &&
                    it.endAyah + 1 == current.startAyah
            }
            val right = list.firstOrNull {
                it.id != current.id && it.stage == PassageStage.OLD && it.surah == current.surah &&
                    it.startAyah == current.endAyah + 1
            }
            val neighbour = listOfNotNull(left, right)
                .filter { weight + metrics.weight(it) <= HifzConstants.MAX_OLD_PASSAGE_CHARS }
                .minByOrNull { metrics.weight(it) } // minBy keeps the first (the left one) on a tie
                ?: break
            val merged = current.copy(
                startAyah = minOf(current.startAyah, neighbour.startAyah),
                endAyah = maxOf(current.endAyah, neighbour.endAyah),
                learnedEpochDay = minOf(current.learnedEpochDay, neighbour.learnedEpochDay),
                lastReviewedEpochDay = smallerReviewDay(current.lastReviewedEpochDay, neighbour.lastReviewedEpochDay),
                lapses = current.lapses + neighbour.lapses,
                hard = current.hard + neighbour.hard,
            )
            list = list.filter { it.id != neighbour.id }.map { if (it.id == current.id) merged else it }
            current = merged
        }
        return list
    }

    private fun smallerReviewDay(a: Int?, b: Int?): Int? = if (a == null || b == null) null else minOf(a, b)

    /**
     * "Check yourself": the stages and the due days do not change. The ayahs the person marked get the difficult flag
     * in the passages that cover them (a flag that is there starts over). Ayahs that are not covered are ignored.
     */
    fun check(passages: List<Passage>, surah: Int, marks: Set<Int>): List<Passage> {
        if (marks.isEmpty()) return passages
        return passages.map { passage ->
            if (passage.surah != surah) return@map passage
            val own = marks.filter { it in passage }
            if (own.isEmpty()) {
                passage
            } else {
                passage.copy(hard = passage.hard + own.associateWith { HifzConstants.HARD_CLEARS_AFTER })
            }
        }
    }

    /** The id of the passage that covers the whole range, or null when none does (the range spans several). */
    fun coveringPassageId(passages: List<Passage>, surah: Int, startAyah: Int, endAyah: Int): Long? =
        passages.firstOrNull { it.surah == surah && it.startAyah <= startAyah && endAyah <= it.endAyah }?.id

    /**
     * The ayahs of the range that are not covered become OLD passages (never reviewed), cut by the pages of the
     * mushaf. Covered ayahs are left as they are.
     */
    fun markKnown(
        passages: List<Passage>,
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        today: Int,
        metrics: TextMetrics,
    ): List<Passage> {
        val coverage = Coverage(passages, metrics)
        val added = ArrayList<Passage>()
        var runStart = -1
        for (ayah in startAyah..endAyah + 1) {
            val free = ayah <= endAyah && !coverage.isCovered(surah, ayah)
            if (free && runStart < 0) runStart = ayah
            if (!free && runStart >= 0) {
                for (piece in Pagination.cutByPage(surah, runStart, ayah - 1, metrics)) {
                    added += oldPassage(piece, today)
                }
                runStart = -1
            }
        }
        return passages + added
    }

    private fun oldPassage(range: AyahRange, today: Int) = Passage(
        id = 0,
        surah = range.surah,
        startAyah = range.startAyah,
        endAyah = range.endAyah,
        learnedEpochDay = today,
        stage = PassageStage.OLD,
        nextDueEpochDay = null,
        lastReviewedEpochDay = null,
        lapses = 0,
    )

    /**
     * Takes the ayahs of the range out of the passages. A passage that is inside the range disappears; one that is
     * cut at an end keeps its row; one that has the range in the middle is split in two (the left part keeps the row,
     * the right one is new). The parts keep the stage, the due day and the lapses; the difficult ayahs follow the numbers.
     */
    fun forget(passages: List<Passage>, surah: Int, startAyah: Int, endAyah: Int): List<Passage> {
        val result = ArrayList<Passage>(passages.size + 1)
        for (passage in passages) {
            val touched = passage.surah == surah && passage.startAyah <= endAyah && startAyah <= passage.endAyah
            if (!touched) {
                result += passage
                continue
            }
            val hasLeft = passage.startAyah < startAyah
            val hasRight = passage.endAyah > endAyah
            if (hasLeft) {
                result += passage.copy(
                    endAyah = startAyah - 1,
                    hard = HardAyahs.restrictTo(passage.hard, passage.startAyah, startAyah - 1),
                )
            }
            if (hasRight) {
                result += passage.copy(
                    id = if (hasLeft) 0 else passage.id,
                    startAyah = endAyah + 1,
                    hard = HardAyahs.restrictTo(passage.hard, endAyah + 1, passage.endAyah),
                )
            }
        }
        return result
    }
}
