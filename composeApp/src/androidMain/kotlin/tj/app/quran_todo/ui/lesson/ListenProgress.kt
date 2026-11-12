package tj.app.quran_todo.ui.lesson

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.audio.ReaderAudioState
import tj.app.quran_todo.ui.i18n.LessonListenCopy

/** The Listen sequence that was started: what [ListenPlan.sequence] was built from. */
data class ListenRun(val surah: Int, val ayahCount: Int, val ayahRepeats: Int, val passageRepeats: Int) {
    /** The number of steps (readings) of the sequence. */
    val length: Int get() = ListenPlan.sequence(1, ayahCount, ayahRepeats, passageRepeats).size
}

/** Where in the Listen sequence a step is: one of the repeats of an ayah, or one of the passes over the whole passage. */
sealed interface ListenPlace {
    /** [ayah] of [ayahs] (1-based, in the passage), [repeat] of [repeats]. */
    data class Ayah(val ayah: Int, val ayahs: Int, val repeat: Int, val repeats: Int) : ListenPlace

    /** [pass] of [passes] over the whole passage. */
    data class Passage(val pass: Int, val passes: Int) : ListenPlace

    fun label(copy: LessonListenCopy): String = when (this) {
        is Ayah -> copy.ayahProgress(ayah, ayahs, repeat, repeats)
        is Passage -> copy.passageProgress(pass, passes)
    }
}

/** The place of step [position] (0-based) of the sequence of [run]; null outside it. Follows [ListenPlan.sequence]. */
fun ListenPlan.place(run: ListenRun, position: Int): ListenPlace? {
    val repeats = run.ayahRepeats.coerceAtLeast(1)
    val each = run.ayahCount * repeats
    return when {
        position < 0 || run.ayahCount <= 0 -> null
        position < each -> ListenPlace.Ayah(position / repeats + 1, run.ayahCount, position % repeats + 1, repeats)
        position < run.length -> ListenPlace.Passage((position - each) / run.ayahCount + 1, run.passageRepeats)
        else -> null
    }
}

/**
 * How far the Listen sequence has got. [fraction] (0..1) covers the whole sequence; [done] says it has been played to its end
 * by itself, the last step included.
 */
@Immutable
data class ListenProgress(
    val place: ListenPlace?,
    val position: Int,
    val length: Int,
    val fraction: Float,
    val done: Boolean = false,
) {
    val atLastStep: Boolean get() = length > 0 && position == length - 1

    companion object {
        /**
         * The progress after [state], given the [previous] one. Null when there is no run, the sound belongs to another surah or the
         * sequence is not the one of [run] (a single ayah, "listen first"). A sequence that stops after its last step, without an
         * error, is done; the stop is recognised from the previous progress, because the player settings that are put back at the
         * end reset the session (and its own "done" mark) right after the last step.
         */
        fun next(previous: ListenProgress?, run: ListenRun?, nowPlayingSurah: Int?, state: ReaderAudioState): ListenProgress? {
            if (run == null) return null
            if (previous?.done == true) return previous
            if (nowPlayingSurah != run.surah) return null
            val length = run.length
            val active = state.isPlaying || state.canResume || state.isPreparing
            if (active) {
                if (state.scriptLength != length || state.scriptPosition !in 0 until length) return null
                val inside = if (state.isPreparing || state.durationMs <= 0) 0f
                else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                return ListenProgress(
                    place = ListenPlan.place(run, state.scriptPosition),
                    position = state.scriptPosition,
                    length = length,
                    fraction = ((state.scriptPosition + inside) / length).coerceIn(0f, 1f),
                )
            }
            if (previous == null) return null
            return if (previous.atLastStep && !state.hasError) previous.copy(fraction = 1f, done = true) else null
        }
    }
}
