package tj.app.quran_todo.ui.mushaf.data

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.CacheEntry
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.mushaf.MushafLayoutFormat
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.common.audio.selectEvictions
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.data.translation.FailureThrottle

/** A page that can be drawn: its layout and the file of its font. */
internal class LoadedPage(val page: MushafPage, val font: File)

/**
 * The pages of the mushaf that the downloads keep, as the download work sees them: which pages are pinned, how to pin one, and the
 * cleaning of what an interrupted run left. A fake in the tests of the runner; [MushafPageStore] is the real one.
 */
internal interface PagesSource {
    /** The page is pinned (both files are in the pinned folder). */
    fun has(page: Int): Boolean

    /** Pins [page]: from the cache when it is there, otherwise fetched. True when it is pinned afterwards. */
    suspend fun pin(page: Int): Boolean

    /** Removes the unfinished files of an interrupted run. */
    fun deleteUnfinished()

    /** No pages: the downloads of audio only (and the tests of them). */
    object None : PagesSource {
        override fun has(page: Int): Boolean = false

        override suspend fun pin(page: Int): Boolean = false

        override fun deleteUnfinished() = Unit
    }
}

/**
 * Where a page of the mushaf comes from, in this order (the same order as the word timings, see `WordTimingsStore`):
 *  1. memory: the last [memoryPages] layouts that were read;
 *  2. the pinned copy: pages that "Downloads and storage" fetched (next to the downloaded audio in `downloads/`, never evicted, deleted
 *     only with the downloads);
 *  3. the cache: pages that reading fetched (`mushaf_cache/`, [cacheLimitBytes], the least recently used file goes first);
 *  4. the network ([MushafLayoutSource]): one request for the layout and one for the font, written to the cache.
 *
 * A page is two files, `mushaf_p{page}.json` (the compact copy of [MushafLayoutFormat]) and `mushaf_f{page}.ttf` (the font); they are
 * written as `*.part` and renamed, so a file that is there is whole. The fonts are not kept in memory (a `FontFamily` is cheap for the
 * screen to make from the file; the screen keeps the last few).
 *
 * Nothing here shows an error: [load] is null for a page that is neither on the phone nor reachable, and the screen shows its quiet
 * "Page not loaded" placeholder. A request that failed is not repeated for [FailureThrottle.DEFAULT_WINDOW_MS] (30 s) unless the person
 * taps "Retry" (`force`); the downloads ([pin]) are not held back by that, they have their own backoff.
 */
internal class MushafPageStore(
    private val pinned: File,
    private val cache: File,
    private val source: MushafLayoutSource,
    private val throttle: FailureThrottle = FailureThrottle(),
    private val memoryPages: Int = MEMORY_PAGES,
    private val cacheLimitBytes: Long = CACHE_LIMIT_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) : PagesSource {
    // The most recently used page is the last entry.
    private val memory = LinkedHashMap<Int, MushafPage>()

    private val _revision = MutableStateFlow(0L)

    /** Grows with every page that is pinned or deleted from the pinned copies, so a screen can read [pinnedSnapshot] again. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        pinned.mkdirs()
        cache.mkdirs()
    }

    fun layoutName(page: Int): String = "mushaf_p$page.json"

    fun fontName(page: Int): String = "mushaf_f$page.ttf"

    private fun recall(page: Int): MushafPage? = synchronized(memory) {
        val found = memory.remove(page) ?: return@synchronized null
        memory[page] = found
        found
    }

    private fun remember(page: Int, value: MushafPage) = synchronized(memory) {
        memory.remove(page)
        memory[page] = value
        while (memory.size > memoryPages) memory.remove(memory.keys.first())
    }

    // -- On the device ---------------------------------------------------------------------------------------

    /** The layout of [page] from memory, the pinned copies or the cache; never the network. */
    fun localPage(page: Int): MushafPage? {
        recall(page)?.let { return it }
        for (directory in listOf(pinned, cache)) {
            val file = File(directory, layoutName(page))
            if (!file.isFile) continue
            val decoded = readLayout(file, page)
            if (decoded != null) {
                if (directory == cache) file.setLastModified(now())
                remember(page, decoded)
                return decoded
            }
            // Damaged or of another version: gone, it is fetched again.
            file.delete()
        }
        return null
    }

    /** The font file of [page] from the pinned copies or the cache; never the network. */
    fun localFont(page: Int): File? {
        for (directory in listOf(pinned, cache)) {
            val file = File(directory, fontName(page))
            if (!file.isFile) continue
            if (isFont(file)) {
                if (directory == cache) file.setLastModified(now())
                return file
            }
            file.delete()
        }
        return null
    }

    /** Both files of [page] are on the phone (in memory counts for the layout). */
    fun isLocal(page: Int): Boolean = localPage(page) != null && localFont(page) != null

    // -- Reading ---------------------------------------------------------------------------------------------

    /** The layout of [page]: from the device, else from the network (cached). Null when neither works. */
    suspend fun page(page: Int, force: Boolean = false): MushafPage? = withContext(Dispatchers.IO) {
        localPage(page)?.let { return@withContext it }
        val key = "layout-$page"
        if (force) throttle.clear(key) else if (throttle.isBlocked(key)) return@withContext null
        mutexFor(key).withLock {
            localPage(page)?.let { return@withLock it }
            val fetched = fetchLayout(page)
            if (fetched == null) {
                throttle.markFailed(key)
                null
            } else {
                throttle.clear(key)
                writeLayout(cache, fetched)
                File(cache, layoutName(page)).setLastModified(now())
                remember(page, fetched)
                evictFromCache(keep = setOf(layoutName(page)))
                fetched
            }
        }
    }

    /** The font file of [page]: from the device, else from the network (cached). Null when neither works. */
    suspend fun font(page: Int, force: Boolean = false): File? = withContext(Dispatchers.IO) {
        localFont(page)?.let { return@withContext it }
        val key = "font-$page"
        if (force) throttle.clear(key) else if (throttle.isBlocked(key)) return@withContext null
        mutexFor(key).withLock {
            localFont(page)?.let { return@withLock it }
            val target = File(cache, fontName(page))
            if (fetchFont(page, target)) {
                throttle.clear(key)
                // "Used" is the modification time, as in the cache of the audio.
                target.setLastModified(now())
                evictFromCache(keep = setOf(target.name))
                target
            } else {
                throttle.markFailed(key)
                null
            }
        }
    }

    /** The page and its font, fetched together; null unless both are there. */
    suspend fun load(page: Int, force: Boolean = false): LoadedPage? = coroutineScope {
        val layout = async { page(page, force) }
        val font = async { font(page, force) }
        val loadedLayout = layout.await()
        val loadedFont = font.await()
        if (loadedLayout != null && loadedFont != null) LoadedPage(loadedLayout, loadedFont) else null
    }

    /** Fetches [pages] that are not on the device yet, one after the other (a quiet look ahead; a failure is ignored). */
    suspend fun prefetch(pages: Collection<Int>) {
        for (page in pages) {
            if (page !in 1..MushafPages.COUNT || withContext(Dispatchers.IO) { isLocal(page) }) continue
            load(page)
        }
    }

    // -- Pinned copies (the downloads) --------------------------------------------------------------------------

    /** The page is pinned: both files are in the pinned folder (checked by size, the way [pinnedSnapshot] counts). */
    fun isPinned(page: Int): Boolean = pinnedBytes(page) > 0

    override fun has(page: Int): Boolean = isPinned(page)

    private fun pinnedBytes(page: Int): Long {
        val layout = File(pinned, layoutName(page))
        val font = File(pinned, fontName(page))
        return if (layout.length() >= MIN_LAYOUT_BYTES && font.length() >= MIN_FONT_BYTES) layout.length() + font.length() else 0L
    }

    /**
     * Makes sure both files of [page] are in the pinned folder: copied from the cache when they are there, otherwise fetched there. True
     * when the page is pinned afterwards. A failure is not remembered: the download work retries with its own backoff.
     */
    override suspend fun pin(page: Int): Boolean = withContext(Dispatchers.IO) {
        if (isPinned(page)) return@withContext true
        mutexFor("pin-$page").withLock {
            try {
                pinLayout(page) && pinFont(page)
            } finally {
                changed()
            }
        }
    }

    private suspend fun pinLayout(page: Int): Boolean {
        val target = File(pinned, layoutName(page))
        if (target.length() >= MIN_LAYOUT_BYTES && readLayout(target, page) != null) return true
        val cached = File(cache, layoutName(page))
        if (cached.isFile && readLayout(cached, page) != null) return copy(cached, target)
        val fetched = fetchLayout(page) ?: return false
        writeLayout(pinned, fetched)
        remember(page, fetched)
        return true
    }

    private suspend fun pinFont(page: Int): Boolean {
        val target = File(pinned, fontName(page))
        if (isFont(target)) return true
        val cached = File(cache, fontName(page))
        if (isFont(cached)) return copy(cached, target)
        return fetchFont(page, target)
    }

    /** The pinned pages: page to the bytes of its two files. Only pages that are whole are in it. */
    fun pinnedSnapshot(): Map<Int, Long> {
        val files = pinned.listFiles { file -> file.isFile && (file.name.startsWith("mushaf_p") || file.name.startsWith("mushaf_f")) }
            ?: return emptyMap()
        val layouts = HashMap<Int, Long>()
        val fonts = HashMap<Int, Long>()
        for (file in files) {
            val name = file.name
            when {
                name.startsWith("mushaf_p") && name.endsWith(".json") ->
                    name.removePrefix("mushaf_p").removeSuffix(".json").toIntOrNull()?.let { layouts[it] = file.length() }
                name.startsWith("mushaf_f") && name.endsWith(".ttf") ->
                    name.removePrefix("mushaf_f").removeSuffix(".ttf").toIntOrNull()?.let { fonts[it] = file.length() }
            }
        }
        val whole = HashMap<Int, Long>()
        for ((page, layoutBytes) in layouts) {
            val fontBytes = fonts[page] ?: continue
            if (layoutBytes >= MIN_LAYOUT_BYTES && fontBytes >= MIN_FONT_BYTES) whole[page] = layoutBytes + fontBytes
        }
        return whole
    }

    /** Deletes the pinned copies of [pages]; returns the bytes that were freed. */
    fun deletePinned(pages: Collection<Int>): Long {
        var freed = 0L
        for (page in pages) {
            for (name in listOf(layoutName(page), fontName(page))) {
                val file = File(pinned, name)
                val size = file.length()
                if (file.delete()) freed += size
                File(pinned, name + PART_SUFFIX).delete()
            }
        }
        changed()
        return freed
    }

    /** Deletes every pinned page; returns the bytes that were freed. */
    fun deleteAllPinned(): Long = deletePinned(pinnedSnapshot().keys + partialPages())

    /** The pages that have only one of their two files or an unfinished file (after an interrupted download). */
    private fun partialPages(): Set<Int> {
        val files = pinned.listFiles { file -> file.isFile && file.name.startsWith("mushaf_") } ?: return emptySet()
        return files.mapNotNull { file ->
            file.name.removePrefix("mushaf_").drop(1).takeWhile { it.isDigit() }.toIntOrNull()
        }.toSet()
    }

    /** Removes the unfinished files of interrupted downloads (the process died or the work was stopped). */
    override fun deleteUnfinished() {
        pinned.listFiles { file -> file.isFile && file.name.startsWith("mushaf_") && file.name.endsWith(PART_SUFFIX) }
            ?.forEach { it.delete() }
    }

    /** The pinned folder changed outside of this store (the downloads were deleted): a screen reads the snapshot again. */
    fun changed() {
        _revision.value = _revision.value + 1
        synchronized(memory) {
            // The pinned pages may be gone: what is left in memory is only what the cache can give back.
            memory.keys.toList().forEach { page ->
                if (!File(pinned, layoutName(page)).isFile && !File(cache, layoutName(page)).isFile) memory.remove(page)
            }
        }
    }

    // -- Cache -----------------------------------------------------------------------------------------------

    /** Bytes the cached pages take on disk. */
    fun cacheBytes(): Long = cacheFiles().sumOf { it.length() }

    /** Deletes every cached page; returns the bytes that were freed (the pinned ones stay). */
    fun clearCache(): Long {
        var freed = 0L
        cache.listFiles()?.forEach { file ->
            val size = file.length()
            if (file.isFile && file.delete()) freed += size
        }
        synchronized(memory) {
            memory.keys.toList().forEach { page -> if (!File(pinned, layoutName(page)).isFile) memory.remove(page) }
        }
        return freed
    }

    private fun cacheFiles(): List<File> =
        cache.listFiles { file -> file.isFile && file.name.startsWith("mushaf_") && !file.name.endsWith(PART_SUFFIX) }?.toList().orEmpty()

    private fun evictFromCache(keep: Set<String>) {
        val files = cacheFiles()
        val entries = files.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        val doomed = selectEvictions(entries, cacheLimitBytes, cacheLimitBytes / 100 * 90, keep).map { it.name }.toSet()
        files.filter { it.name in doomed }.forEach { it.delete() }
    }

    // -- Files -----------------------------------------------------------------------------------------------

    // A quick swipe through many pages must not open dozens of connections at once.
    private val network = Semaphore(NETWORK_PARALLELISM)

    private suspend fun fetchLayout(page: Int): MushafPage? = try {
        network.withPermit { source.layout(page)?.takeIf { it.number == page } }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** Fetches the font into [target] through a `.part` file; true when a whole font is there afterwards. */
    private suspend fun fetchFont(page: Int, target: File): Boolean {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + PART_SUFFIX)
        part.delete()
        return try {
            if (!network.withPermit { source.font(page, part) } || !isFont(part)) return false
            target.delete()
            part.renameTo(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            part.delete()
        }
    }

    private fun writeLayout(directory: File, page: MushafPage) {
        directory.mkdirs()
        val target = File(directory, layoutName(page.number))
        val part = File(directory, target.name + PART_SUFFIX)
        part.writeText(MushafLayoutFormat.encode(page), Charsets.UTF_8)
        target.delete()
        if (!part.renameTo(target)) part.delete()
    }

    private fun readLayout(file: File, page: Int): MushafPage? = try {
        MushafLayoutFormat.decode(file.readText(Charsets.UTF_8), page)
    } catch (_: Exception) {
        null
    }

    private fun copy(from: File, to: File): Boolean {
        val part = File(to.parentFile, to.name + PART_SUFFIX)
        return try {
            from.copyTo(part, overwrite = true)
            to.delete()
            part.renameTo(to)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            part.delete()
        }
    }

    /** A file that starts the way a TrueType/OpenType font does and is big enough to hold glyphs (an error page is neither). */
    private fun isFont(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_FONT_BYTES) return false
        return try {
            RandomAccessFile(file, "r").use { input ->
                val head = ByteArray(4)
                input.readFully(head)
                val tag = String(head, Charsets.ISO_8859_1)
                tag == "true" || tag == "OTTO" || tag == "ttcf" ||
                    (head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte())
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** Decoded layouts kept in memory (a page is a few kilobytes decoded). */
        const val MEMORY_PAGES = 10

        /** Requests of this store on the way at once. */
        const val NETWORK_PARALLELISM = 4

        /** The cache of pages that reading fetched: about 300 pages with their fonts; the pinned pages are not part of it. */
        const val CACHE_LIMIT_BYTES = 48L * 1024 * 1024

        /** Anything smaller than this is an error page or an empty answer, not a layout / a font. */
        const val MIN_LAYOUT_BYTES = 300L
        const val MIN_FONT_BYTES = 4_096L

        private const val PART_SUFFIX = ".part"

        /** The store the app uses: pinned pages beside the downloads, the cache in its own folder. */
        val shared: MushafPageStore by lazy {
            val base = AndroidContextHolder.context.noBackupFilesDir
            MushafPageStore(
                pinned = DownloadStorage.shared.directory,
                cache = File(base, "mushaf_cache"),
                source = QuranComMushafSource(scratch = File(base, "mushaf_cache")),
            )
        }

        private val mutexMapGuard = Mutex()
        private val keyMutexes = mutableMapOf<String, Mutex>()

        private suspend fun mutexFor(key: String): Mutex = mutexMapGuard.withLock { keyMutexes.getOrPut(key) { Mutex() } }
    }
}
