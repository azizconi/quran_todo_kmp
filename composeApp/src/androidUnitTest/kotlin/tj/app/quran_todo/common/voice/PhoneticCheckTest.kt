package tj.app.quran_todo.common.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [PhoneticCheck] against the cases made by `scripts/voice_phonetic_ref.py` (`voice/phonetic_cases.json`): every case must match exactly. */
class PhoneticCheckTest {
    private val cases: JsonArray by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("voice/phonetic_cases.json")!!.use { it.readBytes().decodeToString() }
        Json.parseToJsonElement(text).jsonArray
    }

    private fun verdict(name: String) = when (name) {
        "ok" -> WordVerdict.Correct
        "missed" -> WordVerdict.Missed
        "replaced" -> WordVerdict.Replaced
        else -> error(name)
    }

    private fun JsonObject.words() = getValue("words").jsonArray.map { it.jsonPrimitive.content }
    private fun JsonObject.heard() = getValue("heard").jsonPrimitive.content
    private fun JsonObject.verdicts() = getValue("verdicts").jsonArray.map { verdict(it.jsonPrimitive.content) }

    @Test
    fun judgeMatchesTheReference() {
        var count = 0
        for (element in cases) {
            val case = element.jsonObject
            if (case.getValue("kind").jsonPrimitive.content != "judge") continue
            val id = case.getValue("id").jsonPrimitive.content
            val result = PhoneticCheck.judge(case.words(), case.heard())
            assertEquals(case.verdicts(), result.verdicts, "verdicts of $id")
            val extras = case.getValue("extras").jsonArray.map { it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.int }
            assertEquals(extras, result.extras.map { it.gap to it.signs }, "extras of $id")
            count++
        }
        println("PhoneticCheck judge cases passed: $count")
        assertTrue(count > 0)
    }

    @Test
    fun followMatchesTheReference() {
        var count = 0
        for (element in cases) {
            val case = element.jsonObject
            if (case.getValue("kind").jsonPrimitive.content != "follow") continue
            val id = case.getValue("id").jsonPrimitive.content
            val result = PhoneticCheck.follow(case.words(), case.heard())
            assertEquals(case.getValue("shown").jsonPrimitive.int, result.shown, "shown of $id")
            assertEquals(case.verdicts(), result.verdicts, "verdicts of $id")
            count++
        }
        println("PhoneticCheck follow cases passed: $count")
        assertTrue(count > 0)
    }

    @Test
    fun allCasesAreRun() {
        assertEquals(126, cases.size)
    }

    @Test
    fun collapseJoinsRuns() {
        assertEquals("abca", PhoneticCheck.collapse("aabbbcaa"))
        assertEquals("", PhoneticCheck.collapse(""))
    }
}
