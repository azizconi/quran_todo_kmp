package tj.app.quran_todo.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

/**
 * The part of `java.io.File` that the stores of the app use, over `NSFileManager`: the stores of Android are ported to iOS nearly as
 * they are. Paths are plain POSIX paths.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class File(val path: String) {
    constructor(parent: File, child: String) : this(parent.path.trimEnd('/') + "/" + child)

    private val manager get() = NSFileManager.defaultManager

    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val parentFile: File? get() = path.trimEnd('/').substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let(::File)
    val absolutePath: String get() = path

    fun exists(): Boolean = manager.fileExistsAtPath(path)

    fun isDirectory(): Boolean =
        (manager.attributesOfItemAtPath(path, null)?.get(NSFileType) as? String) == NSFileTypeDirectory

    fun isFile(): Boolean = exists() && !isDirectory()

    fun length(): Long = (manager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

    fun lastModified(): Long =
        (((manager.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0) * 1000).toLong()

    fun setLastModified(millis: Long): Boolean =
        manager.setAttributes(mapOf<Any?, Any?>(NSFileModificationDate to NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)), path, null)

    fun delete(): Boolean = exists() && manager.removeItemAtPath(path, null)

    fun mkdirs(): Boolean = manager.createDirectoryAtPath(path, true, null, null)

    /** Moves this file onto [target], replacing it (the POSIX `rename`). */
    fun renameTo(target: File): Boolean {
        if (target.exists()) target.delete()
        return manager.moveItemAtPath(path, target.path, null)
    }

    fun copyTo(target: File, overwrite: Boolean = false): File {
        if (target.exists()) {
            check(overwrite) { "target exists" }
            target.delete()
        }
        check(manager.copyItemAtPath(path, target.path, null)) { "copy failed" }
        return target
    }

    fun listFiles(filter: ((File) -> Boolean)? = null): List<File>? {
        val names = manager.contentsOfDirectoryAtPath(path, null) ?: return null
        val files = names.map { File(this, it as String) }
        return if (filter == null) files else files.filter(filter)
    }

    fun readText(): String = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("cannot read $path")

    fun writeText(text: String) {
        val done = NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        check(done) { "cannot write $path" }
    }

    fun deleteRecursively(): Boolean = !exists() || manager.removeItemAtPath(path, null)

    /** The sizes of every file under this folder (a file itself counts as its own size). */
    fun totalBytes(): Long {
        if (!exists()) return 0L
        if (!isDirectory()) return length()
        return listFiles().orEmpty().sumOf { it.totalBytes() }
    }

    /** The folder is kept out of the iCloud backup (audio, recordings and fonts that can be fetched again must not fill it). */
    fun excludeFromBackup() {
        NSURL.fileURLWithPath(path).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
    override fun toString(): String = path
}

fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/** The folders of the app: Application Support (kept), Caches (the system may clear it), tmp. */
@OptIn(ExperimentalForeignApi::class)
object AppFolders {
    private fun directory(kind: ULong): File {
        val url = NSFileManager.defaultManager.URLForDirectory(kind, NSUserDomainMask, null, true, null)
        return File(url?.path ?: NSTemporaryDirectory())
    }

    val applicationSupport: File by lazy { directory(NSApplicationSupportDirectory).also { it.mkdirs() } }
    val caches: File by lazy { directory(NSCachesDirectory).also { it.mkdirs() } }
    val temporary: File get() = File(NSTemporaryDirectory())

    /** A folder of the app in Application Support that is not part of the iCloud backup. */
    fun noBackup(name: String): File = File(applicationSupport, name).also {
        it.mkdirs()
        it.excludeFromBackup()
    }
}
