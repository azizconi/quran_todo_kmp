package tj.app.quran_todo.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.app.quran_todo.hifz.backup.BackupFormat

/** The answer of [TransferIo.readText]. */
internal sealed interface ReadOutcome {
    class Text(val text: String) : ReadOutcome

    /** More than [BackupFormat.MAX_FILE_BYTES]: not a backup of this app. */
    data object TooLarge : ReadOutcome

    /** The file could not be opened or read (permission, a provider that went away, no space...). */
    data object Unreadable : ReadOutcome
}

/**
 * The files of "Transfer and backup": reading a file the person picked, writing the one they chose to save, and the temporary
 * file that goes to another app through [FileProvider]. Everything runs on the IO dispatcher.
 */
internal class TransferIo(private val context: Context) {

    /** `"1.0.2 (7)"`: goes into the file as `appVersion`. */
    fun appVersion(): String = appVersionLabel(context)

    suspend fun readText(uri: Uri): ReadOutcome = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext ReadOutcome.Unreadable
            input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > BackupFormat.MAX_FILE_BYTES) return@withContext ReadOutcome.TooLarge
                    out.write(buffer, 0, read)
                }
                ReadOutcome.Text(out.toString(Charsets.UTF_8.name()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // IOException, SecurityException (the grant is gone), IllegalArgumentException (a broken uri) and the like.
            ReadOutcome.Unreadable
        }
    }

    /** Writes [text] to the document [uri] that the person created in the system picker (truncating an existing one). */
    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Writes [text] to the cache directory and returns the content uri to hand to another app. The files of earlier exports
     * are deleted first, so the cache never holds more than one copy.
     */
    suspend fun createShareFile(fileName: String, text: String): Uri = withContext(Dispatchers.IO) {
        clearShareDir()
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(text, Charsets.UTF_8)
        FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Deletes the temporary copies of earlier "Send a copy" exports (called by every export, so none outlives the next one). */
    suspend fun clearShareFiles() = withContext(Dispatchers.IO) { clearShareDir() }

    private fun clearShareDir() {
        File(context.cacheDir, SHARE_DIR).listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** A folder of `res/xml/transfer_file_paths.xml`. */
        const val SHARE_DIR = "transfer"
        private const val BUFFER_BYTES = 64 * 1024

        /** The `android:authorities` of the provider in the manifest. */
        fun authority(context: Context): String = context.packageName + ".transferprovider"

        /** The chooser that sends the copy to Telegram, Google Drive, mail... The receiver may read [uri] and nothing else. */
        fun shareIntent(uri: Uri, fileName: String, chooserTitle: String): Intent {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = BackupFormat.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(send, chooserTitle)
        }
    }
}
