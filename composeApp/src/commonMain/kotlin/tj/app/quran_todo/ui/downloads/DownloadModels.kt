package tj.app.quran_todo.ui.downloads

import kotlinx.serialization.Serializable
import tj.app.quran_todo.common.audio.Reciters

/** What a download request was made from; the analytics name goes into `download_started`. */
enum class DownloadKind(val analyticsName: String) {
    PLAN("plan"),
    JUZ("juz"),
    SURAH("surah"),

    /** The layout and the fonts of mushaf pages ("Mushaf pages", task F3a): a juz of them or all 604. */
    PAGES("pages"),

    /** A whole translation of the Quran in one request ("Translations", task F4a); [DownloadRequest.edition] says which. */
    TRANSLATION("translation"),
}

/** A continuous part of one surah: ayahs [first]..[last] (numbers in the surah). */
@Serializable
data class Segment(val surah: Int, val first: Int, val last: Int) {
    val count: Int get() = last - first + 1
}

/**
 * One thing the person asked to download: a surah, a juz or "my plan". It is stored whole in the queue file, so a work that is
 * started again after the process died knows everything it needs: which reciter, which ayahs, whether the translation comes along.
 *
 * @property id unique in the queue: kind, the number and the reciter (`juz-30-alafasy`), so asking twice does not queue twice.
 * @property title what the notification and the queue card call it ("Juz Amma"), in the language at the time of the request.
 * @property language the app language whose translation is downloaded before the audio ([tj.app.quran_todo.common.i18n.AppLanguage]
 *   name), null for audio only.
 * @property pages the pages of the mushaf of a [DownloadKind.PAGES] request (1..604), empty for every other kind; such a request has no
 *   segments and no reciter. Pages are fetched into the pinned folder by [tj.app.quran_todo.ui.mushaf.data.MushafPageStore.pin].
 */
@Serializable
data class DownloadRequest(
    val id: String,
    val kind: DownloadKind,
    val title: String,
    val reciterId: String,
    val language: String? = null,
    val segments: List<Segment>,
    val pages: List<Int> = emptyList(),
    /** The key of the edition of a [DownloadKind.TRANSLATION] request (`TranslationEdition.key`); null for every other kind. */
    val edition: String? = null,
) {
    val ayahCount: Int get() = segments.sumOf { it.count }

    /** The files this request is about, in reading order: the audio cache keys of its ayahs, or the keys of its pages ([PageKeys]). */
    fun keys(): List<String> {
        if (kind == DownloadKind.PAGES) return pages.map(PageKeys::of)
        // A translation is stored in the database, not in files: it has no keys.
        if (kind == DownloadKind.TRANSLATION) return emptyList()
        val reciter = Reciters.byId(reciterId)
        return segments.flatMap { segment ->
            (segment.first..segment.last).map { ayah -> reciter.cacheKey(segment.surah, ayah) }
        }
    }

    companion object {
        fun idOf(kind: DownloadKind, number: Int?, reciterId: String): String =
            listOfNotNull(kind.analyticsName, number?.toString(), reciterId).joinToString("-")

        /** The id of the pages request of [juz] (1..30), or of all pages when it is null; a reciter plays no part in it. */
        fun pagesId(juz: Int?): String = if (juz == null) "pages-all" else "pages-juz-$juz"

        /** The id of the request of the translation [editionKey]: asking twice does not queue twice. */
        fun translationId(editionKey: String): String = "translation-$editionKey"
    }
}

/**
 * The key of a pinned page in the map of downloaded files (`DownloadsViewModel`), next to the keys of the audio: `mushaf-page-3`. An
 * audio key never looks like it (`ayah_78_1`, `husary_78_1`).
 */
object PageKeys {
    private const val PREFIX = "mushaf-page-"

    fun of(page: Int): String = "$PREFIX$page"

    /** The page of a key made by [of], or null for any other key. */
    fun pageOf(key: String): Int? = key.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()
}

/** How much of a row is on the device (computed from the files, see [rowState]). */
sealed interface RowState {
    data object NotDownloaded : RowState

    data class Partial(val done: Int, val total: Int, val bytes: Long) : RowState

    data class Complete(val total: Int, val bytes: Long) : RowState
}

/**
 * The state of a row from the files that exist: [keys] are the cache keys of the row's ayahs, [files] every downloaded file
 * (key to bytes). A row with every ayah is [RowState.Complete]; with some, [RowState.Partial] ("120 of 564 - 12 MB").
 */
fun rowState(keys: List<String>, files: Map<String, Long>): RowState {
    if (keys.isEmpty()) return RowState.NotDownloaded
    var done = 0
    var bytes = 0L
    for (key in keys) {
        val size = files[key] ?: continue
        done++
        bytes += size
    }
    return when (done) {
        0 -> RowState.NotDownloaded
        keys.size -> RowState.Complete(keys.size, bytes)
        else -> RowState.Partial(done, keys.size, bytes)
    }
}
