package tj.app.quran_todo.ui.tafsir

import java.io.File
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.httpAudioFetch
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.tafsir.TafsirChapter
import tj.app.quran_todo.common.tafsir.TafsirFormat
import tj.app.quran_todo.common.tafsir.TafsirPage
import tj.app.quran_todo.common.tafsir.TafsirProvider
import tj.app.quran_todo.common.tafsir.TafsirSource
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.translation.FailureThrottle

/**
 * Where the commentary of a surah comes from. The store ([TafsirStore]) asks this interface and nothing else, so the source can be
 * replaced (Quran Foundation has new terms for developers and OAuth keys for its Content API from 2026-08-10; the open endpoint below
 * still answers without a key, see `docs/redesign/API.md`).
 */
internal interface TafsirChapterSource {
    /** The whole commentary [source] of [surah], or null when it could not be fetched completely or is not understood. */
    suspend fun fetch(source: TafsirSource, surah: Int): TafsirChapter?
}

/**
 * The source of quran.com: `tafsirs/{id}/by_chapter/{surah}?per_page=50&page={n}`, page after page until `next_page` is empty (the
 * longest surah, 2, is six pages, about 1 MB for as-Sa'di and 1.3 MB for Ibn Kathir). Requests are made only when a person opens a
 * commentary, nothing is fetched ahead. [scratch] is a folder for the raw answer while it is read.
 */
internal class QuranComTafsirSource(
    private val scratch: File,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val apiBase: String = Constants.QURAN_COM_API_BASE_URL,
) : TafsirChapterSource {
    fun pageUrl(source: TafsirSource, surah: Int, page: Int): String =
        "${apiBase}tafsirs/${source.remoteId}/by_chapter/$surah?per_page=$PER_PAGE&page=$page"

    override suspend fun fetch(source: TafsirSource, surah: Int): TafsirChapter? {
        scratch.mkdirs()
        val raw = File(scratch, "tafsir_${source.key}_$surah.raw")
        val pages = ArrayList<TafsirPage>()
        try {
            var next: Int? = 1
            while (next != null) {
                if (pages.size >= MAX_PAGES) return null
                raw.delete()
                if (!fetch(pageUrl(source, surah, next), raw)) return null
                val page = TafsirFormat.parsePage(raw.readText(Charsets.UTF_8), surah) ?: return null
                pages += page
                next = page.nextPage?.takeIf { it > next!! }
            }
        } finally {
            raw.delete()
        }
        return TafsirFormat.assemble(surah, source, pages, QuranFacts.ayahCount(surah))
    }

    private companion object {
        const val PER_PAGE = 50
        const val MAX_PAGES = 12
    }
}

/**
 * The source of QuranEnc.com (the Mukhtasar, "Al-Mukhtasar fi Tafsir al-Quran", of the Center for Tafsir Studies): `GET
 * {base}translation/sura/{key}/{surah}`, one request per surah (surah 2 in Turkish is 136 000 characters). The text is kept as it is: the
 * terms of QuranEnc allow no change, addition or removal. The edition number comes from `GET {base}translations/list` (`version` of the
 * entry with the key) when the list has the key; the Mukhtasar editions are not in that list on 2026-10-31, so the version is then
 * null and the screen names the source without a number.
 */
internal class QuranEncTafsirSource(
    private val scratch: File,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val apiBase: String = QURAN_ENC_API_BASE_URL,
) : TafsirChapterSource {
    fun suraUrl(source: TafsirSource, surah: Int): String = "${apiBase}translation/sura/${source.remoteId}/$surah"

    fun listUrl(): String = "${apiBase}translations/list"

    override suspend fun fetch(source: TafsirSource, surah: Int): TafsirChapter? {
        scratch.mkdirs()
        val raw = File(scratch, "tafsir_${source.key}_$surah.raw")
        try {
            raw.delete()
            if (!fetch(suraUrl(source, surah), raw)) return null
            val page = TafsirFormat.parseQuranEnc(raw.readText(Charsets.UTF_8), surah) ?: return null
            return TafsirFormat.assemble(surah, source, listOf(page), QuranFacts.ayahCount(surah), version = version(source, raw))
        } finally {
            raw.delete()
        }
    }

    private suspend fun version(source: TafsirSource, raw: File): String? = try {
        raw.delete()
        if (fetch(listUrl(), raw)) TafsirFormat.quranEncVersion(raw.readText(Charsets.UTF_8), source.remoteId) else null
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        const val QURAN_ENC_API_BASE_URL = "https://quranenc.com/api/v1/"
    }
}

/** Each [TafsirSource] goes to the source of its provider. */
internal class ProviderTafsirSource(
    private val quranCom: TafsirChapterSource,
    private val quranEnc: TafsirChapterSource,
) : TafsirChapterSource {
    override suspend fun fetch(source: TafsirSource, surah: Int): TafsirChapter? = when (source.provider) {
        TafsirProvider.QuranCom -> quranCom.fetch(source, surah)
        TafsirProvider.QuranEnc -> quranEnc.fetch(source, surah)
    }
}

/**
 * The commentaries of the app, one file per source and surah (`tafsir_cache/tafsir-{key}-{surah}.json`, the compact copy of
 * [TafsirFormat]), 30 MB at most (about eight surahs of 2's size in every source: the least recently used goes first), the last
 * [MEMORY_SURAHS] in memory. The order of reading is memory, disk, network ([ChapterCache]).
 */
internal class TafsirStore(
    directory: File,
    private val source: TafsirChapterSource,
    throttle: FailureThrottle = FailureThrottle(),
    limitBytes: Long = CACHE_LIMIT_BYTES,
    now: () -> Long = System::currentTimeMillis,
) {
    private val cache = ChapterCache<TafsirChapter>(
        directory = directory,
        limitBytes = limitBytes,
        memoryEntries = MEMORY_SURAHS,
        encode = TafsirFormat::encode,
        decode = { key, text -> decodeFile(key, text) },
        throttle = throttle,
        now = now,
    )

    private fun decodeFile(key: String, text: String): TafsirChapter? {
        val parts = key.split('-')
        if (parts.size != 3 || parts[0] != "tafsir") return null
        val tafsir = TafsirSource.byKey(parts[1]) ?: return null
        return TafsirFormat.decode(text, parts[2].toIntOrNull() ?: return null, tafsir)
    }

    private fun keyOf(tafsir: TafsirSource, surah: Int) = "tafsir-${tafsir.key}-$surah"

    /** From memory or the disk; never the network. */
    fun local(tafsir: TafsirSource, surah: Int): TafsirChapter? = cache.local(keyOf(tafsir, surah))

    /** The commentary: from the device, else from the network (cached). Null when neither works. */
    suspend fun load(tafsir: TafsirSource, surah: Int, force: Boolean = false): TafsirChapter? =
        cache.load(keyOf(tafsir, surah), force) { source.fetch(tafsir, surah) }

    fun bytes(): Long = cache.bytes()

    fun clear(): Long = cache.clear()

    companion object {
        /** Surahs kept decoded in memory. */
        const val MEMORY_SURAHS = 3

        /** The folder of the commentaries: the cache, not the backup; the phone may clear it. */
        const val CACHE_LIMIT_BYTES = 30L * 1024 * 1024

        val shared: TafsirStore by lazy {
            val directory = File(AndroidContextHolder.context.noBackupFilesDir, "tafsir_cache")
            TafsirStore(
                directory = directory,
                source = ProviderTafsirSource(QuranComTafsirSource(scratch = directory), QuranEncTafsirSource(scratch = directory)),
            )
        }
    }
}
