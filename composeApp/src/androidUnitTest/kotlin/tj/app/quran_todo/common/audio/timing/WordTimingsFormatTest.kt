package tj.app.quran_todo.common.audio.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordTimingsFormatTest {
    private fun parse(text: String, recitationId: Int, surah: Int): WordTimings =
        assertNotNull(WordTimingsFormat.parseQuranCom(text, recitationId, surah), "the answer must be readable")

    @Test
    fun alFatihaIsReadAyahByAyah() {
        val timings = parse(WordTimingsFixtures.AlafasyFatiha, recitationId = 7, surah = 1)
        assertEquals(7, timings.recitationId)
        assertEquals(1, timings.surah)
        assertEquals((1..7).toList(), timings.ayahs.keys.toList())
        assertEquals(
            listOf(WordSpan(0, 60, 610), WordSpan(1, 620, 1310), WordSpan(2, 1320, 2450), WordSpan(3, 2460, 5970)),
            timings.spans(1),
        )
        assertEquals(listOf(4, 4, 2, 3, 4, 3, 9), (1..7).map { timings.spans(it).size })
        assertEquals(WordSpan(8, 6640, 12320), timings.spans(7).last())
        assertEquals(emptyList(), timings.spans(8))
    }

    @Test
    fun ayatAlKursiHasFiftyWordsInOrder() {
        val spans = parse(WordTimingsFixtures.AlafasyAyatAlKursi, 7, 2).spans(255)
        assertEquals((0 until 50).toList(), spans.map { it.wordIndex })
        assertEquals(40, spans.first().startMs)
        assertEquals(51590, spans.last().endMs)
        assertTrue(spans.zipWithNext().all { (a, b) -> b.startMs >= a.startMs && a.endMs >= a.startMs })
    }

    @Test
    fun numbersThatAreStringsAreReadLikeNumbers() {
        // As-Sudais prints every number of a segment as a string.
        val sudais = parse(WordTimingsFixtures.SudaisFatiha, recitationId = 3, surah = 1)
        assertEquals(listOf(0, 1, 2, 3), sudais.spans(1).map { it.wordIndex })
        assertEquals(WordSpan(0, 380, 730), sudais.spans(1).first())
        assertEquals(WordSpan(0, 420, 1000), sudais.spans(2).first())
    }

    @Test
    fun aMergedSegmentIsCutIntoEqualPiecesOneForEachWord() {
        // [1, 4, 740, 3082]: the words 1, 2 and 3 are said together; 2342 ms make three pieces.
        val spans = parse(WordTimingsFixtures.SudaisFatiha, 3, 1).spans(1)
        assertEquals(listOf(WordSpan(1, 740, 1520), WordSpan(2, 1520, 2301), WordSpan(3, 2301, 3082)), spans.drop(1))
        // The last word of 1:7 (words 8 and 9 together, 6660..11140) is cut in two.
        val last = parse(WordTimingsFixtures.SudaisFatiha, 3, 1).spans(7)
        assertEquals(listOf(7, 8), last.takeLast(2).map { it.wordIndex })
        assertEquals(6660, last[last.size - 2].startMs)
        assertEquals(11140, last.last().endMs)
        assertEquals(last[last.size - 2].endMs, last.last().startMs)
    }

    @Test
    fun anEndBeforeItsStartIsLiftedToTheStart() {
        // 2:240 of Ash-Shuraim has [22, 23, 16440, 16430].
        val spans = parse(WordTimingsFixtures.ShuraymGlitch, 10, 2).spans(240)
        assertTrue(spans.all { it.endMs >= it.startMs })
        val word = spans.first { it.wordIndex == 22 }
        assertEquals(16440, word.startMs)
        assertEquals(16440, word.endMs)
    }

    @Test
    fun startsThatGoBackAreLiftedToTheStartBeforeThem() {
        // 2:182 of Al-Minshawi: [2, 3, 3150, 3080] followed by [3, 4, 3090, 5440].
        val spans = parse(WordTimingsFixtures.MinshawiGlitch, 9, 2).spans(182)
        assertEquals(3150, spans.first { it.wordIndex == 2 }.startMs)
        assertEquals(3150, spans.first { it.wordIndex == 3 }.startMs)
        assertEquals(5440, spans.first { it.wordIndex == 3 }.endMs)
        assertTrue(spans.zipWithNext().all { (a, b) -> b.startMs >= a.startMs }, "starts never go back")
    }

    @Test
    fun aWordWithoutASegmentHasNoSpan() {
        // 2:23 of Abdul Basit has segments for the words 9 and 11 but none for 10.
        val spans = parse(WordTimingsFixtures.TablawiMissingWord, 2, 2).spans(23)
        assertTrue(9 in spans.map { it.wordIndex })
        assertFalse(10 in spans.map { it.wordIndex })
        assertTrue(11 in spans.map { it.wordIndex })
        assertEquals(19, spans.size)
    }

    @Test
    fun aWordMayStartLateWhenTheRecitationSkipsTheBeginning() {
        // Al-Minshawi (mujawwad) 2:2 has segments for the words 2.. only.
        val spans = parse(WordTimingsFixtures.AlMinshawiMujawwadSecond, 8, 2).spans(2)
        assertEquals(listOf(2, 3, 4, 5, 6), spans.map { it.wordIndex })
    }

    @Test
    fun anAnswerForAnotherSurahOrWithoutAudioFilesIsNotTimings() {
        assertNull(WordTimingsFormat.parseQuranCom(WordTimingsFixtures.AlafasyFatiha, 7, surah = 2))
        assertNull(WordTimingsFormat.parseQuranCom("""{"audio_files":[]}""", 7, 1))
        assertNull(WordTimingsFormat.parseQuranCom("""{"error":"Not found"}""", 7, 1))
        assertNull(WordTimingsFormat.parseQuranCom("<html>blocked</html>", 7, 1))
        assertNull(WordTimingsFormat.parseQuranCom("", 7, 1))
    }

    @Test
    fun anAyahWithoutSegmentsIsLeftOut() {
        val text = """{"audio_files":[{"verse_key":"112:1","segments":[]},{"verse_key":"112:2","segments":[[0,1,10,500],[1,2,510,900]]}]}"""
        val timings = parse(text, 7, 112)
        assertEquals(listOf(2), timings.ayahs.keys.toList())
    }

    @Test
    fun brokenSegmentsAreSkippedAndAWordKeepsItsFirstSpan() {
        val spans = WordTimingsFormat.spansFromSegments(
            listOf(
                intArrayOf(0, 1, 100, 400),
                intArrayOf(5, 6), // too short
                intArrayOf(-1, 0, 1, 2), // before the first word
                intArrayOf(0, 1, 500, 900), // the same word again
                intArrayOf(1, 2, 410, 800), // starts before the end of the previous one: kept
                intArrayOf(2, 400, 900, 1000), // 398 words at once: damaged data
            ),
        )
        assertEquals(listOf(WordSpan(0, 100, 400), WordSpan(1, 410, 800)), spans)
    }

    @Test
    fun theCompactCopyKeepsEverything() {
        val original = parse(WordTimingsFixtures.AlafasyAlifLamMim, 7, 2)
        val copy = assertNotNull(WordTimingsFormat.decode(WordTimingsFormat.encode(original)))
        assertEquals(original, copy)
        assertEquals(listOf(WordSpan(0, 30, 7080)), copy.spans(1))
        assertEquals(7, copy.spans(2).size)
    }

    @Test
    fun aDamagedOrForeignCopyIsNotTimings() {
        assertNull(WordTimingsFormat.decode(""))
        assertNull(WordTimingsFormat.decode("not json"))
        assertNull(WordTimingsFormat.decode("""{"v":2,"r":7,"s":1,"a":{}}"""))
        assertNull(WordTimingsFormat.decode("""{"r":7,"s":1,"a":{}}"""))
        assertNull(WordTimingsFormat.decode("""{"v":1,"r":7,"s":1,"a":{"1":[0,10]}}"""))
        assertNull(WordTimingsFormat.decode("""{"v":1,"r":7,"s":1,"a":{"x":[0,10,20]}}"""))
        // A cut-off file.
        val whole = WordTimingsFormat.encode(parse(WordTimingsFixtures.AlafasyFatiha, 7, 1))
        assertNull(WordTimingsFormat.decode(whole.substring(0, whole.length / 2)))
    }
}
