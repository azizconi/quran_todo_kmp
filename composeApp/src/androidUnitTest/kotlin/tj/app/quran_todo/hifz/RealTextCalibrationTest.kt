package tj.app.quran_todo.hifz

import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.PassageOps
import tj.app.quran_todo.hifz.schedule.TextMetrics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The constants of the scheduler were measured on the bundled text; these tests read the very same file (the app reads it
 * through Res) and check them. `chars` is the number of Unicode code points, what SQLite `LENGTH(text)` returns.
 */
class RealTextCalibrationTest {
    private val quran = QuranTsvParser.parse(BundledFiles.quranTsv())
    private val metrics = TextMetrics(
        quran.ayahs.map { AyahMetric(it.surahNumber, it.numberInSurah, it.page, it.text.codePointCount(0, it.text.length)) },
    )

    private fun pageWeights(): Map<Int, Int> =
        quran.ayahs.groupBy { it.page }.mapValues { (_, ayahs) -> ayahs.sumOf { it.text.codePointCount(0, it.text.length) } }

    @Test
    fun theTextHasAllAyahs() {
        assertEquals(QuranFacts.AYAH_COUNT, (1..QuranFacts.SURAH_COUNT).sumOf { metrics.ayahCount(it) })
        assertEquals(QuranFacts.AYAH_COUNT, quran.ayahs.size)
    }

    @Test
    fun alFatihaWithTheBasmalaTakesAboutFiftySecondsOfRecitation() {
        // 7 ayahs, the basmala is the first one.
        assertEquals(285, metrics.surahChars(1))
        val seconds = HifzConstants.recitationSeconds(metrics.surahChars(1))
        assertTrue(abs(seconds - 50.0) < 0.5, "Al-Fatiha takes $seconds s")
    }

    @Test
    fun aMushafPageIsAboutTwelveHundredCharactersAndMaxOldPassageCharsIsOnePage() {
        val pages = pageWeights()
        assertEquals(604, pages.size)
        val average = pages.values.average()
        assertTrue(average in 1_150.0..1_250.0, "an average page is $average characters")
        assertTrue(abs(HifzConstants.CHARS_PER_PAGE - average) < 1.0, "CHARS_PER_PAGE=${HifzConstants.CHARS_PER_PAGE} vs $average")
        assertTrue(abs(HifzConstants.MAX_OLD_PASSAGE_CHARS - average) / average < 0.05, "MAX_OLD_PASSAGE_CHARS=${HifzConstants.MAX_OLD_PASSAGE_CHARS} vs $average")
        assertTrue(pages.values.max() < 1_600, "the fullest page has ${pages.values.max()} characters")
    }

    @Test
    fun aTypicalDayOfJuzAmmaFitsComfortablyIntoTheBudget() {
        // Five new ayahs of An-Naba (78:1-5) and a repetition of a page of old: the estimate in minutes.
        val newSeconds = HifzConstants.newSeconds(metrics.chars(78, 1, 5))
        val pageReview = HifzConstants.reviewSeconds(1_184)
        assertTrue(newSeconds in 150..600, "5 new ayahs: $newSeconds s")
        assertTrue(pageReview in 300..600, "a page of repetition: $pageReview s")
        assertTrue(newSeconds + 2 * pageReview < HifzConstants.LESSON_BUDGET_SECONDS)
    }

    @Test
    fun markingAWholeJuzAmmaAsKnownCutsItIntoPagePassages() {
        var passages = emptyList<Passage>()
        for (surah in 114 downTo 78) passages = PassageOps.markKnown(passages, surah, 1, metrics.ayahCount(surah), 100, metrics)
        assertTrue(passages.all { it.stage == PassageStage.OLD && it.lastReviewedEpochDay == null })
        // Juz Amma has about 21 pages; every piece is at most one page.
        assertTrue(passages.size in 21..60, "${passages.size} passages")
        assertTrue(passages.all { metrics.weight(it) <= 1_600 })
        assertEquals((78..114).sumOf { metrics.ayahCount(it) }, passages.sumOf { it.ayahCount })
        // Al-Ikhlas is on one page and is one passage.
        assertEquals(1, passages.count { it.surah == 112 })
    }

    @Test
    fun theLessonOfAFreshJuzAmmaPlanOffersOneSmallPassageAtATime() {
        val plan = Fx.plan(HifzPresets.JUZ_30, daily = 5)
        val lesson = LessonBuilder.build(LessonInput(today = 100, plan = plan, passages = emptyList(), todayLog = emptyList(), metrics = metrics))
        val block = lesson.newBlock!!
        assertEquals(LessonNewState.PENDING, block.state)
        assertEquals(114, block.surah) // the shortest surah first
        assertEquals(1 to 6, block.startAyah to block.endAyah) // 6 <= 7.5: the whole surah
        assertTrue(lesson.totalMinutes in 1..HifzConstants.LESSON_BUDGET_MINUTES, "${lesson.totalMinutes} min")
    }
}
