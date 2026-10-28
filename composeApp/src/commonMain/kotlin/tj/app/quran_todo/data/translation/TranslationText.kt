package tj.app.quran_todo.data.translation

/** Text helpers for translations that come from the network. */
object TranslationText {
    private val FootnoteTag = Regex("<sup\\b[^>]*>[\\s\\S]*?</sup>", RegexOption.IGNORE_CASE)
    private val LineBreakTag = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val AnyTag = Regex("<[^>]*>")
    private val Entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val Whitespace = Regex("\\s+")

    /**
     * Cleans a translation of quran.com v4 for display: drops the footnote markers
     * (`<sup foot_note="170564">1</sup>`, together with their number), any other tag, decodes the few HTML
     * entities that can occur and collapses runs of spaces.
     */
    fun cleanQuranCom(raw: String): String {
        var text = raw
        if ('<' in text) {
            text = FootnoteTag.replace(text, "")
            text = LineBreakTag.replace(text, " ")
            text = AnyTag.replace(text, "")
        }
        if ('&' in text) text = Entity.replace(text) { decodeEntity(it) }
        return Whitespace.replace(text, " ").trim()
    }

    /** True when [text] has at least one Cyrillic letter (U+0400..U+04FF). */
    fun containsCyrillic(text: String): Boolean = text.any { it.code in 0x0400..0x04FF }

    private fun decodeEntity(match: MatchResult): String {
        val body = match.groupValues[1]
        return when {
            body.startsWith("#x") || body.startsWith("#X") -> codePointOrSelf(match, body.drop(2).toIntOrNull(16))
            body.startsWith("#") -> codePointOrSelf(match, body.drop(1).toIntOrNull())
            else -> when (body.lowercase()) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> match.value
            }
        }
    }

    private fun codePointOrSelf(match: MatchResult, code: Int?): String =
        if (code != null && code in 1..0xFFFF && code !in 0xD800..0xDFFF) code.toChar().toString() else match.value
}
