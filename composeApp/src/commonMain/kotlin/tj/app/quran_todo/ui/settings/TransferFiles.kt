package tj.app.quran_todo.ui.settings

import tj.app.quran_todo.hifz.backup.BackupFormat

/** The answer of [TransferFiles.readText]. */
sealed interface ReadOutcome {
    class Text(val text: String) : ReadOutcome

    /** More than [BackupFormat.MAX_FILE_BYTES]: not a backup of this app. */
    data object TooLarge : ReadOutcome

    /** The file could not be opened or read (permission, a provider that went away, no space...). */
    data object Unreadable : ReadOutcome
}

/**
 * The files of "Transfer and backup": reading a file the person picked, writing the one they chose to save, and the temporary file that
 * goes to another app. A file is named by an opaque `location` string: on Android a `content://` uri, on iOS a `file://` url string.
 *
 * iOS (S2): `readText` / `writeText` open the url from `UIDocumentPickerViewController` (call `startAccessingSecurityScopedResource`
 * first) and read at most [BackupFormat.MAX_FILE_BYTES] bytes; `createShareFile` writes UTF-8 text to `NSTemporaryDirectory()/transfer/
 * fileName` and returns the url string for `UIActivityViewController`; `clearShareFiles` empties that folder; `appVersion` is
 * `"{CFBundleShortVersionString} ({CFBundleVersion})"`.
 */
interface TransferFiles {
    /** `"1.0.2 (7)"`: goes into the file as `appVersion`. */
    fun appVersion(): String

    suspend fun readText(location: String): ReadOutcome

    /** Writes [text] to the document [location] that the person created in the system picker (truncating an existing one). */
    suspend fun writeText(location: String, text: String)

    /** Writes [text] to the cache directory and returns the location to hand to another app; earlier exports are deleted first. */
    suspend fun createShareFile(fileName: String, text: String): String

    /** Deletes the temporary copies of earlier "Send a copy" exports. */
    suspend fun clearShareFiles()
}
