package tj.app.quran_todo.ui.home

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.common.JuzIndex
import tj.app.quran_todo.ui.common.JuzRange
import tj.app.quran_todo.ui.i18n.ayahCountLabel

/** The tabs of the list under the search field. */
/** "Translation" (task F4a) exists only while the person searches: the third segment of the switch under the field. */
enum class HomeTab { Surahs, Juz, Translation }

/** One of the 30 rows of the "Juz" tab (D1). */
@Immutable
data class JuzRowUi(
    val juz: Int,
    /** The traditional name: the opening words of the juz, in Arabic. */
    val arabicName: String,
    /** The surah and the ayah (numbers in the surah) the juz starts at; the reader opens there. */
    val startSurah: Int,
    val startAyah: Int,
    /** Names of the first and the last surah in the app language (the search reads them). */
    val startName: String,
    val endName: String,
    /** "An-Naba 1 — An-Nas 6 · 564 ayahs". */
    val subtitle: String,
    val ayahCount: Int,
    /** Memorized (covered) ayahs inside the juz. */
    val coveredCount: Int,
    val canOpen: Boolean,
) {
    /** Memorized ayahs / all ayahs of the juz. */
    val fraction: Float get() = if (ayahCount <= 0) 0f else (coveredCount.toFloat() / ayahCount).coerceIn(0f, 1f)

    /** The badge of the row: a solid medallion when the whole juz is memorized, the ring while some of it is. */
    val status: SurahTodoStatus?
        get() = when {
            ayahCount > 0 && coveredCount >= ayahCount -> SurahTodoStatus.LEARNED
            coveredCount > 0 -> SurahTodoStatus.LEARNING
            else -> null
        }
}

/** The rows of the "Juz" tab and its search. Pure: the ViewModel builds the rows off the main thread. */
internal object JuzRows {
    /**
     * Where a juz row opens the reader from (`Dest.Reader.origin`). Its own value on purpose: the shared element keys of the
     * reader's header are built from the origin and the surah number, and no juz row has such keys, so nothing flies.
     */
    const val ORIGIN = "juz"

    /**
     * The 30 rows. [rowsByNumber] gives the names of the surahs in the app language (and whether the surah can be opened),
     * [covered] is `HifzRepository.coveredAyahs()` (surah -> covered ayah numbers): one map for all 30 juz.
     */
    fun build(
        language: AppLanguage,
        rowsByNumber: Map<Int, SurahRowUi>,
        covered: Map<Int, Set<Int>>,
    ): List<JuzRowUi> = (1..JuzIndex.COUNT).map { juz ->
        val range = JuzIndex.range(juz)
        val count = JuzIndex.ayahCount(juz)
        val nameOf = { surah: Int -> rowsByNumber[surah]?.name ?: surah.toString() }
        JuzRowUi(
            juz = juz,
            arabicName = JuzIndex.arabicName(juz),
            startSurah = range.startSurah,
            startAyah = range.startAyah,
            startName = nameOf(range.startSurah),
            endName = nameOf(range.endSurah),
            subtitle = subtitle(range, count, language, nameOf),
            ayahCount = count,
            coveredCount = JuzIndex.coveredCount(juz, covered),
            canOpen = rowsByNumber[range.startSurah]?.canOpen == true,
        )
    }

    /**
     * "An-Naba 1 — An-Nas 6 · 564 ayahs": where the juz starts and ends and how many ayahs it has. A juz inside one surah
     * (the second juz ends in Al-Baqarah too) reads "Al-Baqarah 142–252 · 111 ayahs".
     */
    fun subtitle(range: JuzRange, ayahCount: Int, language: AppLanguage, nameOf: (Int) -> String): String {
        val places = if (range.startSurah == range.endSurah) {
            "${nameOf(range.startSurah)} ${range.startAyah}–${range.endAyah}"
        } else {
            "${nameOf(range.startSurah)} ${range.startAyah} — ${nameOf(range.endSurah)} ${range.endAyah}"
        }
        return "$places · ${ayahCountLabel(ayahCount, language)}"
    }

    // -- Search -----------------------------------------------------------------------------------

    /**
     * The words that mean "juz" in the five languages, as [SurahSearch.normalize] writes them (lower case, no diacritics, the
     * Tajik "ҷ" folded to "ч"): "juz 30", "джуз 30", "ҷузъи 30", "cüz 30" and "جزء 30" all find the 30th.
     */
    private val JuzWords = listOf("juz", "джуз", "чуз", "чузъ", "чузъи", "жуз", "cuz", "جزء", "جز")

    /**
     * The rows that match [query] on the "Juz" tab; a blank query matches every row. Understood:
     * - the number: "30", "juz 30", "джуз 30", "juz30", "30-juz" (a lone "juz" matches all);
     * - an ayah reference "2:255": the juz that holds it;
     * - words of the Arabic name of the juz ("عم") and of the names of the surah it starts and the one it ends with.
     */
    fun search(rows: List<JuzRowUi>, query: String): List<JuzRowUi> {
        val normalized = SurahSearch.normalize(query)
        if (normalized.isEmpty()) return rows

        SurahSearch.parseAyahReference(query)?.let { reference ->
            val juz = JuzIndex.juzOf(reference.surah, reference.ayah)
            return rows.filter { it.juz == juz }
        }

        val words = normalized.split(' ').filter { it.isNotEmpty() }
        val first = words.first()
        // The leading "juz" word, alone ("juz"), glued to the number ("juz30") or followed by it ("juz 30").
        val juzWord = JuzWords.firstOrNull { word -> first.startsWith(word) && first.drop(word.length).all(Char::isDigit) }
        val numberText = when {
            juzWord != null -> {
                val glued = first.removePrefix(juzWord)
                when {
                    words.size == 1 && glued.isEmpty() -> return rows
                    words.size == 1 -> glued
                    words.size == 2 && glued.isEmpty() -> words[1]
                    else -> null
                }
            }
            words.size == 1 -> first
            // "30-juz" (Uzbek), "30. cüz" (Turkish): the number first.
            words.size == 2 && first.all(Char::isDigit) && words[1] in JuzWords -> first
            else -> null
        }
        if (numberText != null && numberText.all(Char::isDigit)) {
            val number = numberText.toIntOrNull() ?: return emptyList()
            return rows.filter { it.juz == number }
        }

        val text = words.filterNot { it in JuzWords }.joinToString(" ")
        if (text.isEmpty()) return rows
        return rows.filter { row ->
            SurahSearch.normalize(row.arabicName).contains(text) ||
                SurahSearch.normalize(row.startName).contains(text) ||
                SurahSearch.normalize(row.endName).contains(text)
        }
    }
}
