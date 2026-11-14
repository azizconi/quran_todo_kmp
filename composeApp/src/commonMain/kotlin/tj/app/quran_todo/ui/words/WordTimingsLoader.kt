package tj.app.quran_todo.ui.words

import tj.app.quran_todo.common.audio.timing.WordTimings

/**
 * The word timings of a surah (which word sounds when) for the word highlight of the reader: memory, pinned copy, cache, network.
 * Null without a copy and without network (the reader then has no highlight). Android: `WordTimingsStore`.
 */
interface WordTimingsLoader {
    suspend fun load(recitationId: Int, surah: Int): WordTimings?
}
