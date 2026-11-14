package tj.app.quran_todo.ui.lesson

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.schedule.LessonItem
import tj.app.quran_todo.hifz.schedule.LessonNew
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.ui.hifz.HifzSession

/** The three steps of the lesson, in the order they are done. */
enum class LessonBlock { New, Recent, Old }

/** One step of the header: how many cards of the block are done. */
data class LessonStep(val block: LessonBlock, val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done >= total
}

/** The card the person works on now: the new passage, or one repetition of the Recent or Old block. */
sealed interface LessonCard {
    /** Identifies the card across recompositions, rotations and a process death. */
    val key: String
    val block: LessonBlock
    val range: AyahRange

    data class New(val item: LessonNew) : LessonCard {
        override val key: String get() = "new"
        override val block: LessonBlock get() = LessonBlock.New
        override val range: AyahRange get() = AyahRange(item.surah, item.startAyah, item.endAyah)
    }

    /** [position] counts from 1 among the cards of the block, [total] is the size of the block. */
    data class Review(
        val item: LessonItem,
        override val block: LessonBlock,
        val position: Int,
        val total: Int,
    ) : LessonCard {
        override val key: String get() = "p${item.passageId}"
        override val range: AyahRange get() = AyahRange(item.surah, item.startAyah, item.endAyah)
    }
}

/** What the lesson screen derives from the [LessonPlan]. Pure, so it is unit tested. */
object LessonCards {
    /**
     * The first card that is not done: the new passage (when it is to be learned today), then the Recent block, then
     * the Old block. Null when everything is done. A new passage that was moved to the next day does not count.
     */
    fun current(plan: LessonPlan): LessonCard? {
        val new = plan.newBlock
        if (new != null && new.state == LessonNewState.PENDING) return LessonCard.New(new)
        reviewCard(plan.recent, LessonBlock.Recent)?.let { return it }
        return reviewCard(plan.old, LessonBlock.Old)
    }

    private fun reviewCard(items: List<LessonItem>, block: LessonBlock): LessonCard.Review? {
        val next = items.firstOrNull { !it.done } ?: return null
        return LessonCard.Review(next, block, position = items.count { it.done } + 1, total = items.size)
    }

    /** The steps of the header: only blocks that have something in them, in the order of the lesson. */
    fun steps(plan: LessonPlan): List<LessonStep> = buildList {
        val new = plan.newBlock
        if (new != null && new.state != LessonNewState.DEFERRED) {
            add(LessonStep(LessonBlock.New, done = if (new.state == LessonNewState.DONE) 1 else 0, total = 1))
        }
        if (plan.recent.isNotEmpty()) add(LessonStep(LessonBlock.Recent, plan.recent.count { it.done }, plan.recent.size))
        if (plan.old.isNotEmpty()) add(LessonStep(LessonBlock.Old, plan.old.count { it.done }, plan.old.size))
    }

    /** The blocks of the lesson that are done completely (and have something in them). */
    fun completeBlocks(plan: LessonPlan): Set<LessonBlock> =
        steps(plan).filter { it.complete }.mapTo(LinkedHashSet()) { it.block }

    /** "Minutes of the lesson" for the `lesson_completed` event: a bucket, never the exact number. */
    fun minutesBucket(minutes: Int): String = when {
        minutes <= 5 -> "le5"
        minutes <= 10 -> "le10"
        minutes <= 15 -> "le15"
        minutes <= 25 -> "le25"
        else -> "gt25"
    }
}

/**
 * Where inside a card the person is. A new passage goes Listen, Read, Recall; a repetition goes Intro (the card with
 * "listen first") and Recall.
 */
enum class CardPhase {
    Listen, Read, Recall, Intro;

    /** The phase after this one, or null after Recall (the card is finished by grading it). */
    fun next(): CardPhase? = when (this) {
        Listen -> Read
        Read -> Recall
        Intro -> Recall
        Recall -> null
    }

    companion object {
        fun first(card: LessonCard): CardPhase = if (card is LessonCard.New) Listen else Intro
    }
}

/**
 * The position inside the current card: its phase and, in Recall, the session with the answers so far. It lives in
 * the view model (a rotation keeps it) and is saved as text for the case the process dies; it applies only to the
 * card with the same [key].
 */
data class CardProgress(
    val key: String,
    val phase: CardPhase,
    val recall: HifzSession? = null,
) {
    fun encode(): String = listOf(CodecVersion, key, phase.ordinal.toString(), recall?.encode().orEmpty()).joinToString(Separator)

    companion object {
        private const val CodecVersion = "c1"
        private const val Separator = "~"

        fun first(card: LessonCard) = CardProgress(card.key, CardPhase.first(card))

        /** Reads [encode]'s output; null for anything else, including the empty string. */
        fun decode(text: String?): CardProgress? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split(Separator, limit = 4)
            if (parts.size != 4 || parts[0] != CodecVersion || parts[1].isEmpty()) return null
            val phase = parts[2].toIntOrNull()?.let { CardPhase.entries.getOrNull(it) } ?: return null
            val recall = if (parts[3].isEmpty()) null else HifzSession.decode(parts[3]) ?: return null
            if (phase == CardPhase.Recall && recall == null) return null
            return CardProgress(parts[1], phase, recall)
        }
    }
}

/** The sound of the "Listen" phase of a new passage. */
object ListenPlan {
    /**
     * The ayahs (numbers in the surah) in the order they are played: every ayah [ayahRepeats] times in turn, then the
     * whole passage [passageRepeats] times (each ayah once per pass). A passage of one ayah has no passes of its own
     * (the repeats of the ayah are the passage). [ayahRepeats] is at least 1.
     */
    fun sequence(startAyah: Int, endAyah: Int, ayahRepeats: Int, passageRepeats: Int): List<Int> {
        if (endAyah < startAyah) return emptyList()
        val ayahs = startAyah..endAyah
        val each = ayahs.flatMap { ayah -> List(ayahRepeats.coerceAtLeast(1)) { ayah } }
        if (startAyah == endAyah || passageRepeats <= 0) return each
        return each + List(passageRepeats) { ayahs.toList() }.flatten()
    }

    /** One pass over the passage: what "listen first" of a repetition plays. */
    fun once(startAyah: Int, endAyah: Int): List<Int> = sequence(startAyah, endAyah, ayahRepeats = 1, passageRepeats = 0)
}
