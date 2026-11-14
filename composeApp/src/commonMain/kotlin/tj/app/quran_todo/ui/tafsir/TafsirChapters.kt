package tj.app.quran_todo.ui.tafsir

import tj.app.quran_todo.common.tafsir.TafsirChapter
import tj.app.quran_todo.common.tafsir.TafsirSource

/**
 * The commentaries (tafsir), one file per source and surah: memory, then disk, then the network. Android: `TafsirStore`.
 * iOS (S2): the same with files under Caches (30 MB, least recently used first) and the compact copy of `TafsirFormat`.
 */
interface TafsirChapters {
    /** From memory or the disk; never the network. */
    fun local(tafsir: TafsirSource, surah: Int): TafsirChapter?

    /** From the device, else from the network (cached). Null when neither works. */
    suspend fun load(tafsir: TafsirSource, surah: Int, force: Boolean = false): TafsirChapter?

    fun bytes(): Long
    fun clear(): Long
}
