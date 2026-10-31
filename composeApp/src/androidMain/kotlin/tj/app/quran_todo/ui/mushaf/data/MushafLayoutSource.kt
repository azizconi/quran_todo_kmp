package tj.app.quran_todo.ui.mushaf.data

import java.io.File
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.httpAudioFetch
import tj.app.quran_todo.common.mushaf.MushafLayoutFormat
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.common.utils.Constants

/**
 * Where the layout of a page (its lines and words) and the font that draws it come from. The store ([MushafPageStore]) asks this
 * interface and nothing else, so the source can be replaced (Quran Foundation has new terms for developers and OAuth keys for its Content
 * API from 2026-08-10; the open endpoint below still answers without a key, see `docs/redesign/API.md`).
 */
internal interface MushafLayoutSource {
    /** The layout of [page] (1..604), or null when it could not be fetched or is not understood. One request. */
    suspend fun layout(page: Int): MushafPage?

    /** Writes the font of [page] to [target]; false when it could not be fetched (a half-written file is the caller's to delete). */
    suspend fun font(page: Int, target: File): Boolean
}

/**
 * The source of quran.com: the layout from the open API (`verses/by_page`, `code_v1` and `line_number` of every word), the fonts of the
 * pages (KFGQPC, King Fahd Glorious Quran Printing Complex, version 1, `p{page}.ttf`) from `verses.quran.com`. A page is one request for
 * the layout and one for the font; nothing polls.
 *
 * [scratch] is a folder for the raw answer while it is read.
 */
internal class QuranComMushafSource(
    private val scratch: File,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val apiBase: String = Constants.QURAN_COM_API_BASE_URL,
    private val fontsBase: String = FONTS_BASE_URL,
) : MushafLayoutSource {
    fun layoutUrl(page: Int): String =
        "${apiBase}verses/by_page/$page?words=true&word_fields=code_v1,line_number,page_number&per_page=50&fields=chapter_id"

    fun fontUrl(page: Int): String = "${fontsBase}p$page.ttf"

    override suspend fun layout(page: Int): MushafPage? {
        scratch.mkdirs()
        val raw = File(scratch, "layout_p$page.raw")
        raw.delete()
        return try {
            if (!fetch(layoutUrl(page), raw)) return null
            MushafLayoutFormat.parseQuranCom(raw.readText(Charsets.UTF_8), page)
        } finally {
            raw.delete()
        }
    }

    override suspend fun font(page: Int, target: File): Boolean = fetch(fontUrl(page), target)

    companion object {
        const val FONTS_BASE_URL = "https://verses.quran.com/fonts/quran/hafs/v1/ttf/"
    }
}
