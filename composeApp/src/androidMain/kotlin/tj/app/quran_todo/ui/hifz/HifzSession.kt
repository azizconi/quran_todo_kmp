package tj.app.quran_todo.ui.hifz

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.hifz.model.ReviewGrade

/** Where an ayah of the reader stands in a recall session. */
enum class HifzPhase {
    /** Not part of the session: shown as it always is, only dimmed. */
    Outside,

    /** Answered: revealed again, with its mark on the rosette. */
    Done,

    /** The ayah being recalled. The only one the person can touch. */
    Current,

    /** Waiting for its turn: hidden. */
    Upcoming,
}

/** The answer to one ayah: "I remember it" or "it was hard". The grade of the whole passage is chosen at the end. */
enum class AyahMark { REMEMBERED, HARD }

/** One ayah of a session: its global number (the database key), its number in the surah and the mark it got. */
@Immutable
data class HifzItem(
    val number: Int,
    val inSurah: Int,
    val mark: AyahMark? = null,
)

/**
 * A recall session ("Recall" in the lesson, "Check yourself" in the reader): an ordered list of ayahs of one
 * surah, the position in it and what each ayah was answered. Pure data with pure transitions, so the whole flow can
 * be unit tested and saved as text.
 *
 * [index] is the position of the ayah being recalled; it equals `items.size` once every ayah is answered.
 * [revealed] says the text of that ayah was shown and the two answers are on offer. Nothing is written anywhere until
 * the end: the session only collects marks, and the grade of the passage is chosen on the sheet that follows
 * ([suggestedGrade] is what that sheet starts with).
 */
@Immutable
data class HifzSession(
    val surah: Int,
    val items: List<HifzItem>,
    val index: Int = 0,
    val revealed: Boolean = false,
) {
    private val positions: Map<Int, Int> = items.withIndex().associate { (position, item) -> item.inSurah to position }

    val size: Int get() = items.size

    val finished: Boolean get() = index >= items.size

    val current: HifzItem? get() = items.getOrNull(index)

    /** Ayahs answered so far. */
    val markedCount: Int get() = items.count { it.mark != null }

    /** Ayahs not answered yet. */
    val remaining: Int get() = items.count { it.mark == null }

    /** The ayahs answered "hard", as numbers in the surah. */
    val hardAyahs: Set<Int> get() = items.filter { it.mark == AyahMark.HARD }.mapTo(LinkedHashSet()) { it.inSurah }

    /** The grade the passage is offered with: see [RecallGrading.suggest]. */
    val suggestedGrade: ReviewGrade get() = RecallGrading.suggest(size, hardAyahs.size)

    /** First and last ayah of the session (numbers in the surah). */
    val firstAyah: Int get() = items.first().inSurah
    val lastAyah: Int get() = items.last().inSurah

    /** Position in the session of the ayah with this number in the surah, or null when it is not part of it. */
    fun positionOf(inSurah: Int): Int? = positions[inSurah]

    fun phaseOf(inSurah: Int): HifzPhase {
        val position = positions[inSurah] ?: return HifzPhase.Outside
        return when {
            position < index -> HifzPhase.Done
            position == index -> HifzPhase.Current
            else -> HifzPhase.Upcoming
        }
    }

    fun itemOf(inSurah: Int): HifzItem? = positions[inSurah]?.let(items::get)

    fun reveal(): HifzSession = if (finished || revealed) this else copy(revealed = true)

    /** Marks the current ayah and moves on to the next one, hidden again. Ignored until the ayah is revealed. */
    fun mark(mark: AyahMark): HifzSession {
        if (finished || !revealed) return this
        val marked = items.toMutableList()
        marked[index] = marked[index].copy(mark = mark)
        return copy(items = marked, index = index + 1, revealed = false)
    }

    /** Text form for `rememberSaveable`: survives the process being killed. */
    fun encode(): String = buildString {
        append(CodecVersion).append('|')
        append(surah).append('|')
        append(index).append('|')
        append(if (revealed) 1 else 0).append('|')
        items.forEachIndexed { position, item ->
            if (position > 0) append(',')
            append(item.number).append(':').append(item.inSurah).append(':').append(item.mark?.ordinal ?: -1)
        }
    }

    companion object {
        private const val CodecVersion = "h2"

        /** Reads [encode]'s output; null for anything else, including the empty string. */
        fun decode(text: String?): HifzSession? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split('|')
            if (parts.size != 5 || parts[0] != CodecVersion) return null
            val surah = parts[1].toIntOrNull() ?: return null
            val index = parts[2].toIntOrNull() ?: return null
            val revealed = when (parts[3]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            if (parts[4].isEmpty()) return null
            val items = parts[4].split(',').map { entry ->
                val fields = entry.split(':')
                if (fields.size != 3) return null
                val number = fields[0].toIntOrNull() ?: return null
                val inSurah = fields[1].toIntOrNull() ?: return null
                val ordinal = fields[2].toIntOrNull() ?: return null
                val mark = if (ordinal < 0) null else AyahMark.entries.getOrNull(ordinal) ?: return null
                HifzItem(number, inSurah, mark)
            }
            if (index !in 0..items.size) return null
            return HifzSession(surah, items, index, revealed && index < items.size)
        }
    }
}

/** Builds the sessions the reader and the lesson start. [ayahs] must be sorted by number in the surah. */
object HifzSessions {
    /**
     * The ayahs [startInSurah]..[endInSurah] (numbers in the surah; swapped when reversed, clamped to
     * what exists). This is what a lesson card or a `Dest.Reader(hifz = true)` opens. Null when no ayah falls in the
     * range.
     */
    fun range(surah: Int, ayahs: List<AyahEntity>, startInSurah: Int, endInSurah: Int): HifzSession? {
        val low = minOf(startInSurah, endInSurah)
        val high = maxOf(startInSurah, endInSurah)
        val picked = ayahs.filter { it.numberInSurah in low..high }
        if (picked.isEmpty()) return null
        return HifzSession(surah, picked.map { HifzItem(it.number, it.numberInSurah) })
    }

    /** The longest session "check yourself from here" makes when no passage end is given. */
    const val MaxFromHere = 10

    /**
     * From the ayah at list index [startIndex] on: to [endIndex] when the repeat range is on and ends at or after the
     * start, otherwise at most [MaxFromHere] ayahs (a recorded check of a whole long surah would not be a check any
     * more). Null for a surah without ayahs.
     */
    fun fromAyah(surah: Int, ayahs: List<AyahEntity>, startIndex: Int, endIndex: Int? = null): HifzSession? {
        if (ayahs.isEmpty()) return null
        val last = ayahs.lastIndex
        val start = startIndex.coerceIn(0, last)
        val end = endIndex?.takeIf { it >= start }?.coerceAtMost(last) ?: minOf(last, start + MaxFromHere - 1)
        return HifzSession(
            surah = surah,
            items = ayahs.subList(start, end + 1).map { HifzItem(it.number, it.numberInSurah) },
        )
    }
}
