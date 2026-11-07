package tj.app.quran_todo.ui.progress.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.ui.settings.TransferIo
import tj.app.quran_todo.ui.theme.QhColors

/**
 * Hands the report to another app. The picture lives in `cache/report/` (a folder of `res/xml/transfer_file_paths.xml`, served by
 * the same `FileProvider` as the backup copy, `${applicationId}.transferprovider`); the previous picture is deleted whenever a new
 * report is sent (and when a report is sent as text only), so the cache never holds more than one.
 */
internal object ReportShare {
    /** A folder of `res/xml/transfer_file_paths.xml`; separate from the backup copy of "Transfer and backup". */
    const val DIR = "report"
    const val MIME_PNG = "image/png"
    const val MIME_PDF = "application/pdf"
    private const val MIME_TEXT = "text/plain"

    /** `quran-hifz-report-2026-10-30.png`. */
    fun fileName(toDay: Int): String = "quran-hifz-report-${LocalDate.fromEpochDays(toDay)}.png"

    /** Writes [image] as PNG into the cache (after deleting the earlier ones) and returns the uri for the other app. */
    suspend fun writePng(context: Context, image: ImageBitmap, toDay: Int): Uri = withContext(Dispatchers.IO) {
        clearDir(context)
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, fileName(toDay))
        file.outputStream().use { out ->
            if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("png was not written")
        }
        FileProvider.getUriForFile(context, TransferIo.authority(context), file)
    }

    /** `quran-hifz-report-2026-10-30.pdf`. */
    fun pdfFileName(toDay: Int): String = "quran-hifz-report-${LocalDate.fromEpochDays(toDay)}.pdf"

    /** Writes [doc] as a PDF into the cache (after deleting the earlier files) and returns the uri for the other app. */
    suspend fun writePdf(context: Context, doc: ReportDoc, colors: QhColors, toDay: Int): Uri = withContext(Dispatchers.IO) {
        clearDir(context)
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, pdfFileName(toDay))
        file.outputStream().use { out -> ReportPdf.render(doc, colors, out) }
        FileProvider.getUriForFile(context, TransferIo.authority(context), file)
    }

    /** Deletes the pictures of earlier reports. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) { clearDir(context) }

    private fun clearDir(context: Context) {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * The chooser of one `ACTION_SEND` that carries the text (`EXTRA_TEXT`) and, when [image] is given, the picture
     * (`EXTRA_STREAM`, `image/png`): messengers show the text as the caption. The receiver may read [image] and nothing else.
     */
    fun intent(text: String, subject: String, image: Uri?, chooserTitle: String, mime: String = MIME_PNG): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (image != null) mime else MIME_TEXT
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (image != null) {
                putExtra(Intent.EXTRA_STREAM, image)
                clipData = ClipData.newRawUri(subject, image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(send, chooserTitle)
    }
}
