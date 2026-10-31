package tj.app.quran_todo.data.translation

/**
 * The text of a search made comparable: lower case, no accents, the letters of the five languages brought to their base letters, so
 * that "Милостив" finds "милостив", "ёлка" is found by "елка", "Kapı" by "kapi", "ŞAHİN" by "sahin" and the Tajik "Ҳамду" by "хамду".
 *
 * Every character of the text becomes one character or none (apostrophes and combining marks vanish), so a hit in the normalized text
 * can be put back on the original with [NormalizedText.originalRange].
 */
object SearchText {
    /** The base letter of [c] for a search: null when the character does not take part in a search at all (it is dropped). */
    private fun fold(c: Char): Char? {
        val code = c.code
        // Combining marks (accents, Arabic harakat) and the apostrophes of Uzbek (o', g'), Arabic transliteration and quotes.
        if (code in 0x0300..0x036F || code in 0x064B..0x065F || code == 0x0670) return null
        return when (c) {
            '\'', '`', '‘', '’', 'ʻ', 'ʼ', 'ʿ', '´', 'ʹ' -> null
            // Turkish and the dotted/dotless i.
            'İ', 'I', 'ı', 'î', 'Î', 'ï', 'ì', 'í', 'ī', 'Ī' -> 'i'
            'â', 'Â', 'à', 'á', 'ä', 'ã', 'ā', 'Ā', 'À', 'Á', 'Ä' -> 'a'
            'û', 'Û', 'ü', 'Ü', 'ù', 'ú', 'ū', 'Ū' -> 'u'
            'ö', 'Ö', 'ò', 'ó', 'ô', 'ō' -> 'o'
            'ç', 'Ç' -> 'c'
            'ğ', 'Ğ' -> 'g'
            'ş', 'Ş', 'ṣ', 'Ṣ' -> 's'
            'è', 'é', 'ê', 'ë', 'ē', 'È', 'É' -> 'e'
            'ḥ', 'Ḥ' -> 'h'
            'ḍ', 'Ḍ' -> 'd'
            'ṭ', 'Ṭ' -> 't'
            'ẓ', 'Ẓ' -> 'z'
            // Russian and Tajik (Cyrillic): yo, long i, long u, and the letters Tajik adds to the Russian alphabet.
            'ё', 'Ё' -> 'е' // ё -> е
            'ӣ', 'Ӣ' -> 'и' // ӣ -> и
            'ӯ', 'Ӯ' -> 'у' // ӯ -> у
            'ҳ', 'Ҳ' -> 'х' // ҳ -> х
            'қ', 'Қ' -> 'к' // қ -> к
            'ғ', 'Ғ' -> 'г' // ғ -> г
            'ҷ', 'Ҷ' -> 'ч' // ҷ -> ч
            else -> c.lowercaseChar()
        }
    }

    /** [text] folded, with the position of every kept character in [text]. */
    fun normalize(text: String): NormalizedText {
        val out = StringBuilder(text.length)
        val origin = IntArray(text.length)
        for (index in text.indices) {
            val folded = fold(text[index]) ?: continue
            origin[out.length] = index
            out.append(folded)
        }
        return NormalizedText(out.toString(), origin.copyOf(out.length))
    }

    /** Just the folded string. */
    fun fold(text: String): String = normalize(text).text

    /** The words of a query, folded; empty words are gone. */
    fun tokens(query: String): List<String> = fold(query).split(' ', '\t', '\n').filter { it.isNotEmpty() }

    /** A query is searched from this many letters (without counting spaces). */
    const val MIN_QUERY_LENGTH = 2

    fun isSearchable(query: String): Boolean = tokens(query).sumOf { it.length } >= MIN_QUERY_LENGTH
}

/** The folded text and, for each of its characters, where it was in the original. */
class NormalizedText(val text: String, private val origin: IntArray) {
    /** The part of the original that the folded range [start, endExclusive) stands for. */
    fun originalRange(start: Int, endExclusive: Int): IntRange = origin[start]..origin[endExclusive - 1]
}

/** One result of a search in the translation. */
class TranslationHit(
    val ayahNumber: Int,
    val surah: Int,
    val ayahInSurah: Int,
    val text: String,
    /** Where the query is in [text]: sorted, not overlapping. */
    val ranges: List<IntRange>,
)

/** [hits] in the order of the Quran, at most the limit; [truncated] says that there were more. */
class TranslationSearchResult(val hits: List<TranslationHit>, val truncated: Boolean) {
    companion object {
        val Empty = TranslationSearchResult(emptyList(), false)
    }
}

/**
 * A search in the text of one translation, in memory: the 6236 rows are folded once; a query is the words of the field, an ayah
 * is a hit when it has every word (as a part of a word too: "милост" finds "милостивый"). No index in the database.
 */
class TranslationSearchIndex(texts: Map<Int, String>) {
    private class Row(val ayahNumber: Int, val text: String, val folded: NormalizedText)

    private val rows: List<Row> = texts.entries.sortedBy { it.key }.map { Row(it.key, it.value, SearchText.normalize(it.value)) }

    val size: Int get() = rows.size

    fun search(query: String, limit: Int = DEFAULT_LIMIT): TranslationSearchResult {
        val tokens = SearchText.tokens(query)
        if (tokens.sumOf { it.length } < SearchText.MIN_QUERY_LENGTH) return TranslationSearchResult.Empty
        val hits = ArrayList<TranslationHit>()
        for (row in rows) {
            val found = rangesOf(row, tokens) ?: continue
            if (hits.size == limit) return TranslationSearchResult(hits, truncated = true)
            val (surah, inSurah) = WholeTranslation.locate(row.ayahNumber) ?: continue
            hits += TranslationHit(row.ayahNumber, surah, inSurah, row.text, found)
        }
        return TranslationSearchResult(hits, truncated = false)
    }

    /** The places of every word in [row], merged; null when one of the words is missing. */
    private fun rangesOf(row: Row, tokens: List<String>): List<IntRange>? {
        val folded = row.folded
        val places = ArrayList<IntRange>()
        for (token in tokens) {
            var from = 0
            var any = false
            while (true) {
                val at = folded.text.indexOf(token, from)
                if (at < 0) break
                any = true
                places += folded.originalRange(at, at + token.length)
                from = at + token.length
            }
            if (!any) return null
        }
        places.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        for (place in places) {
            val last = merged.lastOrNull()
            if (last != null && place.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, place.last)
            } else {
                merged += place
            }
        }
        return merged
    }

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}
