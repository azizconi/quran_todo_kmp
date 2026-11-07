package tj.app.quran_todo.ui.progress.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.OutputStream
import tj.app.quran_todo.ui.progress.analytics.Analytics
import tj.app.quran_todo.ui.theme.QhColors

/**
 * The report as a PDF (`android.graphics.pdf.PdfDocument`), A4, the same [ReportDoc] as the text and the card, so all three say the
 * same. The page is plain: the brand line, the title and period, the memorized share as a bar, the rows, the heat strip. The text
 * flows onto a second page when it does not fit; a month of many surahs is the longest report and fits two.
 *
 * Units are PostScript points (1/72 inch): an A4 page is 595 x 842. The colours are the light tokens of the app (a printed page is
 * always light). No star shapes: the only ornament is a hairline.
 */
internal object ReportPdf {
    const val PageWidth = 595
    const val PageHeight = 842
    private const val Margin = 40f

    /** Writes [doc] to [out] and returns how many pages it took. Safe off the main thread (no composition is touched). */
    fun render(doc: ReportDoc, colors: QhColors, out: OutputStream): Int {
        val pdf = PdfDocument()
        try {
            val writer = Writer(pdf, colors)
            writer.draw(doc)
            val pages = writer.finish()
            pdf.writeTo(out)
            return pages
        } finally {
            pdf.close()
        }
    }

    private class Writer(private val pdf: PdfDocument, private val colors: QhColors) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = Margin
        private val width = PageWidth - 2 * Margin

        private fun paint(size: Float, bold: Boolean, color: Color, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color.toArgb()
            letterSpacing = spacing
        }

        private fun layout(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(align)
                .setLineSpacing(0f, 1.15f)
                .build()

        private fun newPage() {
            page?.let { pdf.finishPage(it) }
            number++
            val next = pdf.startPage(PdfDocument.PageInfo.Builder(PageWidth, PageHeight, number).create())
            page = next
            canvas = next.canvas
            y = Margin
        }

        /** Makes room for a block of [height] points: starts the next page when it would run over the bottom margin. */
        private fun ensure(height: Float) {
            if (page == null || y + height > PageHeight - Margin) newPage()
        }

        private fun text(text: String, paint: TextPaint, gapAfter: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val l = layout(text, paint, align)
            ensure(l.height.toFloat())
            val c = canvas!!
            c.save()
            c.translate(Margin, y)
            l.draw(c)
            c.restore()
            y += l.height + gapAfter
        }

        fun draw(doc: ReportDoc) {
            newPage()
            val center = Layout.Alignment.ALIGN_CENTER
            text("QURAN HIFZ", paint(10f, true, colors.inkSecondary, spacing = 0.25f), gapAfter = 10f, align = center)
            text(doc.title, paint(24f, true, colors.ink), gapAfter = 4f, align = center)
            text(doc.period, paint(13f, false, colors.inkSecondary), gapAfter = 16f, align = center)
            rule(colors.gold, gapAfter = 18f)

            text(doc.percentText, paint(34f, true, colors.ink), gapAfter = 2f, align = center)
            text(doc.caption, paint(13f, false, colors.ink), gapAfter = 10f, align = center)
            bar(doc.fraction)

            doc.rows.forEach { row ->
                rule(colors.hairline, gapAfter = 12f)
                // Keep the label with its value and the first line.
                ensure(60f)
                text(row.label, paint(11f, true, colors.inkSecondary), gapAfter = 2f)
                if (row.value.isNotBlank()) text(row.value, paint(16f, true, colors.ink), gapAfter = 3f)
                for (line in row.lines) {
                    text(if (row.bulleted) "•  $line" else line, paint(12f, false, colors.ink), gapAfter = 2f)
                }
                y += 6f
            }

            if (doc.strip.isNotEmpty()) {
                rule(colors.hairline, gapAfter = 12f)
                val cell = 11f
                val gap = 3f
                val perRow = ((width + gap) / (cell + gap)).toInt().coerceAtLeast(1)
                val rows = (doc.strip.size + perRow - 1) / perRow
                ensure(24f + rows * (cell + gap))
                text(doc.stripLabel, paint(11f, true, colors.inkSecondary), gapAfter = 6f)
                val c = canvas!!
                val max = doc.strip.max()
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                doc.strip.forEachIndexed { index, count ->
                    val left = Margin + (index % perRow) * (cell + gap)
                    val top = y + (index / perRow) * (cell + gap)
                    fill.color = stripColor(Analytics.level(count, max)).toArgb()
                    c.drawRoundRect(RectF(left, top, left + cell, top + cell), 2f, 2f, fill)
                }
                y += rows * (cell + gap)
            }
        }

        private fun stripColor(level: Int): Color = when (level) {
            0 -> colors.surfaceStrong
            1 -> colors.primary.copy(alpha = 0.3f)
            2 -> colors.primary.copy(alpha = 0.55f)
            3 -> colors.primary.copy(alpha = 0.8f)
            else -> colors.primary
        }

        private fun rule(color: Color, gapAfter: Float) {
            ensure(1f + gapAfter)
            val p = Paint().apply { this.color = color.toArgb(); strokeWidth = 0.8f }
            canvas!!.drawLine(Margin, y, Margin + width, y, p)
            y += 1f + gapAfter
        }

        private fun bar(fraction: Float) {
            ensure(14f)
            val c = canvas!!
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surfaceStrong.toArgb() }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.learned.toArgb() }
            val h = 8f
            c.drawRoundRect(RectF(Margin, y, Margin + width, y + h), h / 2, h / 2, track)
            if (fraction > 0f) {
                // A visible start even for a tiny share, like the ring on the screen.
                val w = (width * fraction).coerceAtLeast(h)
                c.drawRoundRect(RectF(Margin, y, Margin + w, y + h), h / 2, h / 2, fill)
            }
            y += h + 14f
        }

        fun finish(): Int {
            page?.let { pdf.finishPage(it) }
            page = null
            return number
        }
    }
}
