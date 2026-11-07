package tj.app.quran_todo.data.bundled

import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qurantodo.composeapp.generated.resources.Res

/**
 * The Quran text that ships inside the app (`composeResources/files/quran/quran-uthmani.tsv`, the same
 * `quran-uthmani` text the app used to download). Reading and parsing happen on [Dispatchers.Default], never
 * on the main thread.
 *
 * [readBytes] is a parameter so the loading can be tested without the resources machinery.
 */
class BundledQuranSource(
    private val readBytes: suspend (String) -> ByteArray = { path -> Res.readBytes(path) },
) {
    /** How long reading the file and parsing it took, for the debug log. */
    class Timing(val readMs: Long, val parseMs: Long)

    suspend fun load(): BundledQuran = loadTimed().first

    suspend fun loadTimed(): Pair<BundledQuran, Timing> = withContext(Dispatchers.Default) {
        val t0 = getTimeMillis()
        val bytes = readBytes(PATH)
        val t1 = getTimeMillis()
        val quran = QuranTsvParser.parse(bytes.decodeToString())
        val t2 = getTimeMillis()
        quran to Timing(readMs = t1 - t0, parseMs = t2 - t1)
    }

    companion object {
        const val PATH = "files/quran/quran-uthmani.tsv"
        const val EDITION_ID = "quran-uthmani"
    }
}
