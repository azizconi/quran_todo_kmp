package tj.app.quran_todo.common.tajweed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import tj.app.quran_todo.data.bundled.QuranFacts

class TajweedFormatTest {
    private fun json(
        surahs: Int = QuranFacts.SURAH_COUNT,
        ayahsOf: (Int) -> Int = QuranFacts::ayahCount,
        text: (Int, Int) -> String = { surah, ayah -> "[h[ٱ]ل$surah-$ayah" },
    ): String = buildString {
        append("""{"code":200,"status":"OK","data":{"surahs":[""")
        for (surah in 1..surahs) {
            if (surah > 1) append(',')
            append("""{"number":$surah,"name":"x","englishName":"x","ayahs":[""")
            for (ayah in 1..ayahsOf(surah)) {
                if (ayah > 1) append(',')
                append("""{"number":$ayah,"text":"${text(surah, ayah).replace("\"", "\\\"")}","numberInSurah":$ayah,"juz":1,"sajda":false}""")
            }
            append("]}")
        }
        append("""],"edition":{"identifier":"quran-tajweed"}}}""")
    }

    @Test
    fun aCompleteAnswerIsSplitIntoSurahsAndAyahs() {
        val surahs = assertNotNull(TajweedFormat.splitQuran(json()))
        assertEquals(QuranFacts.SURAH_COUNT, surahs.size)
        assertEquals(QuranFacts.AYAH_COUNT, surahs.sumOf { it.size })
        assertEquals(286, surahs[1].size)
        assertEquals("[h[ٱ]ل2-255", surahs[1][254])
    }

    @Test
    fun anAnswerThatIsNotCompleteIsNeverKept() {
        assertNull(TajweedFormat.splitQuran(json(surahs = 113)))
        assertNull(TajweedFormat.splitQuran(json(ayahsOf = { if (it == 2) 285 else QuranFacts.ayahCount(it) })))
        assertNull(TajweedFormat.splitQuran(json(text = { surah, ayah -> if (surah == 114 && ayah == 6) "" else "x" })))
        assertNull(TajweedFormat.splitQuran("{}"))
        assertNull(TajweedFormat.splitQuran("not json"))
        assertNull(TajweedFormat.splitQuran(json().dropLast(40)))
    }

    @Test
    fun aLineBreakInsideAnAyahIsTurnedIntoASpace() {
        val surahs = assertNotNull(TajweedFormat.splitQuran(json(text = { _, _ -> "a\\nb" })))
        assertEquals("a b", surahs[0][0])
    }

    @Test
    fun aSurahFileHoldsOneAyahPerLine() {
        val markups = listOf("[h[ٱ]لْحَمْدُ", "[n[ـٰ]", "وَ")
        val text = TajweedFormat.encodeSurah(markups)
        assertEquals(markups, TajweedFormat.decodeSurah(text, 3))
        assertNull(TajweedFormat.decodeSurah(text, 4))
        assertNull(TajweedFormat.decodeSurah("", 1))
        assertNull(TajweedFormat.decodeSurah("a\n\nb", 3))
    }
}
