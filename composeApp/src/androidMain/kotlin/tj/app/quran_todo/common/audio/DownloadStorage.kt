package tj.app.quran_todo.common.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.common.platform.AndroidContextHolder
import java.io.File

/**
 * The audio the person asked to download ("Downloads and storage"): one `.mp3` per ayah and reciter in [directory], named like the
 * files of the listening cache (`reciter.cacheKey(surah, ayah)` + `.mp3`). It is a separate folder on purpose: the 1 GB LRU cache
 * ([AudioCacheStorage]) only ever looks at its own folder, so downloaded files are never evicted; they go only when the person deletes
 * them. The playback code looks here first ([AudioFileStore]).
 *
 * A file is written as `<key>.mp3.download` and renamed when complete ([commit]): a file that is there is whole.
 *
 * [revision] changes every time the set of files changes, so a screen can read [snapshot] again.
 */
internal class DownloadStorage(val directory: File) {
    private val _revision = MutableStateFlow(0L)

    /** Grows with every file that is added or deleted. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    fun fileFor(key: String): File = File(directory, "$key.mp3")

    fun temporaryFileFor(key: String): File = File(directory, "$key.mp3.download")

    /** The ayah is downloaded: a complete file is there. */
    fun contains(key: String): Boolean = isValid(fileFor(key))

    /** The downloaded file of [key], or null. */
    fun existing(key: String): File? = fileFor(key).takeIf(::isValid)

    /** Moves the finished [temporary] file in place of [key]'s file. False when the file is too small to be audio or cannot be moved. */
    fun commit(key: String, temporary: File): Boolean {
        if (!isValid(temporary)) {
            temporary.delete()
            return false
        }
        val target = fileFor(key)
        target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return false
        }
        changed()
        return true
    }

    /** Every downloaded ayah: key (`ayah_78_1`, `husary_78_1`) to its size in bytes. */
    fun snapshot(): Map<String, Long> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) } ?: return emptyMap()
        val result = HashMap<String, Long>(files.size * 2)
        for (file in files) {
            val size = file.length()
            if (size >= MIN_VALID_BYTES) result[file.name.removeSuffix(SUFFIX)] = size
        }
        return result
    }

    /** Bytes the downloaded audio takes on disk. */
    fun totalBytes(): Long = snapshot().values.sum()

    /** Deletes the files of [keys] (and their unfinished copies); returns the bytes that were freed. */
    fun delete(keys: Collection<String>): Long {
        var freed = 0L
        for (key in keys) {
            val file = fileFor(key)
            val size = if (file.exists()) file.length() else 0L
            if (file.delete()) freed += size
            temporaryFileFor(key).delete()
        }
        changed()
        return freed
    }

    /** Deletes all downloads and unfinished downloads; returns the bytes that were freed. */
    fun deleteAll(): Long {
        var freed = 0L
        directory.listFiles()?.forEach { file ->
            val size = file.length()
            if (file.isFile && file.delete()) freed += size
        }
        changed()
        return freed
    }

    /** Removes the leftovers of downloads that were interrupted (the process died or the work was stopped). */
    fun deleteUnfinished() {
        directory.listFiles { file -> file.isFile && file.name.endsWith(TEMPORARY_SUFFIX) }?.forEach { it.delete() }
    }

    private fun changed() {
        _revision.value = _revision.value + 1
    }

    private fun isValid(file: File): Boolean = file.exists() && file.length() >= MIN_VALID_BYTES

    companion object {
        const val SUFFIX = ".mp3"
        const val TEMPORARY_SUFFIX = ".mp3.download"

        /** Anything smaller than this is an error page or an empty answer, not a recording. */
        const val MIN_VALID_BYTES = 512L

        /** The downloads of the app, in the no-backup directory (audio must not be restored to another device). */
        val shared: DownloadStorage by lazy {
            DownloadStorage(File(AndroidContextHolder.context.noBackupFilesDir, "downloads"))
        }
    }
}
