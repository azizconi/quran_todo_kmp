package tj.app.quran_todo.hifz.legacy

import kotlinx.datetime.TimeZone
import tj.app.quran_todo.common.settings.parseAyahKey
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.TextMetrics

/** The old data of a person, as it is in the database and in the preferences before the passage model. */
class LegacyInput(
    val surahTodos: List<SurahTodoEntity>,
    val ayahTodos: List<AyahTodoEntity>,
    val ayahReviews: List<AyahReviewEntity>,
    /** Keys `"surah:number"` of the weak ayahs from the preferences. */
    val weakKeys: Set<String>,
    /** The daily goal of the old home screen, if the person set one. */
    val dailyGoal: Int?,
    val today: Int,
    val zone: TimeZone = TimeZone.currentSystemDefault(),
)

class LegacyImportResult(
    val passages: List<Passage>,
    val plan: HifzPlan?,
    /** Weak ayah keys that did not fit into any passage (or into any surah) and were dropped. */
    val droppedWeakKeys: Int,
)

/**
 * The one-time conversion of the old data into passages (pure; the repository runs it in one transaction).
 *
 * 1. `surah_todo` LEARNED: the whole surah is covered by `OLD` passages, never reviewed.
 * 2. `ayah_todo` LEARNED (surah not LEARNED): runs of ayahs become `OLD` passages.
 * 3. `ayah_todo` LEARNING and ayahs that have an `ayah_review` but no status become ladder passages: the stage comes from
 *    `ayah_review.intervalIndex` (0 R1, 1 R3, 2 R7, 3 R14, 4 R30, 5+ OLD), the due day is the day of `nextReviewAt` (it may be
 *    in the past: then it is overdue); without a review R1 due today. A passage of several ayahs takes the smallest stage
 *    and the smallest due day.
 * 4. Runs of consecutive ayahs of one class are cut by the pages of the mushaf (an ayah belongs to the page it starts on).
 * 5. The old tables number ayahs globally: they become numbers in the surah through [QuranFacts.firstAyahNumber]. A weak key
 *    `"s:n"`: if `n` is in the global range of surah `s` it is global, else if it is in `1..ayahCount(s)` it is a number in
 *    the surah, else it is dropped. A weak ayah inside a passage becomes a difficult ayah (clears after 2), outside - dropped.
 * 6. The plan: surahs that are being learned (`surah_todo` LEARNING, or covered partly by the passages) make a `CUSTOM` plan: all
 *    in 67..114 - descending (short to long), else ascending; `dailyNewAyahs` = the old daily goal when it is in 1..20, else 5.
 *    No such surahs - no plan.
 * 7. `ayah_review` and the SM-2 state are not used by the Android path any more, but they are not deleted.
 */
object LegacyImport {
    private enum class AyahClass { NONE, OLD, LADDER }

    private class ReviewInfo(val rank: Int, val dueDay: Int, val lastReviewedDay: Int)

    fun run(input: LegacyInput, metrics: TextMetrics): LegacyImportResult {
        val today = input.today
        val classes = Array(QuranFacts.SURAH_COUNT + 1) { surah -> Array(metrics.ayahCount(surah) + 1) { AyahClass.NONE } }
        val reviews = HashMap<Pair<Int, Int>, ReviewInfo>()
        val learnedDays = HashMap<Pair<Int, Int>, Int>()

        fun mark(surah: Int, ayah: Int, kind: AyahClass) {
            if (surah in 1..QuranFacts.SURAH_COUNT && ayah in 1..metrics.ayahCount(surah) && classes[surah][ayah] == AyahClass.NONE) {
                classes[surah][ayah] = kind
            }
        }

        // 1. Learned surahs.
        val learnedSurahs = input.surahTodos.filter { it.status == SurahTodoStatus.LEARNED }.map { it.surahNumber }.toSet()
        for (surah in learnedSurahs) for (ayah in 1..metrics.ayahCount(surah)) mark(surah, ayah, AyahClass.OLD)

        // 2. Learned ayahs.
        for (row in input.ayahTodos) {
            val ayah = inSurahNumber(row.surahNumber, row.ayahNumber) ?: continue
            if (row.status == AyahTodoStatus.LEARNED) {
                mark(row.surahNumber, ayah, AyahClass.OLD)
            }
            val day = minOf(today, EpochDays.ofMillis(row.updatedAt, input.zone))
            val key = row.surahNumber to ayah
            learnedDays[key] = minOf(learnedDays[key] ?: day, day)
        }

        // 3. Ayahs in the ladder.
        for (review in input.ayahReviews) {
            val ayah = inSurahNumber(review.surahNumber, review.ayahNumber) ?: continue
            reviews[review.surahNumber to ayah] = ReviewInfo(
                rank = review.intervalIndex.coerceAtLeast(0),
                dueDay = EpochDays.ofMillis(review.nextReviewAt, input.zone),
                lastReviewedDay = minOf(today, EpochDays.ofMillis(review.lastReviewedAt, input.zone)),
            )
        }
        for (row in input.ayahTodos) {
            if (row.status != AyahTodoStatus.LEARNING) continue
            val ayah = inSurahNumber(row.surahNumber, row.ayahNumber) ?: continue
            mark(row.surahNumber, ayah, AyahClass.LADDER)
        }
        for ((key, _) in reviews) mark(key.first, key.second, AyahClass.LADDER)

        // 4. Cut into passages.
        val passages = ArrayList<Passage>()
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            val count = metrics.ayahCount(surah)
            var runStart = -1
            var runClass = AyahClass.NONE
            for (ayah in 1..count + 1) {
                val kind = if (ayah <= count) classes[surah][ayah] else AyahClass.NONE
                val continues = runStart >= 0 && kind == runClass && metrics.page(surah, ayah) == metrics.page(surah, runStart)
                if (runStart >= 0 && !continues) {
                    passages += buildPassage(surah, runStart, ayah - 1, runClass, today, reviews, learnedDays)
                    runStart = -1
                }
                if (runStart < 0 && kind != AyahClass.NONE) {
                    runStart = ayah
                    runClass = kind
                }
            }
        }

        // 5. Weak ayahs become difficult ayahs of the passages that cover them.
        val hard = HashMap<Int, MutableMap<Int, Int>>() // index in passages -> ayah -> clears
        var dropped = 0
        for (key in input.weakKeys) {
            val parsed = parseAyahKey(key)
            val resolved = parsed?.let { resolveWeak(it.surahNumber, it.ayahNumber) }
            val index = resolved?.let { (surah, ayah) -> passages.indexOfFirst { it.surah == surah && ayah in it } } ?: -1
            if (resolved == null || index < 0) {
                dropped++
            } else {
                hard.getOrPut(index) { LinkedHashMap() }[resolved.second] = HifzConstants.HARD_CLEARS_AFTER
            }
        }
        val withHard = passages.mapIndexed { index, passage ->
            hard[index]?.let { marks -> passage.copy(hard = marks.entries.sortedBy { it.key }.associate { it.key to it.value }) }
                ?: passage
        }

        return LegacyImportResult(withHard, buildPlan(input, withHard, metrics), dropped)
    }

    private fun buildPassage(
        surah: Int,
        start: Int,
        end: Int,
        kind: AyahClass,
        today: Int,
        reviews: Map<Pair<Int, Int>, ReviewInfo>,
        learnedDays: Map<Pair<Int, Int>, Int>,
    ): Passage {
        val learned = (start..end).mapNotNull { learnedDays[surah to it] }.minOrNull() ?: today
        if (kind == AyahClass.OLD) {
            return Passage(0, surah, start, end, learned, PassageStage.OLD, null, null, 0)
        }
        val infos = (start..end).map { reviews[surah to it] }
        // An ayah without a review is R1 due today.
        val rank = infos.minOf { it?.rank ?: 0 }
        val stage = PassageStage.ofRank(rank)
        val due = if (stage == PassageStage.OLD) {
            null
        } else {
            infos.minOf { info -> if (info == null) today else if (info.rank >= PassageStage.OLD.rank) Int.MAX_VALUE else info.dueDay }
        }
        val lastReviewed = infos.filterNotNull().minOfOrNull { it.lastReviewedDay }
        return Passage(0, surah, start, end, learned, stage, due, lastReviewed, 0)
    }

    /** The number in the surah of the global ayah number [global] of [surah], or null if it does not belong to it. */
    private fun inSurahNumber(surah: Int, global: Int): Int? {
        val first = QuranFacts.firstAyahNumber(surah)
        if (first == 0) return null
        val ayah = global - first + 1
        return ayah.takeIf { it in 1..QuranFacts.ayahCount(surah) }
    }

    /** The meaning of a weak key `surah:n` (see the rules above); null when it fits nothing. */
    private fun resolveWeak(surah: Int, n: Int): Pair<Int, Int>? {
        if (surah !in 1..QuranFacts.SURAH_COUNT) return null
        inSurahNumber(surah, n)?.let { return surah to it }
        return if (n in 1..QuranFacts.ayahCount(surah)) surah to n else null
    }

    private fun buildPlan(input: LegacyInput, passages: List<Passage>, metrics: TextMetrics): HifzPlan? {
        val covered = IntArray(QuranFacts.SURAH_COUNT + 1)
        val flags = Array(QuranFacts.SURAH_COUNT + 1) { BooleanArray(metrics.ayahCount(it) + 1) }
        for (passage in passages) for (ayah in passage.startAyah..passage.endAyah) {
            if (!flags[passage.surah][ayah]) {
                flags[passage.surah][ayah] = true
                covered[passage.surah]++
            }
        }
        val learning = HashSet<Int>()
        for (row in input.surahTodos) if (row.status == SurahTodoStatus.LEARNING) learning += row.surahNumber
        for (surah in 1..QuranFacts.SURAH_COUNT) if (covered[surah] > 0) learning += surah
        val order = learning
            .filter { it in 1..QuranFacts.SURAH_COUNT && covered[it] < metrics.ayahCount(it) }
            .sorted()
        if (order.isEmpty()) return null
        val surahOrder = if (order.all { it in 67..114 }) order.reversed() else order
        val daily = input.dailyGoal?.takeIf { it in HifzConstants.DAILY_NEW_MIN..HifzConstants.DAILY_NEW_MAX }
            ?: HifzConstants.DAILY_NEW_DEFAULT
        return HifzPlan(
            scope = HifzScope.CUSTOM,
            surahOrder = surahOrder,
            dailyNewAyahs = daily,
            targetEpochDay = null,
            startedEpochDay = input.today,
            manzilDays = HifzConstants.MANZIL_DAYS_DEFAULT,
        )
    }
}
