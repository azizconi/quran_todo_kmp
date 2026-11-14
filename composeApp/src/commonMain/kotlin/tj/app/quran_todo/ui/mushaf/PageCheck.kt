package tj.app.quran_todo.ui.mushaf

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.HifzPhase
import tj.app.quran_todo.ui.hifz.RecallGrading

/** One ayah of "Check yourself" on a page and the answer it got. */
@Immutable
data class PageCheckItem(val key: AyahKey, val mark: AyahMark? = null)

/**
 * A part of the checked ayahs that lies in one surah: [first]..[last] (numbers in the surah) and the ones of them answered "hard". A
 * page of three surahs gives three parts; each one is written to the hifz model on its own ([PageCheckPart]).
 */
data class PageCheckPart(val surah: Int, val first: Int, val last: Int, val hard: Set<Int>)

/**
 * "Check yourself" on a page of the mushaf (task F3a): the ayahs of the page from the one the person picked (or the first) to the
 * last one, hidden, recalled one at a time ("Show", then "I remember" / "Hard"). Pure data with pure transitions, like [tj.app.quran_todo.ui.hifz.HifzSession]
 * of the text reader, so the whole flow is tested without a screen; the answers are only collected here, the result is written once at
 * the end: for every surah part of the checked ayahs [PageCheckPart], through the same `CheckRouting.choose` as the reader (B2).
 *
 * [index] is the position of the ayah being recalled; it equals `items.size` once every ayah is answered. [revealed] says its text is
 * shown and the two answers are on offer.
 */
@Immutable
data class PageCheck(
    val page: Int,
    val items: List<PageCheckItem>,
    val index: Int = 0,
    val revealed: Boolean = false,
) {
    private val positions: Map<AyahKey, Int> = items.withIndex().associate { (position, item) -> item.key to position }

    val size: Int get() = items.size

    val finished: Boolean get() = index >= items.size

    val current: PageCheckItem? get() = items.getOrNull(index)

    /** Ayahs answered so far. */
    val markedCount: Int get() = items.count { it.mark != null }

    /** The ayahs answered "hard". */
    val hardAyahs: Set<AyahKey> get() = items.filter { it.mark == AyahMark.HARD }.mapTo(LinkedHashSet()) { it.key }

    /** The grade the sheet at the end starts with, by the rule of the text reader. */
    val suggestedGrade: ReviewGrade get() = RecallGrading.suggest(size, hardAyahs.size)

    fun phaseOf(key: AyahKey): HifzPhase {
        val position = positions[key] ?: return HifzPhase.Outside
        return when {
            position < index -> HifzPhase.Done
            position == index -> HifzPhase.Current
            else -> HifzPhase.Upcoming
        }
    }

    fun markOf(key: AyahKey): AyahMark? = positions[key]?.let { items[it].mark }

    fun reveal(): PageCheck = if (finished || revealed) this else copy(revealed = true)

    /** Marks the ayah being recalled and moves on to the next, hidden again. Ignored until the ayah is revealed. */
    fun mark(mark: AyahMark): PageCheck {
        if (finished || !revealed) return this
        val marked = items.toMutableList()
        marked[index] = marked[index].copy(mark = mark)
        return copy(items = marked, index = index + 1, revealed = false)
    }

    /** The checked ayahs cut by surah, with the hard ones of [hard] (the choice of the grade sheet) inside each part. */
    fun parts(hard: Set<AyahKey> = hardAyahs): List<PageCheckPart> {
        val result = ArrayList<PageCheckPart>()
        var start = 0
        while (start < items.size) {
            val surah = items[start].key.surah
            var end = start
            while (end + 1 < items.size && items[end + 1].key.surah == surah) end++
            val keys = items.subList(start, end + 1).map { it.key }
            result += PageCheckPart(surah, keys.first().ayah, keys.last().ayah, keys.filter { it in hard }.mapTo(LinkedHashSet()) { it.ayah })
            start = end + 1
        }
        return result
    }

    companion object {
        /**
         * The check of [page] from [start] (the ayah the person picked; the first ayah of the page when it is null or not on the page) to
         * the last ayah of the page. Null for a page without ayahs.
         */
        fun of(page: MushafPage, start: AyahKey?): PageCheck? {
            val ayahs = page.ayahs
            if (ayahs.isEmpty()) return null
            val from = ayahs.indexOf(start).coerceAtLeast(0)
            return PageCheck(page.number, ayahs.subList(from, ayahs.size).map { PageCheckItem(it) })
        }
    }
}
