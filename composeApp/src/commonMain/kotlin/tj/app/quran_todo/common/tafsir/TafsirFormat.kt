package tj.app.quran_todo.common.tafsir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Reading and writing [TafsirChapter]: the answer of quran.com and the compact copy of the app.
 *
 * **The answer** (`GET https://api.quran.com/api/v4/tafsirs/{id}/by_chapter/{surah}?per_page=50&page={n}`, 2:1..286 take six pages):
 * `tafsirs[]` with `verse_key` ("2:19") and `text` (empty for an ayah that shares the text of its group, see [TafsirChapter]) and
 * `pagination` (`next_page`, `total_records`).
 *
 * **The copy of the app** is one JSON file per surah and source: `{"v":1,"s":2,"r":170,"n":286,"t":{"1":"text","2":"text",…}}`, ayahs
 * that have no text left out. It is smaller than the answer (no ids, slugs, keys) and complete when it exists.
 */
object TafsirFormat {
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** One page of the answer for [surah]; null when it is not that answer or has a verse of another surah. */
    fun parsePage(text: String, surah: Int): TafsirPage? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val items = root["tafsirs"] as? JsonArray ?: return null
        val entries = ArrayList<Pair<Int, String>>(items.size)
        for (item in items) {
            val obj = item as? JsonObject ?: return null
            val key = (obj["verse_key"] as? JsonPrimitive)?.contentOrNull ?: return null
            val parts = key.split(':')
            if (parts.size != 2 || parts[0].toIntOrNull() != surah) return null
            val ayah = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            entries += ayah to ((obj["text"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
        val pagination = root["pagination"] as? JsonObject
        return TafsirPage(
            entries = entries,
            surah = surah,
            nextPage = (pagination?.get("next_page") as? JsonPrimitive)?.intOrNull,
            totalRecords = (pagination?.get("total_records") as? JsonPrimitive)?.intOrNull,
        )
    }

    /**
     * The answer of QuranEnc (`GET https://quranenc.com/api/v1/translation/sura/{key}/{sura}`): `result[]` with `sura`, `aya` (both strings),
     * `translation` (the text, untouched: the terms of QuranEnc forbid changes) and `footnotes` (empty for the Mukhtasar). Every ayah has its
     * own text. One page, no pagination.
     */
    fun parseQuranEnc(text: String, surah: Int): TafsirPage? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val items = root["result"] as? JsonArray ?: return null
        val entries = ArrayList<Pair<Int, String>>(items.size)
        for (item in items) {
            val obj = item as? JsonObject ?: return null
            if (obj.str("sura")?.toIntOrNull() != surah) return null
            val ayah = obj.str("aya")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            entries += ayah to obj.str("translation").orEmpty()
        }
        return TafsirPage(entries, surah, nextPage = null, totalRecords = entries.size)
    }

    /** The `version` of the entry [key] in `GET https://quranenc.com/api/v1/translations/list`, or null when the list has no such entry. */
    fun quranEncVersion(text: String, key: String): String? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val items = root["translations"] as? JsonArray ?: return null
        return items.firstNotNullOfOrNull { item ->
            val obj = item as? JsonObject ?: return@firstNotNullOfOrNull null
            if (obj.str("key") == key) obj.str("version")?.takeIf { it.isNotBlank() } else null
        }
    }

    /**
     * The chapter out of all its [pages]; [ayahCount] is the number of ayahs of the surah. Null unless there is at least one entry, no
     * ayah is listed twice and none is beyond [ayahCount] (a damaged answer is never kept). An ayah the answer does not list (Muyassar
     * leaves out the ayahs of a group after the first) and an ayah with an empty text are the same: they belong to the group before them.
     * A surah without any text (Ibn Kathir has none for 105) is a chapter that has no passages.
     */
    fun assemble(surah: Int, source: TafsirSource, pages: List<TafsirPage>, ayahCount: Int, version: String? = null): TafsirChapter? {
        val texts = HashMap<Int, String>()
        var entries = 0
        for (page in pages) {
            if (page.surah != surah) return null
            for ((ayah, text) in page.entries) {
                if (ayah !in 1..ayahCount || texts.put(ayah, text) != null) return null
                entries++
            }
        }
        if (entries == 0) return null
        pages.lastOrNull()?.totalRecords?.let { if (it != entries) return null }
        return TafsirChapter(surah, source, ayahCount, texts, version)
    }

    fun encode(chapter: TafsirChapter): String = buildJsonObject {
        put("v", VERSION)
        put("s", chapter.surah)
        put("r", chapter.source.remoteId)
        chapter.version?.let { put("ver", it) }
        put("n", chapter.ayahCount)
        put(
            "t",
            buildJsonObject { chapter.texts.entries.sortedBy { it.key }.forEach { (ayah, text) -> put(ayah.toString(), text) } },
        )
    }.toString()

    /** Reads [encode]'s output; null for anything else and for a copy of another surah, source or version. */
    fun decode(text: String, surah: Int, source: TafsirSource): TafsirChapter? {
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return null
            if (root.int("v") != VERSION || root.int("s") != surah || root.str("r") != source.remoteId) return null
            val count = root.int("n")?.takeIf { it > 0 } ?: return null
            val entries = root["t"] as? JsonObject ?: return null
            val texts = HashMap<Int, String>()
            for ((key, value) in entries) {
                val ayah = key.toIntOrNull()?.takeIf { it in 1..count } ?: return null
                texts[ayah] = (value as? JsonPrimitive)?.contentOrNull ?: return null
            }
            TafsirChapter(surah, source, count, texts, root.str("ver"))
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
}
