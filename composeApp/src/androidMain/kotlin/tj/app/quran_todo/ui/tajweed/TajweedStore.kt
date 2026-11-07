package tj.app.quran_todo.ui.tajweed

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.tajweed.TajweedAlignment
import tj.app.quran_todo.common.tajweed.TajweedAyah
import tj.app.quran_todo.common.tajweed.TajweedFormat
import tj.app.quran_todo.common.tajweed.TajweedMarkup
import tj.app.quran_todo.common.tajweed.TajweedSpan
import tj.app.quran_todo.data.bundled.QuranFacts

/** Whether the tajweed markup is on the device (the "Aa" sheet shows it, the text waits for [Ready]). */
internal sealed interface TajweedAvailability {
    /** Not on the device and nothing is running. */
    data object Missing : TajweedAvailability

    /** The one request is running; [fraction] (0..1) is counted against the known size of the answer (2.5 MB). */
    data class Downloading(val fraction: Float) : TajweedAvailability

    /** The last try failed (no network, a broken answer): "An internet connection is needed once" and "Retry". */
    data object NeedsInternet : TajweedAvailability

    /** Every surah is on the device. */
    data object Ready : TajweedAvailability
}

/**
 * The tajweed markup on the device and the colours of an ayah (task F3b).
 *
 * **Storage.** Turning tajweed on for the first time fetches the whole Quran in ONE request (`quran-tajweed` of api.alquran.cloud, about
 * 2.5 MB) and keeps it in `filesDir/tajweed/` (not a cache: nothing evicts it): [TajweedFormat.splitQuran] checks the answer, and every surah
 * is written as its own small file (`s002.txt`: the markup of the ayahs, one per line), the marker `ready` last. A try that did not finish
 * leaves no marker. It is not in "Downloads and storage": one small file for all surahs.
 *
 * **Reading.** A surah is read and parsed lazily and in the background ([load]); the ayahs of the last [maxSurahs] surahs are kept parsed
 * in memory (an LRU). The rules are moved onto the text that is drawn ([TajweedAlignment]) one ayah at a time, on first use, and the result
 * is kept with the surah ([spansIfReady]: no waiting, so a row of the list can ask for it while it is composed).
 *
 * **Without network.** A failed try is shown as [TajweedAvailability.NeedsInternet]; the setting stays on and the colours come when
 * the file does: the reader asks again when it is opened ([prepare], not more often than every [RETRY_AFTER_MS]) and the "Retry" button
 * asks at once ([ensureDownloaded] with `force`). A file that cannot be read any more is dropped and fetched again.
 */
internal class TajweedStore(
    private val directory: File,
    private val fetch: suspend (url: String, target: File, onBytes: (Long) -> Unit) -> Boolean = ::httpTajweedFetch,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val maxSurahs: Int = MAX_SURAHS,
) {
    private val _availability = MutableStateFlow<TajweedAvailability>(if (isComplete()) TajweedAvailability.Ready else TajweedAvailability.Missing)
    val availability: StateFlow<TajweedAvailability> = _availability.asStateFlow()

    private val _version = MutableStateFlow(0)

    /** Grows whenever something a screen may show changed: the file arrived or went, a surah was parsed. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val lock = Any()
    private var lastFailureAt = Long.MIN_VALUE
    private val loading = Mutex()

    // Most recently used last; trimmed to maxSurahs.
    private val memory = LinkedHashMap<Int, SurahMarkup>()

    /** Starts the download when the markup is not on the device, nothing runs, and (unless [force]) the last try is not recent. */
    fun ensureDownloaded(force: Boolean = false) {
        synchronized(lock) {
            when (_availability.value) {
                is TajweedAvailability.Ready, is TajweedAvailability.Downloading -> return
                else -> Unit
            }
            if (!force && lastFailureAt != Long.MIN_VALUE && now() - lastFailureAt < RETRY_AFTER_MS) return
            _availability.value = TajweedAvailability.Downloading(0f)
        }
        scope.launch { download() }
    }

    /** What a screen does when it shows [surah] with tajweed on: asks for the file when it is missing, reads the surah when it is there. */
    suspend fun prepare(surah: Int) {
        when (_availability.value) {
            // A row of a list that is already in memory costs nothing: no hop to another thread.
            is TajweedAvailability.Ready -> if (recall(surah) == null) load(surah)
            is TajweedAvailability.Missing, is TajweedAvailability.NeedsInternet -> ensureDownloaded()
            is TajweedAvailability.Downloading -> Unit
        }
    }

    /** Reads and parses [surah] into memory (once). False when the markup is not on the device or the file of the surah is damaged. */
    suspend fun load(surah: Int): Boolean = withContext(Dispatchers.IO) {
        if (surah !in 1..QuranFacts.SURAH_COUNT || _availability.value !is TajweedAvailability.Ready) return@withContext false
        if (recall(surah) != null) return@withContext true
        loading.withLock {
            if (recall(surah) != null) return@withLock true
            val parsed = try {
                val text = File(directory, surahFile(surah)).readText(Charsets.UTF_8)
                TajweedFormat.decodeSurah(text, QuranFacts.ayahCount(surah))?.map(TajweedMarkup::parse)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (parsed == null) {
                // A file that cannot be read: drop all of it, the next ask fetches it again.
                discard()
                return@withLock false
            }
            remember(surah, SurahMarkup(parsed))
            _version.update { it + 1 }
            true
        }
    }

    /**
     * The rules on [text] (what the screen draws for ayah [ayah] of [surah]), or null while the surah is not in memory. Never blocks on the
     * file: the cost is the alignment of one ayah (a fraction of a millisecond), done once per text and kept.
     */
    fun spansIfReady(surah: Int, ayah: Int, text: String): List<TajweedSpan>? = recall(surah)?.spans(ayah, text)

    /** The surahs that are in memory now (the most recently used last); for tests. */
    fun surahsInMemory(): List<Int> = synchronized(memory) { memory.keys.toList() }

    private fun recall(surah: Int): SurahMarkup? = synchronized(memory) {
        val markup = memory.remove(surah) ?: return@synchronized null
        memory[surah] = markup
        markup
    }

    private fun remember(surah: Int, markup: SurahMarkup) = synchronized(memory) {
        memory.remove(surah)
        memory[surah] = markup
        while (memory.size > maxSurahs) memory.remove(memory.keys.first())
    }

    private fun isComplete(): Boolean = try {
        File(directory, MARKER).readText(Charsets.UTF_8).trim() == MARKER_CONTENT
    } catch (_: Exception) {
        false
    }

    /** Forgets the file: no marker, nothing in memory, the state says it is missing. */
    private fun discard() {
        File(directory, MARKER).delete()
        synchronized(memory) { memory.clear() }
        synchronized(lock) { _availability.value = TajweedAvailability.Missing }
        _version.update { it + 1 }
    }

    private suspend fun download() {
        val part = File(directory, PART)
        val ok = try {
            directory.mkdirs()
            File(directory, MARKER).delete()
            part.delete()
            var reported = 0f
            val fetched = fetch(TajweedFormat.URL, part) { bytes ->
                val fraction = (bytes.toFloat() / TajweedFormat.EXPECTED_BYTES).coerceIn(0f, MAX_PROGRESS)
                if (fraction - reported >= 0.01f) {
                    reported = fraction
                    synchronized(lock) {
                        if (_availability.value is TajweedAvailability.Downloading) _availability.value = TajweedAvailability.Downloading(fraction)
                    }
                }
            }
            fetched && store(part.readText(Charsets.UTF_8))
        } catch (cancelled: CancellationException) {
            synchronized(lock) { _availability.value = TajweedAvailability.Missing }
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            part.delete()
        }
        if (ok) synchronized(memory) { memory.clear() }
        synchronized(lock) {
            if (ok) {
                _availability.value = TajweedAvailability.Ready
            } else {
                lastFailureAt = now()
                _availability.value = TajweedAvailability.NeedsInternet
            }
        }
        _version.update { it + 1 }
    }

    /** Writes every surah (a temporary file, then the final name) and the marker; false when the answer is not a whole Quran. */
    private fun store(json: String): Boolean {
        val surahs = TajweedFormat.splitQuran(json) ?: return false
        for ((index, markups) in surahs.withIndex()) {
            val target = File(directory, surahFile(index + 1))
            val temporary = File(directory, target.name + ".tmp")
            temporary.writeText(TajweedFormat.encodeSurah(markups), Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                return false
            }
        }
        File(directory, MARKER).writeText(MARKER_CONTENT, Charsets.UTF_8)
        return true
    }

    /** The parsed ayahs of one surah and the alignments made so far. */
    private class SurahMarkup(private val ayahs: List<TajweedAyah>) {
        private class Aligned(val text: String, val spans: List<TajweedSpan>)

        private val aligned = arrayOfNulls<Aligned>(ayahs.size)

        fun spans(ayah: Int, text: String): List<TajweedSpan> {
            val index = ayah - 1
            if (index !in ayahs.indices) return emptyList()
            aligned[index]?.let { if (it.text == text) return it.spans }
            val spans = TajweedAlignment.align(ayahs[index], text)
            aligned[index] = Aligned(text, spans)
            return spans
        }
    }

    companion object {
        /** Parsed surahs kept in memory (Al-Baqarah is the biggest: 286 ayahs, a few hundred kilobytes). */
        const val MAX_SURAHS = 10

        /** A failed download is not tried again by itself for this long (the "Retry" button does not wait). */
        const val RETRY_AFTER_MS = 30_000L

        private const val MARKER = "ready"
        private const val MARKER_CONTENT = "tajweed-v1 6236"
        private const val PART = "download.part"
        private const val MAX_PROGRESS = 0.97f

        fun surahFile(surah: Int): String = "s${surah.toString().padStart(3, '0')}.txt"

        /** The store the app uses: `filesDir/tajweed/`. */
        val shared: TajweedStore by lazy { TajweedStore(File(AndroidContextHolder.context.filesDir, "tajweed")) }
    }
}

/**
 * The plain HTTP download of [url] into [target], reporting the bytes read so far. Cancelling the coroutine stops it between two chunks.
 * True only when the whole body arrived. The system may compress the answer, so the length it reports is not used for the progress.
 */
internal suspend fun httpTajweedFetch(url: String, target: File, onBytes: (Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    var finished = false
    try {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
        val expected = connection.contentLengthLong
        var written = 0L
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    written += count
                    onBytes(written)
                }
            }
        }
        finished = expected < 0 || written == expected
        finished
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    } finally {
        if (!finished) connection.disconnect()
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val BUFFER_BYTES = 32 * 1024
