package tj.app.quran_todo.ui.recording

import java.io.File
import tj.app.quran_todo.common.platform.AndroidContextHolder

/** The passage a recording belongs to: the ayahs [startAyah]..[endAyah] (numbers in the surah) of [surah]. */
data class PassageKey(val surah: Int, val startAyah: Int, val endAyah: Int) {
    /** The folder name of the passage: `78_1_5`. */
    val folder: String get() = "${surah}_${startAyah}_$endAyah"
}

/** One recording of the person's own reading: the file, when it was started ([epochMillis]) and its size. */
data class Recording(val file: File, val key: PassageKey, val epochMillis: Long, val sizeBytes: Long)

/**
 * The files of the person's own recordings (task F2a): `{root}/{surah}_{start}_{end}/{epochMillis}.m4a`. The root is in the no-backup
 * directory of the app, so the recordings never reach the backup of Android, and nothing in the app reads them but the player of
 * "My reading" (they are not in the backup file of the transfer either). Only the last [keepPerPassage] recordings of a passage stay.
 *
 * A recording in progress is written to `{epochMillis}.m4a.part` and renamed when it is finished, so a file that is listed is always one
 * that can be played; a `.part` file left by a killed process is removed the next time its passage is listed.
 */
class RecordingStore(
    val root: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val keepPerPassage: Int = KEEP_PER_PASSAGE,
) {
    private val lock = Any()

    /** The files that are being written right now: a listing never takes them for leftovers. */
    private val active = HashSet<File>()

    fun folderOf(key: PassageKey): File = File(root, key.folder)

    /** The file a new recording of [key] is written to (a `.part` file, see [finish]); its folder is created. */
    fun newPart(key: PassageKey): File = synchronized(lock) {
        val folder = folderOf(key).also { it.mkdirs() }
        var stamp = now()
        while (File(folder, "$stamp$SUFFIX").exists() || File(folder, "$stamp$PART_SUFFIX").exists()) stamp++
        File(folder, "$stamp$PART_SUFFIX").also { active += it }
    }

    /** The recording of [part] was given up (the recorder failed, or nothing was said): the file goes. */
    fun discard(part: File) = synchronized(lock) {
        active -= part
        part.delete()
    }

    /**
     * A recording of [key] was written to [part]: it becomes a recording of the passage and the passage keeps its last
     * [keepPerPassage]. Null (and the file is removed) when [part] holds nothing that can be played ([minBytes]).
     */
    fun finish(part: File, key: PassageKey, minBytes: Long = MIN_BYTES): Recording? = synchronized(lock) {
        active -= part
        if (!part.exists()) return null
        if (part.length() < minBytes) {
            part.delete()
            return null
        }
        val target = File(part.parentFile, part.name.removeSuffix(PART_SUFFIX).plus(SUFFIX))
        if (!part.renameTo(target)) {
            part.delete()
            return null
        }
        prune(key)
        recordingOf(target, key)
    }

    /** The recordings of [key], the newest first. A `.part` file that no recording is writing is a leftover of a killed process and goes. */
    fun list(key: PassageKey): List<Recording> = synchronized(lock) {
        val folder = folderOf(key)
        val files = folder.listFiles() ?: return emptyList()
        files.filter { it.name.endsWith(PART_SUFFIX) && it !in active }.forEach { it.delete() }
        files.mapNotNull { recordingOf(it, key) }.sortedByDescending { it.epochMillis }
    }

    fun count(key: PassageKey): Int = list(key).size

    /** Removes the oldest recordings of [key] beyond [keepPerPassage]. */
    fun prune(key: PassageKey) = synchronized(lock) {
        list(key).drop(keepPerPassage).forEach { it.file.delete() }
    }

    fun delete(recording: Recording) = synchronized(lock) {
        recording.file.delete()
        val folder = recording.file.parentFile
        if (folder != null && folder.listFiles().isNullOrEmpty()) folder.delete()
    }

    /** Bytes of every recording (and leftover) on the device. */
    fun totalBytes(): Long = synchronized(lock) {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** "Delete all recordings". */
    fun deleteAll() = synchronized(lock) {
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun recordingOf(file: File, key: PassageKey): Recording? {
        if (!file.isFile || !file.name.endsWith(SUFFIX)) return null
        val stamp = file.name.removeSuffix(SUFFIX).toLongOrNull() ?: return null
        return Recording(file, key, stamp, file.length())
    }

    companion object {
        const val SUFFIX = ".m4a"
        const val PART_SUFFIX = ".m4a.part"

        /** Only the last this many recordings of a passage stay on the phone. */
        const val KEEP_PER_PASSAGE = 20

        /** A file smaller than this holds no sound: the recorder was stopped at once. */
        const val MIN_BYTES = 1_024L

        /** The recordings of the app, in the no-backup directory (a recording must not be restored to another device). */
        val shared: RecordingStore by lazy {
            RecordingStore(File(AndroidContextHolder.context.noBackupFilesDir, "recordings"))
        }
    }
}
