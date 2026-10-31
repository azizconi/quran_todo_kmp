package tj.app.quran_todo.common.tafsir

/**
 * Makes the HTML of a commentary safe for the screen: Ibn Kathir has `<span style="color: rgb(59, 62, 102);">` and the like, which would
 * paint dark blue on the dark theme, and Muyassar has `<span class="green">` around quoted words. Attributes are cut off every tag and
 * `<span>` (and similar wrapper) tags are removed, their content stays; headings, paragraphs, bold and italic stay for
 * `AnnotatedString.fromHtml`. A plain text (as-Sa'di) is returned as it is.
 */
object TafsirHtml {
    private val tag = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*?>")

    /** The text has a tag in it. */
    fun isHtml(text: String): Boolean = tag.containsMatchIn(text)

    fun clean(text: String): String {
        if (!isHtml(text)) return text
        return tag.replace(text) { match ->
            val name = match.groupValues[2].lowercase()
            val closing = match.groupValues[1] == "/"
            when (name) {
                "span", "font", "div", "section", "article", "img", "script", "style", "iframe" -> ""
                "br" -> "<br>"
                else -> if (closing) "</$name>" else "<$name>"
            }
        }
    }

    private val blockEnd = Regex("</(p|h[1-6]|blockquote|li)>")
    private val lineBreaks = Regex("\\s*\\n\\s*")

    /**
     * The text cut into blocks for the screen to draw one by one (a text of 50 000 characters is not laid out as one piece): HTML after
     * every paragraph, heading, quote and list item (each block [clean]ed, so it is HTML again), a plain text at every line break. Blocks
     * that hold nothing are left out.
     */
    fun blocks(text: String): List<String> {
        if (!isHtml(text)) return text.split(lineBreaks).map { it.trim() }.filter { it.isNotEmpty() }
        val cleaned = clean(text)
        val out = ArrayList<String>()
        var start = 0
        for (match in blockEnd.findAll(cleaned)) {
            val end = match.range.last + 1
            out += cleaned.substring(start, end)
            start = end
        }
        if (start < cleaned.length) out += cleaned.substring(start)
        return out.map { it.trim() }.filter { block -> block.replace(tag, "").isNotBlank() }
    }
}
