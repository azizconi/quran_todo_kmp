package tj.app.quran_todo.ui.words

import java.io.File
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.httpAudioFetch
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.common.words.WordMeaningsChapter
import tj.app.quran_todo.common.words.WordMeaningsFormat
import tj.app.quran_todo.common.words.WordMeaningsPage
import tj.app.quran_todo.data.translation.FailureThrottle
import tj.app.quran_todo.ui.tafsir.ChapterCache

/**
 * Where the word-by-word meanings of a surah come from; the store asks this interface only, so the source can be replaced (see
 * [tj.app.quran_todo.ui.tafsir.TafsirChapterSource] for the terms of the Content API).
 */
internal interface WordMeaningsSource {
    /** The meanings of every word of [surah], or null when they could not be fetched completely or are not understood. */
    suspend fun fetch(surah: Int): WordMeaningsChapter?
}

/**
 * The source of quran.com: `verses/by_chapter/{surah}?words=true&fields=verse_key&per_page=50&page={n}`, page after page (surah 2 is six
 * pages of about 230 KB; the answer has the words in English whatever language is asked for, so none is asked for). Only on the first tap
 * on a word in a surah; the answer is cut down to the compact copy of [WordMeaningsFormat] right away. [scratch] holds the raw answer.
 */
internal class QuranComWordMeaningsSource(
    private val scratch: File,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val apiBase: String = Constants.QURAN_COM_API_BASE_URL,
) : WordMeaningsSource {
    fun pageUrl(surah: Int, page: Int): String = "${apiBase}verses/by_chapter/$surah?words=true&fields=verse_key&per_page=$PER_PAGE&page=$page"

    override suspend fun fetch(surah: Int): WordMeaningsChapter? {
        scratch.mkdirs()
        val raw = File(scratch, "words_$surah.raw")
        val pages = ArrayList<WordMeaningsPage>()
        try {
            var next: Int? = 1
            while (next != null) {
                if (pages.size >= MAX_PAGES) return null
                raw.delete()
                if (!fetch(pageUrl(surah, next), raw)) return null
                val page = WordMeaningsFormat.parsePage(raw.readText(Charsets.UTF_8), surah) ?: return null
                pages += page
                next = page.nextPage?.takeIf { it > next!! }
            }
        } finally {
            raw.delete()
        }
        return WordMeaningsFormat.assemble(surah, pages)
    }

    private companion object {
        const val PER_PAGE = 50
        const val MAX_PAGES = 12
    }
}

/**
 * The meanings of the words, one compact file per surah (`word_meanings/words_{surah}.json`), 12 MB at most (the whole Quran is about
 * 10 MB, the least recently used goes first), the last [MEMORY_SURAHS] in memory. Memory, disk, network ([ChapterCache]).
 */
internal class WordMeaningsStore(
    directory: File,
    private val source: WordMeaningsSource,
    throttle: FailureThrottle = FailureThrottle(),
    limitBytes: Long = CACHE_LIMIT_BYTES,
    now: () -> Long = System::currentTimeMillis,
) {
    private val cache = ChapterCache<WordMeaningsChapter>(
        directory = directory,
        limitBytes = limitBytes,
        memoryEntries = MEMORY_SURAHS,
        encode = WordMeaningsFormat::encode,
        decode = { key, text -> key.removePrefix("words_").toIntOrNull()?.let { WordMeaningsFormat.decode(text, it) } },
        throttle = throttle,
        now = now,
    )

    private fun keyOf(surah: Int) = "words_$surah"

    /** From memory or the disk; never the network. */
    fun local(surah: Int): WordMeaningsChapter? = cache.local(keyOf(surah))

    /** The meanings: from the device, else from the network (cached). Null when neither works. */
    suspend fun load(surah: Int, force: Boolean = false): WordMeaningsChapter? =
        cache.load(keyOf(surah), force) { source.fetch(surah) }

    fun bytes(): Long = cache.bytes()

    fun clear(): Long = cache.clear()

    companion object {
        const val MEMORY_SURAHS = 3
        const val CACHE_LIMIT_BYTES = 12L * 1024 * 1024

        val shared: WordMeaningsStore by lazy {
            val directory = File(AndroidContextHolder.context.noBackupFilesDir, "word_meanings")
            WordMeaningsStore(directory = directory, source = QuranComWordMeaningsSource(scratch = directory))
        }
    }
}
