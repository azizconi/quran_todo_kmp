package tj.app.quran_todo.ui.drill

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.ui.hifz.AyahMark

/** One difficult ayah in the queue of the drill: [ayah] is its number in the surah. */
@Immutable
data class DrillItem(val surah: Int, val ayah: Int)

/** What one surah's group of the drill writes to `HifzRepository.drillHard`. */
@Immutable
data class DrillWrite(val surah: Int, val remembered: Set<Int>, val stillHard: Set<Int>)

/** The queue of a drill: [items] in the order of the mushaf (at most [DrillQueues.Limit]), and [more] that did not fit. */
@Immutable
data class DrillQueue(val items: List<DrillItem>, val more: Int)

object DrillQueues {
    /** A drill takes at most this many ayahs; the rest waits for the next time. */
    const val Limit = 20

    /**
     * The queue of [hard] (`HifzRepository.hardAyahs()`: surah -> difficult ayahs in the surah): the surahs in the order of the mushaf and
     * the ayahs of each in ascending order, at most [limit]; [DrillQueue.more] counts the ones that are left out.
     */
    fun build(hard: Map<Int, Set<Int>>, limit: Int = Limit): DrillQueue {
        val all = hard.keys.sorted().flatMap { surah -> hard.getValue(surah).sorted().map { DrillItem(surah, it) } }
        return DrillQueue(items = all.take(limit), more = (all.size - limit).coerceAtLeast(0))
    }

    /** "Number of ayahs of the drill" for the `drill_completed` event: a bucket, never the exact number. */
    fun countBucket(count: Int): String = when {
        count <= 3 -> "le3"
        count <= 7 -> "le7"
        count <= 12 -> "le12"
        else -> "gt12"
    }
}

/**
 * Where a drill is: the queue, the ayah being recalled ([index]) and the answers so far. Pure, so the whole flow is unit tested.
 *
 * The answers of one surah's group are written together ([DrillWrite]) when its last ayah is answered; [pending] are the answers of the
 * group that is still going, written when the person leaves early.
 */
@Immutable
data class DrillState(
    val queue: DrillQueue,
    val index: Int = 0,
    /** The answers to `items[0 until index]`, in order. */
    val answers: List<AyahMark> = emptyList(),
    /** How many answers are already written (the groups that are closed). */
    val written: Int = 0,
) {
    val items: List<DrillItem> get() = queue.items

    val total: Int get() = items.size

    val finished: Boolean get() = index >= items.size

    val current: DrillItem? get() = items.getOrNull(index)

    /** The ayah of the same surah that comes before [current] in the surah, or null for the first ayah of a surah (no hint to start with). */
    val previousAyah: Int? get() = current?.ayah?.takeIf { it > 1 }?.minus(1)

    val remembered: Int get() = answers.count { it == AyahMark.REMEMBERED }

    val stillHard: Int get() = answers.count { it == AyahMark.HARD }

    /** The result of answering [current] with [mark]: the new state and, when this closed the group of its surah, what to write. */
    fun answer(mark: AyahMark): Step {
        val item = current ?: return Step(this, null)
        val next = copy(index = index + 1, answers = answers + mark)
        val closes = items.getOrNull(index + 1)?.surah != item.surah
        return if (closes) {
            val write = next.groupWrite(from = written, until = next.index)
            Step(next.copy(written = next.index), write)
        } else {
            Step(next, null)
        }
    }

    /** The answers of the group that is still going (already answered, not yet written): null when there are none. */
    fun pending(): DrillWrite? = if (index > written) groupWrite(from = written, until = index) else null

    /** Marks the pending answers as written (the person left early and they were saved). */
    fun flushed(): DrillState = copy(written = index)

    private fun groupWrite(from: Int, until: Int): DrillWrite? {
        if (until <= from) return null
        val surah = items[from].surah
        val remembered = LinkedHashSet<Int>()
        val stillHard = LinkedHashSet<Int>()
        for (position in from until until) {
            val item = items[position]
            if (item.surah != surah) continue
            if (answers[position] == AyahMark.REMEMBERED) remembered += item.ayah else stillHard += item.ayah
        }
        return DrillWrite(surah, remembered, stillHard)
    }

    /** What [answer] did. */
    class Step(val state: DrillState, val write: DrillWrite?)
}
