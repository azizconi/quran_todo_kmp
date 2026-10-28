package tj.app.quran_todo.ui.hifz

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.presentation.review.ReviewQuality

/** Where an ayah of the reader stands in a memorize session. */
enum class HifzPhase {
    /** Not part of the session: shown as it always is, only dimmed. */
    Outside,

    /** Rated: revealed again, with its rating on the rosette. */
    Done,

    /** The ayah being recalled. The only one the person can touch. */
    Current,

    /** Waiting for its turn: hidden. */
    Upcoming,
}

/** One ayah of a session: its global number (the database key), its number in the surah and the rating it got. */
@Immutable
data class HifzItem(
    val number: Int,
    val inSurah: Int,
    val rating: ReviewQuality? = null,
)

/**
 * A memorize session: an ordered list of ayahs of one surah, the position in it and what each ayah was
 * rated. Pure data with pure transitions, so the whole flow can be unit tested and saved as text.
 *
 * [index] is the position of the ayah being recalled; it equals `items.size` once every ayah is rated.
 * [revealed] says the text of that ayah was shown and the ratings are on offer.
 */
@Immutable
data class HifzSession(
    val surah: Int,
    /** Number of ayahs in the surah (needed to keep the surah status in sync when an ayah gets one). */
    val totalInSurah: Int,
    val items: List<HifzItem>,
    val index: Int = 0,
    val revealed: Boolean = false,
    /** 1 for the first session, 2 for "repeat the hard ones" made out of it, and so on. */
    val round: Int = 1,
) {
    private val positions: Map<Int, Int> = items.withIndex().associate { (position, item) -> item.inSurah to position }

    val size: Int get() = items.size

    val finished: Boolean get() = index >= items.size

    val current: HifzItem? get() = items.getOrNull(index)

    val ratedCount: Int get() = items.count { it.rating != null }

    /** Ayahs not rated yet. */
    val remaining: Int get() = items.count { it.rating == null }

    fun count(quality: ReviewQuality): Int = items.count { it.rating == quality }

    /** Share of Good and Easy among the rated ayahs, 0..1. */
    val recallShare: Float
        get() {
            val rated = ratedCount
            if (rated == 0) return 0f
            return (count(ReviewQuality.GOOD) + count(ReviewQuality.EASY)).toFloat() / rated
        }

    /** The ayahs rated Hard or Forgot, in session order. */
    val hardItems: List<HifzItem>
        get() = items.filter { it.rating == ReviewQuality.HARD || it.rating == ReviewQuality.FORGOT }

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

    /** Rates the current ayah and moves on to the next one, hidden again. Ignored until the ayah is revealed. */
    fun rate(quality: ReviewQuality): HifzSession {
        if (finished || !revealed) return this
        val rated = items.toMutableList()
        rated[index] = rated[index].copy(rating = quality)
        return copy(items = rated, index = index + 1, revealed = false)
    }

    /** A new session out of the ayahs rated Hard or Forgot, or null when there are none. */
    fun retryHard(): HifzSession? {
        val hard = hardItems
        if (hard.isEmpty()) return null
        return HifzSession(
            surah = surah,
            totalInSurah = totalInSurah,
            items = hard.map { it.copy(rating = null) },
            round = round + 1,
        )
    }

    /** Text form for `rememberSaveable`: survives the process being killed. */
    fun encode(): String = buildString {
        append(CodecVersion).append('|')
        append(surah).append('|')
        append(totalInSurah).append('|')
        append(index).append('|')
        append(if (revealed) 1 else 0).append('|')
        append(round).append('|')
        items.forEachIndexed { position, item ->
            if (position > 0) append(',')
            append(item.number).append(':').append(item.inSurah).append(':').append(item.rating?.ordinal ?: -1)
        }
    }

    companion object {
        private const val CodecVersion = "h1"

        /** Reads [encode]'s output; null for anything else, including the empty string. */
        fun decode(text: String?): HifzSession? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split('|')
            if (parts.size != 7 || parts[0] != CodecVersion) return null
            val surah = parts[1].toIntOrNull() ?: return null
            val total = parts[2].toIntOrNull() ?: return null
            val index = parts[3].toIntOrNull() ?: return null
            val revealed = when (parts[4]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val round = parts[5].toIntOrNull() ?: return null
            if (parts[6].isEmpty()) return null
            val items = parts[6].split(',').map { entry ->
                val fields = entry.split(':')
                if (fields.size != 3) return null
                val number = fields[0].toIntOrNull() ?: return null
                val inSurah = fields[1].toIntOrNull() ?: return null
                val ordinal = fields[2].toIntOrNull() ?: return null
                val rating = if (ordinal < 0) null else ReviewQuality.entries.getOrNull(ordinal) ?: return null
                HifzItem(number, inSurah, rating)
            }
            if (index !in 0..items.size) return null
            return HifzSession(surah, total, items, index, revealed && index < items.size, round)
        }
    }
}

/** Builds the sessions the reader starts. [ayahs] must be sorted by number in the surah. */
object HifzSessions {
    /**
     * The ayahs [startInSurah]..[endInSurah] (numbers in the surah; swapped when reversed, clamped to
     * what exists). This is what a "Today" task opens. Null when no ayah falls in the range.
     */
    fun range(surah: Int, ayahs: List<AyahEntity>, startInSurah: Int, endInSurah: Int): HifzSession? {
        val low = minOf(startInSurah, endInSurah)
        val high = maxOf(startInSurah, endInSurah)
        val picked = ayahs.filter { it.numberInSurah in low..high }
        if (picked.isEmpty()) return null
        return HifzSession(surah, ayahs.size, picked.map { HifzItem(it.number, it.numberInSurah) })
    }

    /**
     * From the ayah at list index [startIndex] to the end of the surah, or to [endIndex] when the repeat
     * range is on and ends at or after the start. Null for a surah without ayahs.
     */
    fun fromAyah(surah: Int, ayahs: List<AyahEntity>, startIndex: Int, endIndex: Int? = null): HifzSession? {
        if (ayahs.isEmpty()) return null
        val last = ayahs.lastIndex
        val start = startIndex.coerceIn(0, last)
        val end = endIndex?.takeIf { it >= start }?.coerceAtMost(last) ?: last
        return HifzSession(
            surah = surah,
            totalInSurah = ayahs.size,
            items = ayahs.subList(start, end + 1).map { HifzItem(it.number, it.numberInSurah) },
        )
    }
}
