package tj.app.quran_todo.ui.progress

import tj.app.quran_todo.common.settings.parseAyahKey
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.home.QuranMeta
import kotlin.math.roundToInt

/** Where one surah stands: its state and how many ayahs are memorized / in work. */
internal data class SurahProgress(
    val number: Int,
    val ayahCount: Int,
    val state: MapState,
    /** Memorized ayahs; [ayahCount] for a memorized surah. */
    val learnedAyahs: Int,
    /** Ayahs being memorized. Zero for a memorized surah (they are already counted as learned). */
    val learningAyahs: Int,
) {
    /** Memorized ayahs / all ayahs of the surah, 0..1. */
    val fraction: Float
        get() = if (ayahCount > 0) (learnedAyahs.toFloat() / ayahCount).coerceIn(0f, 1f) else 0f
}

/** The totals of the ring and the tiles; computed from [SurahProgress], so nothing is counted twice. */
internal data class ProgressTotals(
    val learnedAyahs: Int,
    val learningAyahs: Int,
    val totalAyahs: Int,
    val learnedSurahs: Int,
    val learningSurahs: Int,
) {
    /** Memorized / all ayahs, 0..1. */
    val fraction: Float
        get() = if (totalAyahs > 0) (learnedAyahs.toFloat() / totalAyahs).coerceIn(0f, 1f) else 0f

    /**
     * Whole percent, rounded to the nearest. Honest at the edges: 0 only when nothing is memorized,
     * 100 only when every ayah is.
     */
    val percent: Int
        get() = when {
            learnedAyahs <= 0 || totalAyahs <= 0 -> 0
            learnedAyahs >= totalAyahs -> 100
            else -> (learnedAyahs * 100f / totalAyahs).roundToInt().coerceIn(0, 99)
        }

    /** Something is memorized, yet it rounds to 0%: the screen shows "<1" instead. */
    val belowOnePercent: Boolean get() = learnedAyahs > 0 && percent == 0

    /** Nothing memorized and nothing in work. */
    val isUntouched: Boolean
        get() = learnedAyahs == 0 && learningAyahs == 0 && learnedSurahs == 0 && learningSurahs == 0
}

/** A surah with weak ayahs; [ayahs] are numbers inside the surah, ascending and without repeats. */
internal data class WeakSurah(val surah: Int, val ayahs: List<Int>)

/** The pure arithmetic of the progress screen (no storage, no clock), covered by unit tests. */
internal object ProgressCalculator {
    /** Ayahs per surah in the Hafs numbering, index 0 = surah 1. */
    val StandardAyahCounts: IntArray = IntArray(QuranMeta.SurahCount) { QuranMeta.ayahCount(it + 1) }

    /** Days shown by the activity chart. */
    const val ActivityDays = 14

    /**
     * State of every surah.
     *
     * - A memorized surah counts all its ayahs as memorized, whatever the ayah table says about it:
     *   marking the surah and marking its ayahs never add up.
     * - Otherwise memorized ayahs are the ayahs with status LEARNED (each ayah once).
     * - A surah whose ayahs are all memorized is memorized too.
     * - A surah marked "memorizing", or with any ayah marked, is in progress.
     */
    fun surahProgress(
        statuses: Map<Int, SurahTodoStatus>,
        ayahTodos: List<AyahTodoEntity>,
        ayahCounts: IntArray = StandardAyahCounts,
    ): List<SurahProgress> {
        val learned = IntArray(ayahCounts.size + 1)
        val learning = IntArray(ayahCounts.size + 1)
        val seen = HashSet<Int>(ayahTodos.size * 2)
        for (todo in ayahTodos) {
            if (todo.surahNumber !in 1..ayahCounts.size) continue
            if (!seen.add(todo.ayahNumber)) continue
            when (todo.status) {
                AyahTodoStatus.LEARNED -> learned[todo.surahNumber]++
                AyahTodoStatus.LEARNING -> learning[todo.surahNumber]++
            }
        }
        return List(ayahCounts.size) { index ->
            val number = index + 1
            val count = ayahCounts[index]
            val ownLearned = minOf(learned[number], count)
            val ownLearning = minOf(learning[number], count - ownLearned)
            when {
                statuses[number] == SurahTodoStatus.LEARNED || (count > 0 && ownLearned >= count) ->
                    SurahProgress(number, count, MapState.Learned, learnedAyahs = count, learningAyahs = 0)

                statuses[number] == SurahTodoStatus.LEARNING || ownLearned + ownLearning > 0 ->
                    SurahProgress(number, count, MapState.Learning, learnedAyahs = ownLearned, learningAyahs = ownLearning)

                else -> SurahProgress(number, count, MapState.NotStarted, learnedAyahs = 0, learningAyahs = 0)
            }
        }
    }

    fun totals(surahs: List<SurahProgress>): ProgressTotals = ProgressTotals(
        learnedAyahs = surahs.sumOf { it.learnedAyahs },
        learningAyahs = surahs.sumOf { it.learningAyahs },
        totalAyahs = surahs.sumOf { it.ayahCount },
        learnedSurahs = surahs.count { it.state == MapState.Learned },
        learningSurahs = surahs.count { it.state == MapState.Learning },
    )

    /**
     * Weak ayahs by surah. [keys] are the stored `"surah:globalAyah"` strings; the global number
     * (1..6236) is turned into the number inside the surah. Keys that do not name an ayah of that
     * surah are dropped, repeats counted once. The surah with most weak ayahs comes first.
     */
    fun weakSpots(keys: Set<String>, ayahCounts: IntArray = StandardAyahCounts): List<WeakSurah> {
        val offsets = offsets(ayahCounts)
        val bySurah = HashMap<Int, java.util.TreeSet<Int>>()
        for (key in keys) {
            val parsed = parseAyahKey(key) ?: continue
            val surah = parsed.surahNumber
            if (surah !in 1..ayahCounts.size) continue
            val local = parsed.ayahNumber - offsets[surah - 1]
            if (local !in 1..ayahCounts[surah - 1]) continue
            bySurah.getOrPut(surah) { java.util.TreeSet() }.add(local)
        }
        return bySurah
            .map { (surah, ayahs) -> WeakSurah(surah, ayahs.toList()) }
            .sortedWith(compareByDescending<WeakSurah> { it.ayahs.size }.thenBy { it.surah })
    }

    /**
     * Ayahs worked on per local day (as [epochDayOf] sees it): ayahs whose status changed
     * ([AyahTodoEntity.updatedAt]) or that were reviewed ([AyahReviewEntity.lastReviewedAt]) that day.
     * An ayah that was both counts once, like the "done today" counter of the home screen.
     */
    fun activityByDay(
        ayahTodos: List<AyahTodoEntity>,
        reviews: List<AyahReviewEntity>,
        epochDayOf: (epochMillis: Long) -> Int,
    ): Map<Int, Int> {
        val ayahsByDay = HashMap<Int, HashSet<Int>>()
        for (todo in ayahTodos) {
            if (todo.updatedAt > 0) ayahsByDay.getOrPut(epochDayOf(todo.updatedAt)) { HashSet() }.add(todo.ayahNumber)
        }
        for (review in reviews) {
            if (review.lastReviewedAt > 0) {
                ayahsByDay.getOrPut(epochDayOf(review.lastReviewedAt)) { HashSet() }.add(review.ayahNumber)
            }
        }
        return ayahsByDay.mapValues { it.value.size }
    }

    /** Counts of the [days] days ending [today], oldest first. */
    fun lastDays(activity: Map<Int, Int>, today: Int, days: Int = ActivityDays): List<ActivityDayUi> =
        (days - 1 downTo 0).map { back -> (today - back).let { day -> ActivityDayUi(day, activity[day] ?: 0) } }

    /**
     * Consecutive days with activity. A streak is still alive when today has no activity yet: it counts
     * back from yesterday, so it does not read 0 every morning.
     */
    fun streak(activity: Map<Int, Int>, today: Int): Int {
        var day = if ((activity[today] ?: 0) > 0) today else today - 1
        var days = 0
        while ((activity[day] ?: 0) > 0) {
            days++
            day--
        }
        return days
    }

    /** Ayahs before each surah: `offsets[0] = 0`, `offsets[1] = 7`, ... */
    internal fun offsets(ayahCounts: IntArray): IntArray {
        val result = IntArray(ayahCounts.size)
        var sum = 0
        ayahCounts.forEachIndexed { index, count ->
            result[index] = sum
            sum += count
        }
        return result
    }
}
