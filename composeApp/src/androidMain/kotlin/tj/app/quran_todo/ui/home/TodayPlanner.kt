package tj.app.quran_todo.ui.home

import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus

/** The kinds of work in the Today row, in the order of the cards. */
enum class TodayKind {
    /** Ayahs to learn. */
    New,

    /** Ayahs whose spaced-repetition review is due (recent and old reviews together). */
    Review,

    /** Ayahs marked weak that are not due for review. */
    Weak,
}

/**
 * One piece of work inside a single surah. [startAyah]..[endAyah] are numbers inside the surah (the
 * reader opens that range); [ayahNumbers] are the global numbers (1..6236) of the ayahs to work on.
 */
data class TodayTask(
    val kind: TodayKind,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val ayahNumbers: List<Int>,
) {
    val ayahCount: Int get() = ayahNumbers.size
}

/** All the work of one [kind]; [head] is the task a tap on the card opens. */
data class TodayCardSummary(
    val kind: TodayKind,
    val head: TodayTask,
    val totalAyahs: Int,
    val surahs: List<Int>,
)

internal class PlannerInput(
    val index: QuranIndex,
    /** Surahs marked "memorizing", ascending. */
    val learningSurahs: List<Int>,
    val ayahTodos: List<AyahTodoEntity>,
    /** Reviews that are due now, the most overdue first. */
    val dueReviews: List<AyahReviewEntity>,
    /** Keys `"surah:globalAyah"` of ayahs marked weak. */
    val weakAyahKeys: Set<String>,
    val dailyGoal: Int,
    val focusMinutes: Int,
)

/**
 * Plans the day (port of `buildTodayTasks` from the old Hifz home): a few new ayahs from the surahs
 * being memorized, the reviews that are due, and the weak ayahs. Pure: no storage, no clock.
 */
internal object TodayPlanner {
    /** A session never takes more reviews than this from one surah. */
    const val MaxReviewAyahsPerSurah = 5

    fun plan(input: PlannerInput): List<TodayTask> {
        val index = input.index
        val dueKeys = input.dueReviews.mapTo(HashSet()) { key(it.surahNumber, it.ayahNumber) }
        val weakKeys = input.weakAyahKeys
        val result = mutableListOf<TodayTask>()

        // New ayahs: first the ones already being learned (the least recently touched first), then
        // the next unlearned ayahs of the surahs marked "memorizing".
        val newAyahLimit = minOf(input.dailyGoal.coerceIn(1, 50), (input.focusMinutes / 3).coerceAtLeast(1))
        val selectedLearning = input.ayahTodos
            .filter {
                val key = key(it.surahNumber, it.ayahNumber)
                it.status == AyahTodoStatus.LEARNING && key !in dueKeys && key !in weakKeys
            }
            .sortedWith(
                compareBy(
                    { it.updatedAt },
                    { it.surahNumber },
                    { index.localAyah(it.ayahNumber) },
                ),
            )
            .take(newAyahLimit)
            .map { it.surahNumber to it.ayahNumber }
        val alreadySelected = selectedLearning.mapTo(HashSet()) { (surah, ayah) -> key(surah, ayah) }
        val todoByAyah = input.ayahTodos.associateBy { it.ayahNumber }
        // A sequence: stops as soon as the session is full instead of listing every ayah of every surah.
        val nextPlanned = input.learningSurahs
            .sorted()
            .asSequence()
            .flatMap { surah -> index.globalAyahs(surah).asSequence().map { surah to it } }
            .filter { (surah, ayah) ->
                val key = key(surah, ayah)
                key !in dueKeys &&
                    key !in weakKeys &&
                    key !in alreadySelected &&
                    todoByAyah[ayah]?.status != AyahTodoStatus.LEARNED
            }
            .take((newAyahLimit - selectedLearning.size).coerceAtLeast(0))
            .toList()

        (selectedLearning + nextPlanned)
            .groupBy({ it.first }, { it.second })
            .forEach { (surah, ayahs) ->
                result += task(index, TodayKind.New, surah, ayahs.sortedBy { index.localAyah(it) })
            }

        // Reviews: recent and old together, one task per surah.
        input.dueReviews
            .groupBy { it.surahNumber }
            .forEach { (surah, reviews) ->
                val selected = reviews
                    .map { it.ayahNumber }
                    .sortedBy { index.localAyah(it) }
                    .take(MaxReviewAyahsPerSurah)
                result += task(index, TodayKind.Review, surah, selected)
            }

        // Weak ayahs that are not already part of a review.
        input.weakAyahKeys
            .mapNotNull(::parseKey)
            .filter { (surah, ayah) -> key(surah, ayah) !in dueKeys }
            .sortedWith(compareBy({ it.first }, { index.localAyah(it.second) }))
            .groupBy({ it.first }, { it.second })
            .forEach { (surah, ayahs) ->
                result += task(index, TodayKind.Weak, surah, ayahs.take(MaxReviewAyahsPerSurah))
            }
        return result
    }

    /** One card per kind that has work, in the order New, Review, Weak. */
    fun summarize(tasks: List<TodayTask>): List<TodayCardSummary> =
        TodayKind.entries.mapNotNull { kind ->
            val ofKind = tasks.filter { it.kind == kind }
            val head = ofKind.firstOrNull() ?: return@mapNotNull null
            TodayCardSummary(
                kind = kind,
                head = head,
                totalAyahs = ofKind.sumOf { it.ayahCount },
                surahs = ofKind.map { it.surah }.distinct(),
            )
        }

    /**
     * Number of distinct ayahs worked on today: status changed ([AyahTodoEntity.updatedAt]) or
     * reviewed ([AyahReviewEntity.lastReviewedAt]) on the day [isToday] accepts.
     */
    fun countDoneToday(
        todos: List<AyahTodoEntity>,
        reviews: List<AyahReviewEntity>,
        isToday: (epochMillis: Long) -> Boolean,
    ): Int {
        val done = HashSet<Int>()
        todos.forEach { if (it.updatedAt > 0 && isToday(it.updatedAt)) done += it.ayahNumber }
        reviews.forEach { if (it.lastReviewedAt > 0 && isToday(it.lastReviewedAt)) done += it.ayahNumber }
        return done.size
    }

    private fun task(index: QuranIndex, kind: TodayKind, surah: Int, ayahs: List<Int>) = TodayTask(
        kind = kind,
        surah = surah,
        startAyah = index.localAyah(ayahs.first()),
        endAyah = index.localAyah(ayahs.last()),
        ayahNumbers = ayahs,
    )

    private fun key(surah: Int, ayah: Int) = "$surah:$ayah"

    private fun parseKey(key: String): Pair<Int, Int>? {
        val parts = key.split(":")
        val surah = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val ayah = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return surah to ayah
    }
}
