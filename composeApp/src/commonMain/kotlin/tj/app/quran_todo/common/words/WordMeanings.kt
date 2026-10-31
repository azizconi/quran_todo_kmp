package tj.app.quran_todo.common.words

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** The meaning of one word: [text] in English (the only language quran.com gives) and its [transliteration] when it has one. */
data class WordMeaning(val text: String, val transliteration: String?)

/** What a tap on a word of the screen finds: the meaning and where the word is in the ayah, or that the ayah has none. */
sealed interface WordLookup {
    /** The word number [number] of [total]. */
    data class Found(val meaning: WordMeaning, val number: Int, val total: Int) : WordLookup

    /** The words of the screen and of quran.com do not line up for this ayah (see [WordMeaningsChapter.lookup]). */
    data object Unavailable : WordLookup
}

/**
 * The meanings of the words of one surah: [words] by number of the ayah, each ayah's words in reading order (only `char_type_name` ==
 * `word`, the sign that closes the ayah is not a word).
 */
class WordMeaningsChapter(val surah: Int, val words: Map<Int, List<WordMeaning>>) {
    /**
     * The meaning of the word [index] (0-based, as in `displayWords`) of ayah [ayah], which the screen draws with [wordCount] words.
     * The word of the screen `i` is the word of quran.com with `position = i + 1`. An ayah whose number of words differs is
     * [WordLookup.Unavailable] (a wrong meaning is worse than none): on the bundled text that is 37:130 only (checked on all 6236 ayahs,
     * 2026-10-31); 10:1, 13:1 and 50:34 line up and show their meanings.
     */
    fun lookup(ayah: Int, index: Int, wordCount: Int): WordLookup {
        val list = words[ayah] ?: return WordLookup.Unavailable
        if (list.size != wordCount || index !in list.indices) return WordLookup.Unavailable
        return WordLookup.Found(list[index], index + 1, list.size)
    }
}

/** One page of `verses/by_chapter` reduced to what the meanings need. */
class WordMeaningsPage(val surah: Int, val ayahs: Map<Int, List<WordMeaning>>, val nextPage: Int?, val totalRecords: Int?)

/**
 * Reading and writing [WordMeaningsChapter].
 *
 * **The answer** (`GET https://api.quran.com/api/v4/verses/by_chapter/{surah}?words=true&fields=verse_key&per_page=50&page={n}`, 2:1..286
 * take six pages of about 230 KB): `verses[]` with `verse_key` and `words[]`; a word has `position`, `char_type_name` (`word` or `end`),
 * `translation.text` (English whatever language is asked for, checked for ru, tr, id and ur) and `transliteration.text`.
 *
 * **The copy of the app** is one JSON file per surah: `{"v":1,"s":2,"a":{"1":[["Alif Laam Meem","alif-lam-meem"]],"2":[…]}}`, the
 * words of an ayah in order, a word being `[meaning, transliteration]`.
 */
object WordMeaningsFormat {
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** One page of the answer for [surah]; null when it is not that answer or a word has no meaning or number. */
    fun parsePage(text: String, surah: Int): WordMeaningsPage? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val verses = root["verses"] as? JsonArray ?: return null
        val ayahs = LinkedHashMap<Int, List<WordMeaning>>()
        for (element in verses) {
            val verse = element as? JsonObject ?: return null
            val key = (verse["verse_key"] as? JsonPrimitive)?.contentOrNull ?: return null
            val parts = key.split(':')
            if (parts.size != 2 || parts[0].toIntOrNull() != surah) return null
            val ayah = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val words = verse["words"] as? JsonArray ?: return null
            val list = ArrayList<WordMeaning>(words.size)
            var expected = 1
            for (item in words) {
                val word = item as? JsonObject ?: return null
                if (word.string("char_type_name") != "word") continue
                // The words come in order and without gaps: the screen's word i is the word with position i + 1.
                if (word.int("position") != expected) return null
                val meaning = (word["translation"] as? JsonObject)?.string("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val transliteration = (word["transliteration"] as? JsonObject)?.string("text")?.trim()?.takeIf { it.isNotEmpty() }
                list += WordMeaning(meaning, transliteration)
                expected++
            }
            ayahs[ayah] = list
        }
        val pagination = root["pagination"] as? JsonObject
        return WordMeaningsPage(
            surah = surah,
            ayahs = ayahs,
            nextPage = (pagination?.get("next_page") as? JsonPrimitive)?.intOrNull,
            totalRecords = (pagination?.get("total_records") as? JsonPrimitive)?.intOrNull,
        )
    }

    /** The surah out of all its [pages]; null unless the ayahs are 1..n, each once, and none is empty. */
    fun assemble(surah: Int, pages: List<WordMeaningsPage>): WordMeaningsChapter? {
        val all = HashMap<Int, List<WordMeaning>>()
        for (page in pages) {
            if (page.surah != surah) return null
            for ((ayah, list) in page.ayahs) if (all.put(ayah, list) != null) return null
        }
        val count = all.keys.maxOrNull() ?: return null
        if (all.size != count || all.values.any { it.isEmpty() }) return null
        pages.lastOrNull()?.totalRecords?.let { if (it != count) return null }
        return WordMeaningsChapter(surah, all)
    }

    fun encode(chapter: WordMeaningsChapter): String = buildJsonObject {
        put("v", VERSION)
        put("s", chapter.surah)
        put(
            "a",
            buildJsonObject {
                chapter.words.entries.sortedBy { it.key }.forEach { (ayah, list) ->
                    put(
                        ayah.toString(),
                        buildJsonArray {
                            for (word in list) {
                                add(
                                    buildJsonArray {
                                        add(JsonPrimitive(word.text))
                                        add(JsonPrimitive(word.transliteration.orEmpty()))
                                    },
                                )
                            }
                        },
                    )
                }
            },
        )
    }.toString()

    /** Reads [encode]'s output; null for anything else and for a copy of another surah or version. */
    fun decode(text: String, surah: Int): WordMeaningsChapter? {
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return null
            if (root.int("v") != VERSION || root.int("s") != surah) return null
            val entries = root["a"] as? JsonObject ?: return null
            val all = HashMap<Int, List<WordMeaning>>()
            for ((key, value) in entries) {
                val ayah = key.toIntOrNull()?.takeIf { it > 0 } ?: return null
                val list = (value as? JsonArray ?: return null).map { item ->
                    val pair = item as? JsonArray ?: return null
                    val meaning = (pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: return null
                    val transliteration = (pair.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
                    WordMeaning(meaning, transliteration)
                }
                all[ayah] = list
            }
            val count = all.keys.maxOrNull() ?: return null
            if (all.size != count) return null
            WordMeaningsChapter(surah, all)
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
}
