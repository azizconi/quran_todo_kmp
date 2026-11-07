package tj.app.quran_todo.common.audio.timing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Reading and writing [WordTimings]: the answer of quran.com and the compact copy the app keeps.
 *
 * **The answer of quran.com** (`/recitations/{id}/by_chapter/{surah}?fields=segments&per_page=300`, checked on all 12 recitations
 * on 2026-10-30): `audio_files[]`, one per ayah, each with a `verse_key` ("2:255") and `segments`: arrays of four numbers
 * `[first word, last word position, start ms, end ms]`. The first word counts from 0 and the last one from 1, so an ordinary segment of
 * the word number n is `[n, n + 1, start, end]` and a segment `[1, 4, ...]` says that words 1, 2 and 3 are said together (the
 * timing is not that fine for that stretch). What the data really looks like, and how it is dealt with:
 *  - some recitations (As-Sudais) print every number as a string ("0"), so numbers are read from strings too;
 *  - merged segments are cut into equal pieces, one per word;
 *  - a word may be missing (no segment for it): it just has no span, and an ayah whose timing is too patchy is not used at all
 *    ([usableSpans]);
 *  - an end before its start (a few ayahs of several recitations) is lifted to the start, and starts that go back are lifted to the
 *    start before them, so the spans of an ayah are always in order;
 *  - a word that comes twice or out of order keeps its first span.
 *
 * **The copy of the app** is a small JSON file: `{"v":1,"r":7,"s":112,"a":{"1":[0,500,1200,1,1210,1900]}}`, per ayah the triples
 * `wordIndex, startMs, endMs` flattened. [decode] gives null for anything that is not that (a damaged file is then fetched again).
 */
object WordTimingsFormat {
    const val VERSION = 1

    /** A segment that claims more words than this is damaged data, not a merged stretch. */
    private const val MAX_MERGED_WORDS = 64

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads the answer of quran.com for [surah] and [recitationId]. Null when it is not that answer (no `audio_files`) or has no
     * ayah with usable segments.
     */
    fun parseQuranCom(text: String, recitationId: Int, surah: Int): WordTimings? {
        val files = try {
            json.parseToJsonElement(text).jsonObject["audio_files"]?.jsonArray
        } catch (_: Exception) {
            null
        } ?: return null
        val byAyah = LinkedHashMap<Int, List<WordSpan>>()
        for (file in files) {
            val obj = file as? JsonObject ?: continue
            val key = (obj["verse_key"] as? JsonPrimitive)?.contentOrNull() ?: continue
            val parts = key.split(':')
            if (parts.size != 2 || parts[0].toIntOrNull() != surah) continue
            val ayah = parts[1].toIntOrNull() ?: continue
            val segments = (obj["segments"] as? JsonArray)?.mapNotNull(::segmentOf).orEmpty()
            val spans = spansFromSegments(segments)
            if (spans.isNotEmpty()) byAyah[ayah] = spans
        }
        return if (byAyah.isEmpty()) null else WordTimings(recitationId, surah, byAyah)
    }

    /**
     * The spans of one ayah from its segments (each `[first word, last word position, start, end]`), the rules of the class comment.
     * Segments that do not have four numbers are skipped.
     */
    fun spansFromSegments(segments: List<IntArray>): List<WordSpan> {
        val spans = ArrayList<WordSpan>()
        var lastStart = 0
        var nextFree = 0
        for (segment in segments) {
            if (segment.size < 4) continue
            val first = segment[0]
            if (first < 0) continue
            val limit = maxOf(segment[1], first + 1)
            if (limit - first > MAX_MERGED_WORDS) continue
            val start = maxOf(segment[2], lastStart)
            val end = maxOf(segment[3], start)
            val count = limit - first
            var added = false
            for (offset in 0 until count) {
                val word = first + offset
                if (word < nextFree) continue
                spans += WordSpan(
                    wordIndex = word,
                    startMs = start + ((end - start).toLong() * offset / count).toInt(),
                    endMs = start + ((end - start).toLong() * (offset + 1) / count).toInt(),
                )
                nextFree = word + 1
                added = true
            }
            // A segment that only repeated a word says nothing about where the next one starts.
            if (added) lastStart = start
        }
        return spans
    }

    /** The compact copy of the app, see the class comment. */
    fun encode(timings: WordTimings): String = buildJsonObject {
        put("v", VERSION)
        put("r", timings.recitationId)
        put("s", timings.surah)
        put(
            "a",
            buildJsonObject {
                for ((ayah, spans) in timings.ayahs) {
                    put(
                        ayah.toString(),
                        buildJsonArray {
                            for (span in spans) {
                                add(JsonPrimitive(span.wordIndex))
                                add(JsonPrimitive(span.startMs))
                                add(JsonPrimitive(span.endMs))
                            }
                        },
                    )
                }
            },
        )
    }.toString()

    /** Reads the compact copy; null when it is damaged or of another version. */
    fun decode(text: String): WordTimings? {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            if (root["v"]?.jsonPrimitive?.int != VERSION) return null
            val byAyah = LinkedHashMap<Int, List<WordSpan>>()
            for ((key, value) in root.getValue("a").jsonObject) {
                val numbers = value.jsonArray.map { it.jsonPrimitive.int }
                if (numbers.size % 3 != 0) return null
                byAyah[key.toInt()] = numbers.chunked(3).map { (word, start, end) -> WordSpan(word, start, end) }
            }
            WordTimings(root.getValue("r").jsonPrimitive.int, root.getValue("s").jsonPrimitive.int, byAyah)
        } catch (_: Exception) {
            null
        }
    }

    /** One segment: its numbers (also when printed as strings), or null when it is not an array of numbers. */
    private fun segmentOf(element: JsonElement): IntArray? {
        val array = element as? JsonArray ?: return null
        val numbers = IntArray(array.size)
        for ((index, item) in array.withIndex()) {
            val primitive = item as? JsonPrimitive ?: return null
            numbers[index] = primitive.intOrNull ?: primitive.content.toIntOrNull() ?: return null
        }
        return numbers
    }

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null
}
