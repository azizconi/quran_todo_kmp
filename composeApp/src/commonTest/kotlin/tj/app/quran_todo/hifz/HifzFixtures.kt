package tj.app.quran_todo.hifz

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.TextMetrics

/** Fixtures of the hifz tests: a Quran with the real ayah counts, 100 characters an ayah and 10 ayahs a page. */
object Fx {
    /**
     * All 114 surahs with the real number of ayahs. By default an ayah has 100 characters and a page holds 10 ayahs of one
     * surah (the pages of different surahs never mix), so 5 ayahs weigh 500 and a full page 1000.
     */
    fun metrics(
        chars: (surah: Int, ayah: Int) -> Int = { _, _ -> 100 },
        page: (surah: Int, ayah: Int) -> Int = { surah, ayah -> surah * 100 + (ayah - 1) / 10 },
    ): TextMetrics = TextMetrics(
        (1..QuranFacts.SURAH_COUNT).flatMap { surah ->
            (1..QuranFacts.ayahCount(surah)).map { ayah -> AyahMetric(surah, ayah, page(surah, ayah), chars(surah, ayah)) }
        },
    )

    val flat: TextMetrics = metrics()

    fun passage(
        id: Long,
        surah: Int,
        start: Int,
        end: Int,
        stage: PassageStage = PassageStage.OLD,
        due: Int? = null,
        last: Int? = null,
        lapses: Int = 0,
        learned: Int = 0,
        hard: Map<Int, Int> = emptyMap(),
    ) = Passage(
        id = id,
        surah = surah,
        startAyah = start,
        endAyah = end,
        learnedEpochDay = learned,
        stage = stage,
        nextDueEpochDay = if (stage == PassageStage.OLD) null else due,
        lastReviewedEpochDay = last,
        lapses = lapses,
        hard = hard,
    )

    fun plan(
        order: List<Int>,
        daily: Int = 5,
        target: Int? = null,
        started: Int = 0,
        manzilDays: Int = 7,
        scope: HifzScope = HifzScope.CUSTOM,
    ) = HifzPlan(scope, order, daily, target, started, manzilDays)

    private var logId = 0L

    fun log(
        passageId: Long?,
        surah: Int,
        start: Int,
        end: Int,
        kind: ReviewKind,
        grade: ReviewGrade = ReviewGrade.GOOD,
        day: Int,
    ) = ReviewLogEntry(++logId, passageId, surah, start, end, kind, grade, day, timestamp = day * 86_400_000L + logId)

    fun logOf(passage: Passage, kind: ReviewKind, grade: ReviewGrade = ReviewGrade.GOOD, day: Int) =
        log(passage.id, passage.surah, passage.startAyah, passage.endAyah, kind, grade, day)
}
