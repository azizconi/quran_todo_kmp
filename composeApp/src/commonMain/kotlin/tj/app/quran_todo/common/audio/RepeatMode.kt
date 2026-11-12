package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.settings.ReaderPracticeSettings

/** The three choices of the "Repeat" block of the player: nothing, every ayah N times, or a passage N times. */
enum class RepeatMode { None, Ayah, Passage }

/**
 * The two stored values behind the "Repeat" block: the per-ayah repeat count (`SurahReaderPreferences.repeatCount`) and the
 * passage repeat (`ReaderPracticeSettings.rangeEnabled`, `startAyah`, `endAyah`, `rangeRepeatCount`). Nothing is stored for the mode
 * itself: it is derived, so old data and the ayah sheet's "Repeat this ayah" keep working.
 */
data class RepeatState(val ayahRepeat: Int, val practice: ReaderPracticeSettings)

object RepeatModes {
    /** How many times an ayah repeats when "Ayah" is chosen while no count is set yet. */
    const val DefaultAyahCount = 3

    /** Passage wins when both are active (the passage repeat replaces the per-ayah repeat while it plays). */
    fun derive(state: RepeatState): RepeatMode = when {
        state.practice.rangeEnabled -> RepeatMode.Passage
        state.ayahRepeat != 1 -> RepeatMode.Ayah
        else -> RepeatMode.None
    }

    /**
     * Picks [mode]; the other repeat is switched off. "Passage" only clears the per-ayah repeat: the passage itself starts with its
     * own button (it also starts playing), so a passage that is set stays as it is.
     */
    fun select(state: RepeatState, mode: RepeatMode): RepeatState = when (mode) {
        RepeatMode.None -> RepeatState(1, state.practice.copy(rangeEnabled = false))
        RepeatMode.Ayah -> RepeatState(
            ayahRepeat = state.ayahRepeat.takeIf { it != 1 } ?: DefaultAyahCount,
            practice = state.practice.copy(rangeEnabled = false),
        )
        RepeatMode.Passage -> state.copy(ayahRepeat = 1)
    }

    /** A count for every ayah ("Ayah" mode); a passage repeat is switched off. */
    fun withAyahCount(state: RepeatState, count: Int): RepeatState =
        RepeatState(count, state.practice.copy(rangeEnabled = false))

    /** The passage [from]..[to] repeated [count] times; it replaces the per-ayah repeat. Also what "Repeat this ayah" sets (from == to). */
    fun withPassage(state: RepeatState, from: Int, to: Int, count: Int): RepeatState = RepeatState(
        ayahRepeat = 1,
        practice = state.practice.copy(rangeEnabled = true, startAyah = from, endAyah = to, rangeRepeatCount = count),
    )
}
