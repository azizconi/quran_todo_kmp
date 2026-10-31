package tj.app.quran_todo.ui.mushaf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.mushaf.MushafFixtures
import tj.app.quran_todo.common.mushaf.MushafLayoutFormat
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.HifzPhase

class PageCheckTest {
    private fun page(number: Int): MushafPage = assertNotNull(MushafLayoutFormat.parseQuranCom(MushafFixtures.answer(number), number))

    private fun PageCheck.answerAll(vararg marks: AyahMark): PageCheck {
        var check = this
        marks.forEach { check = check.reveal().mark(it) }
        return check
    }

    @Test
    fun theCheckTakesTheAyahsOfThePageFromTheOnePickedToTheLastOne() {
        val whole = assertNotNull(PageCheck.of(page(604), start = null))
        assertEquals((1..4).map { AyahKey(112, it) } + (1..5).map { AyahKey(113, it) } + (1..6).map { AyahKey(114, it) }, whole.items.map { it.key })
        assertEquals(15, whole.size)

        val fromHere = assertNotNull(PageCheck.of(page(604), start = AyahKey(113, 3)))
        assertEquals(listOf(AyahKey(113, 3), AyahKey(113, 4), AyahKey(113, 5)) + (1..6).map { AyahKey(114, it) }, fromHere.items.map { it.key })

        // An ayah that is not on the page, or none, starts at the top.
        assertEquals(whole.items, assertNotNull(PageCheck.of(page(604), start = AyahKey(2, 1))).items)
    }

    @Test
    fun anAyahIsRevealedThenAnsweredAndTheNextOneWaitsHidden() {
        var check = assertNotNull(PageCheck.of(page(604), start = AyahKey(112, 1)))
        assertEquals(HifzPhase.Current, check.phaseOf(AyahKey(112, 1)))
        assertEquals(HifzPhase.Upcoming, check.phaseOf(AyahKey(112, 2)))
        assertEquals(HifzPhase.Outside, check.phaseOf(AyahKey(2, 255)))

        // The answers are on offer only after the reveal: a mark before it is ignored.
        assertEquals(check, check.mark(AyahMark.REMEMBERED))
        check = check.reveal()
        assertTrue(check.revealed)
        check = check.mark(AyahMark.HARD)
        assertEquals(1, check.index)
        assertEquals(false, check.revealed, "the next ayah is hidden again")
        assertEquals(HifzPhase.Done, check.phaseOf(AyahKey(112, 1)))
        assertEquals(AyahMark.HARD, check.markOf(AyahKey(112, 1)))
        assertEquals(HifzPhase.Current, check.phaseOf(AyahKey(112, 2)))
        assertNull(check.markOf(AyahKey(112, 2)))
    }

    @Test
    fun theCheckIsFinishedWhenEveryAyahIsAnswered() {
        var check = assertNotNull(PageCheck.of(page(604), start = AyahKey(114, 5)))
        assertEquals(2, check.size)
        assertEquals(false, check.finished)
        check = check.answerAll(AyahMark.REMEMBERED, AyahMark.REMEMBERED)
        assertTrue(check.finished)
        assertNull(check.current)
        assertEquals(2, check.markedCount)
        // Nothing more happens after the end.
        assertEquals(check, check.reveal())
        assertEquals(check, check.mark(AyahMark.HARD))
    }

    @Test
    fun theGradeFollowsTheHardAnswersByTheRuleOfTheTextReader() {
        val all = assertNotNull(PageCheck.of(page(604), start = null))
        val clean = all.answerAll(*Array(15) { AyahMark.REMEMBERED })
        assertEquals(ReviewGrade.GOOD, clean.suggestedGrade)
        val some = all.answerAll(*Array(15) { if (it == 3) AyahMark.HARD else AyahMark.REMEMBERED })
        assertEquals(ReviewGrade.HARD, some.suggestedGrade)
        assertEquals(setOf(AyahKey(112, 4)), some.hardAyahs)
        val half = all.answerAll(*Array(15) { if (it < 8) AyahMark.HARD else AyahMark.REMEMBERED })
        assertEquals(ReviewGrade.FORGOT, half.suggestedGrade)
    }

    @Test
    fun aPageOfThreeSurahsIsWrittenAsThreePartsEachWithItsOwnHardAyahs() {
        val marks = Array(15) { if (it == 1 || it == 6 || it == 14) AyahMark.HARD else AyahMark.REMEMBERED }
        val check = assertNotNull(PageCheck.of(page(604), start = null)).answerAll(*marks)
        assertEquals(
            listOf(
                PageCheckPart(112, 1, 4, hard = setOf(2)),
                PageCheckPart(113, 1, 5, hard = setOf(3)),
                PageCheckPart(114, 1, 6, hard = setOf(6)),
            ),
            check.parts(),
        )
    }

    @Test
    fun theGradeSheetCanChangeWhichAyahsAreHard() {
        val check = assertNotNull(PageCheck.of(page(604), start = null)).answerAll(*Array(15) { AyahMark.REMEMBERED })
        val parts = check.parts(hard = setOf(AyahKey(113, 1), AyahKey(114, 6)))
        assertEquals(emptySet(), parts[0].hard)
        assertEquals(setOf(1), parts[1].hard)
        assertEquals(setOf(6), parts[2].hard)
    }

    @Test
    fun aCheckFromTheMiddleOfASurahIsOnePartThatIsNotTheWholeSurah() {
        val check = assertNotNull(PageCheck.of(page(3), start = AyahKey(2, 10)))
        assertEquals(listOf(PageCheckPart(2, 10, 16, emptySet())), check.parts())
    }
}
