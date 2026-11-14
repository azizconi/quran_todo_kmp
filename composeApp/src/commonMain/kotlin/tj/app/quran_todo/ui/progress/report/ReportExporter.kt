package tj.app.quran_todo.ui.progress.report

/**
 * Makes the progress report as a file and hands it to the system: the PDF (`ReportDoc` laid out on pages) and the image of the ring.
 *
 * Android keeps `ReportPdf` (android.graphics.pdf.PdfDocument) and `ReportShare` (FileProvider and the share sheet) as they are: they
 * draw with the Android theme colours (`QhColors`), so they are not behind this interface. iOS (S2):
 * `UIGraphicsPDFRenderer` draws the same `ReportDoc` (title, rows, ring, the colours of the report), the file goes to
 * `NSTemporaryDirectory()` and `UIActivityViewController` shares it.
 */
interface ReportExporter {
    /** Writes the PDF of [doc] to a temporary file and returns its location (a `file://` url or a content uri); null on failure. */
    suspend fun createPdf(doc: ReportDoc, fileName: String): String?

    /** Opens the system share sheet for [location] (as returned by [createPdf]). */
    fun share(location: String, mimeType: String, title: String)
}
