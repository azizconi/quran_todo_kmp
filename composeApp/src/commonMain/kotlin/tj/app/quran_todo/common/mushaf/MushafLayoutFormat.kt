package tj.app.quran_todo.common.mushaf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Reading and writing [MushafPage]: the answer of quran.com and the compact copy the app keeps on the phone.
 *
 * **The answer** (`GET https://api.quran.com/api/v4/verses/by_page/{page}?words=true&word_fields=code_v1,line_number,page_number&per_page=50`,
 * all 604 pages checked on 2026-10-31): `verses[]` in reading order, each with `verse_key` ("2:6") and `words[]`. Every word has
 * `position` (from 1), `char_type_name` (`word`, or `end` for the sign that closes the ayah: the only two values in the whole Quran),
 * `code_v1` (the glyph of the font of the page) and `line_number` (1..15). A verse is whole on one page (no ayah crosses a page), the
 * answer has no more than 42 verses and fits one request. Words of another type or of another page are skipped; a word without a
 * glyph or a line makes the answer unusable (null), so that a damaged page is fetched again, not drawn with holes.
 *
 * **The copy of the app** is one small JSON file per page: `{"v":2,"p":3,"l":[["w",1,[2,6,1,"ﭑ",0],…],["h",2,2],["b",3,2]]}`, a line
 * being `["w", number, word…]` (a word is `[surah, ayah, position, glyph, 1 for the end sign]`), `["h", number, surah]` (header) or
 * `["b", number, surah]` (Bismillah). The headers and Bismillahs are in it already ([MushafLayoutRules]), so drawing needs no rule.
 */
object MushafLayoutFormat {
    /** 2: the star of the rub el hizb is cut off the glyphs (version 1 copies had it and are fetched again). */
    const val VERSION = 2

    private val json = Json { ignoreUnknownKeys = true }

    /** Reads the answer of quran.com for [page]; null when it is not that answer or has no usable words. */
    fun parseQuranCom(text: String, page: Int): MushafPage? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val verses = root["verses"] as? JsonArray ?: return null
        val count = MushafLayoutRules.lineCount(page)
        val byLine = LinkedHashMap<Int, MutableList<MushafWord>>()
        for (element in verses) {
            val verse = element as? JsonObject ?: continue
            val key = AyahKey.parse(verse.string("verse_key")) ?: return null
            val words = verse["words"] as? JsonArray ?: return null
            for (item in words) {
                val word = item as? JsonObject ?: return null
                val type = word.string("char_type_name")
                if (type != "word" && type != "end") continue
                val onPage = word.int("page_number")
                if (onPage != null && onPage != page) continue
                val line = word.int("line_number") ?: return null
                val position = word.int("position") ?: return null
                val glyph = word.string("code_v1")?.takeIf { it.isNotEmpty() }
                    ?.let { MushafLayoutRules.withoutRubSign(key.surah, key.ayah, position, it) } ?: return null
                if (line !in 1..count) return null
                byLine.getOrPut(line) { ArrayList() } += MushafWord(key.surah, key.ayah, position, glyph, isAyahEnd = type == "end")
            }
        }
        if (byLine.isEmpty()) return null
        return MushafPage(page, MushafLayoutRules.arrange(page, byLine))
    }

    /** The compact copy of the app, see the class comment. */
    fun encode(page: MushafPage): String = buildJsonObject {
        put("v", VERSION)
        put("p", page.number)
        put(
            "l",
            buildJsonArray {
                for (line in page.lines) {
                    add(
                        buildJsonArray {
                            when (line) {
                                is MushafLine.Words -> {
                                    add(JsonPrimitive("w"))
                                    add(JsonPrimitive(line.number))
                                    for (word in line.words) {
                                        add(
                                            buildJsonArray {
                                                add(JsonPrimitive(word.surah))
                                                add(JsonPrimitive(word.ayah))
                                                add(JsonPrimitive(word.position))
                                                add(JsonPrimitive(word.glyph))
                                                add(JsonPrimitive(if (word.isAyahEnd) 1 else 0))
                                            },
                                        )
                                    }
                                }
                                is MushafLine.SurahHeader -> {
                                    add(JsonPrimitive("h"))
                                    add(JsonPrimitive(line.number))
                                    add(JsonPrimitive(line.surah))
                                }
                                is MushafLine.Bismillah -> {
                                    add(JsonPrimitive("b"))
                                    add(JsonPrimitive(line.number))
                                    add(JsonPrimitive(line.surah))
                                }
                            }
                        },
                    )
                }
            },
        )
    }.toString()

    /** Reads [encode]'s output; null for anything else, for a copy of another version and for a copy of another page than [page]. */
    fun decode(text: String, page: Int): MushafPage? {
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return null
            if (root.int("v") != VERSION || root.int("p") != page) return null
            val count = MushafLayoutRules.lineCount(page)
            val lines = (root["l"] as? JsonArray ?: return null).map { element ->
                val parts = element as? JsonArray ?: return null
                val kind = (parts.getOrNull(0) as? JsonPrimitive)?.contentOrNull
                val number = parts.getOrNull(1).asInt() ?: return null
                if (number !in 1..count) return null
                when (kind) {
                    "w" -> MushafLine.Words(
                        number,
                        parts.drop(2).map { item ->
                            val w = item as? JsonArray ?: return null
                            val glyph = (w.getOrNull(3) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: return null
                            MushafWord(
                                surah = w.getOrNull(0).asInt() ?: return null,
                                ayah = w.getOrNull(1).asInt() ?: return null,
                                position = w.getOrNull(2).asInt() ?: return null,
                                glyph = glyph,
                                isAyahEnd = w.getOrNull(4).asInt() == 1,
                            )
                        }.also { if (it.isEmpty()) return null },
                    )
                    "h" -> MushafLine.SurahHeader(number, parts.getOrNull(2).asInt() ?: return null)
                    "b" -> MushafLine.Bismillah(number, parts.getOrNull(2).asInt() ?: return null)
                    else -> return null
                }
            }
            if (lines.none { it is MushafLine.Words }) return null
            MushafPage(page, lines)
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(name: String): Int? = this[name].asInt()

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
}
