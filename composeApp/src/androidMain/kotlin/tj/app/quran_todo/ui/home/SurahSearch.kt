package tj.app.quran_todo.ui.home

import java.text.Normalizer

/** A reference to one ayah typed into the search field: `2:255`, `2 255` or `2.255`. */
data class AyahReference(val surah: Int, val ayah: Int)

/** What the search looks at for one surah. All strings are raw (un-normalized) display text. */
data class SearchItem(
    val number: Int,
    /** Names people type: the displayed name, the Latin transliteration, the Russian name. */
    val names: List<String>,
    /** The meaning of the name ("The Cow", "Корова"); ranks just below a name hit. */
    val meanings: List<String> = emptyList(),
    /** The Arabic name. */
    val arabic: List<String> = emptyList(),
)

/**
 * Fuzzy search over the surah list (docs/redesign/ANDROID_REDESIGN.md, 6.3): case, hyphens,
 * apostrophes, diacritics and the leading article (аль-, al-, an-, ash-, ...) do not matter, so
 * "бакара" finds "Аль-Бакара" and "shuara" finds "Ash-Shu'ara". Latin and Cyrillic are compared as
 * they are (no transliteration between scripts); Arabic loses its vowel marks and tatweel.
 */
object SurahSearch {
    private val Articles = setOf(
        "аль", "ал", "ан", "ар", "ас", "ат", "аз", "ад", "аш",
        "al", "an", "ar", "as", "at", "az", "ad", "ash", "ath", "adh", "el", "the",
    )

    /** "Сура Мухаммад", "Surah Yasin": a leading word that is not part of the name. */
    private val SurahWords = setOf("сура", "суры", "surah", "sura", "sure", "surasi", "سوره")

    private const val Apostrophes = "'’‘ʻʼʽʾʿ`´′ˈ"

    /**
     * Lower-cases, strips combining marks (accents, Arabic vowel marks, U+0670), tatweel and
     * apostrophes, folds look-alike letters, turns hyphens and punctuation into single spaces. The
     * leading article stays; see [nameKey] for the article-less form.
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (raw in decomposed) {
            if (Character.getType(raw) == Character.NON_SPACING_MARK.toInt()) continue
            if (raw == 'ـ' || Apostrophes.indexOf(raw) >= 0) continue
            val ch = fold(raw)
            if (ch.isLetterOrDigit()) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(ch)
            } else {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    private fun fold(ch: Char): Char = when (ch) {
        'ٱ' -> 'ا' // alef wasla -> alef
        'ى', 'ی' -> 'ي' // alef maqsura, Persian yeh -> yeh
        'ة' -> 'ه' // teh marbuta -> heh
        'ک' -> 'ك' // Persian kaf -> kaf
        'ı' -> 'i' // dotless i
        'ғ' -> 'г' // Tajik/Uzbek ғ -> г
        'қ' -> 'к' // қ -> к
        'ҳ' -> 'х' // ҳ -> х
        'ҷ' -> 'ч' // ҷ -> ч
        in '٠'..'٩' -> '0' + (ch - '٠') // Arabic-Indic digits
        in '۰'..'۹' -> '0' + (ch - '۰') // extended Arabic-Indic digits
        else -> ch
    }

    /** A surah name or a query reduced to what is compared. */
    internal class NameKey(
        /** Words without the leading article / "surah" word. */
        val words: List<String>,
        /** [words] joined without spaces: "bakara". */
        val collapsed: String,
        /** All words joined without spaces, article included: "albakara". */
        val full: String,
    )

    internal fun nameKey(raw: String): NameKey? {
        val all = normalize(raw).split(' ').filter { it.isNotEmpty() }
        if (all.isEmpty()) return null
        var words = all
        while (words.size > 1 && words.first() in SurahWords) words = words.drop(1)
        if (words.size > 1 && words.first() in Articles) words = words.drop(1)
        if (words.any { word -> word.any { it in '؀'..'ۿ' } }) {
            // Arabic glues the article to the word: "البقره" -> "بقره".
            val first = words.first()
            if (first.length > 2 && first.startsWith("ال")) words = listOf(first.drop(2)) + words.drop(1)
        }
        return NameKey(words = words, collapsed = words.joinToString(""), full = all.joinToString(""))
    }

    /**
     * Words of a surah name as shown in a search: [normalize]d and without the leading article.
     * "Ash-Shu'ara" -> "shuara", "Ya-Sin" -> "ya sin".
     */
    fun normalizeName(raw: String): String = nameKey(raw)?.words?.joinToString(" ").orEmpty()

    /**
     * Parses `2:255`, `2 255`, `2.255` (also `2,255`, `2/255`, and Arabic-Indic digits). Returns null
     * when the text is not a pair of numbers or when it points outside the Quran: the surah must be
     * 1..114 and the ayah 1..[ayahCountOf] (surah).
     */
    fun parseAyahReference(
        query: String,
        ayahCountOf: (surah: Int) -> Int = QuranMeta::ayahCount,
    ): AyahReference? {
        val match = ReferencePattern.matchEntire(asciiDigits(query.trim())) ?: return null
        val surah = match.groupValues[1].toIntOrNull() ?: return null
        val ayah = match.groupValues[2].toIntOrNull() ?: return null
        if (surah !in 1..QuranMeta.SurahCount) return null
        if (ayah !in 1..ayahCountOf(surah)) return null
        return AyahReference(surah, ayah)
    }

    private val ReferencePattern = Regex("""(\d{1,3})\s*[:.,/\s]\s*(\d{1,3})""")

    private fun asciiDigits(text: String): String = buildString(text.length) {
        for (ch in text) append(fold(ch).let { if (it in '0'..'9') it else ch })
    }

    /** Search structure for one list of surahs. Build it once per list ([build]) and query it often. */
    class Index private constructor(private val entries: List<Entry>) {
        private class Entry(val number: Int, val names: List<NameKey>, val meanings: List<NameKey>, val arabic: List<NameKey>)

        /**
         * Surah numbers matching [query], best match first (ties by surah number). A blank query
         * matches everything in the original order. A query of digits only is a surah number.
         */
        fun search(query: String): List<Int> {
            val normalized = normalize(query)
            if (normalized.isEmpty()) return entries.map { it.number }
            if (normalized.all { it in '0'..'9' }) {
                val number = normalized.toIntOrNull() ?: return emptyList()
                return entries.filter { it.number == number }.map { it.number }
            }
            val key = nameKey(query) ?: return entries.map { it.number }
            return entries
                .mapNotNull { entry -> entry.rank(key)?.let { rank -> entry.number to rank } }
                .sortedWith(compareBy({ it.second }, { it.first }))
                .map { it.first }
        }

        private fun Entry.rank(query: NameKey): Int? {
            var best: Int? = null
            fun consider(rank: Int?) {
                if (rank != null && (best == null || rank < best!!)) best = rank
            }
            names.forEach { consider(rankOf(it, query)) }
            arabic.forEach { consider(rankOf(it, query)) }
            // A match on the meaning of a name is worth a little less than on the name itself.
            meanings.forEach { consider(rankOf(it, query)?.plus(1)) }
            return best
        }

        companion object {
            fun build(items: List<SearchItem>): Index = Index(
                items.map { item ->
                    Entry(
                        number = item.number,
                        names = item.names.mapNotNull(::nameKey),
                        meanings = item.meanings.mapNotNull(::nameKey),
                        arabic = item.arabic.mapNotNull(::nameKey),
                    )
                },
            )
        }
    }

    /** 1 exact, 2 starts with, 3 a word starts with, 4 contains; null when it does not match. */
    private fun rankOf(candidate: NameKey, query: NameKey): Int? = when {
        candidate.collapsed == query.collapsed || candidate.full == query.full -> 1
        candidate.collapsed.startsWith(query.collapsed) || candidate.full.startsWith(query.full) -> 2
        candidate.words.any { it.startsWith(query.collapsed) } -> 3
        candidate.collapsed.contains(query.collapsed) || candidate.full.contains(query.full) -> 4
        else -> null
    }
}
