package tj.app.quran_todo.ui.reminder

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonItem
import tj.app.quran_todo.hifz.schedule.LessonNew
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.LessonTomorrow
import tj.app.quran_todo.hifz.schedule.TextMetrics
import tj.app.quran_todo.ui.i18n.reminderCopy
import tj.app.quran_todo.ui.state.LastReadPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The text of the lesson reminder and the rule "do not send it when the lesson is done": for every kind of notification in all five
 * languages, with the plural forms and the units of the lesson card.
 */
class ReminderNoticesTest {
    private val today = 20_000

    // ---- Lessons made by hand, with the numbers the texts are checked against ----

    private fun item(id: Long, done: Boolean, surah: Int = 114, start: Int = 1, end: Int = 3) = LessonItem(
        passageId = id, surah = surah, startAyah = start, endAyah = end, stage = PassageStage.R3, hardAyahs = emptySet(),
        done = done, grade = if (done) ReviewGrade.GOOD else null, estimateSeconds = 120, overdueDays = 0,
    )

    private fun newBlock(state: LessonNewState = LessonNewState.PENDING) = LessonNew(
        surah = 78, startAyah = 1, endAyah = 5, state = state, grade = null, passageId = null, estimateSeconds = 300,
    )

    private fun lesson(
        new: LessonNew? = null,
        recent: List<LessonItem> = emptyList(),
        old: List<LessonItem> = emptyList(),
        remainingMinutes: Int,
        isComplete: Boolean = false,
    ) = LessonPlan(
        today = today, hasPlan = true, planFinished = false, newBlock = new, recent = recent, old = old,
        totalMinutes = remainingMinutes, remainingMinutes = remainingMinutes, catchingUp = false, deferredCount = 0,
        isComplete = isComplete, isEmpty = new == null && recent.isEmpty() && old.isEmpty(),
        tomorrow = LessonTomorrow(newPassage = AyahRange(78, 6, 10), dueCount = 0),
    )

    private fun names(language: AppLanguage): (Int) -> String = { surah ->
        when (surah) {
            78 -> if (language == AppLanguage.RU) "Ан-Наба" else "An-Naba"
            18 -> if (language == AppLanguage.RU) "Аль-Кахф" else "Al-Kahf"
            else -> "S$surah"
        }
    }

    private fun build(language: AppLanguage, lesson: LessonPlan, lastRead: LastReadPosition? = null) =
        ReminderNotices.build(language, lesson, lastRead, names(language))

    // ---- The five kinds of text ----

    private val newAndThree = lesson(new = newBlock(), recent = List(3) { item(it + 1L, done = false) }, remainingMinutes = 12)
    private val onlyThree = lesson(recent = List(3) { item(it + 1L, done = false) }, remainingMinutes = 6)
    private val onlyNew = lesson(new = newBlock(), remainingMinutes = 5)
    private val twoLeft = lesson(
        recent = listOf(item(1, done = true), item(2, done = false)),
        old = listOf(item(3, done = false)),
        remainingMinutes = 4,
    )

    @Test
    fun newPassageAndRepetitionsInRussianReadLikeTheBrief() {
        val notice = build(AppLanguage.RU, newAndThree)!!
        assertEquals(ReminderKind.LESSON, notice.kind)
        assertEquals("Урок дня", notice.title)
        assertEquals("Сегодня: Ан-Наба 1–5 и 3 повторения · ~12 мин", notice.body)
        assertEquals(ReminderTarget.Lesson, notice.target)
    }

    @Test
    fun onlyRepetitionsInRussianReadLikeTheBrief() {
        assertEquals("Сегодня 3 повторения · ~6 мин", build(AppLanguage.RU, onlyThree)!!.body)
    }

    @Test
    fun onlyTheNewPassageInRussianReadsLikeTheBrief() {
        assertEquals("Сегодня: Ан-Наба 1–5 · ~5 мин", build(AppLanguage.RU, onlyNew)!!.body)
    }

    @Test
    fun aLessonPartlyDoneSaysWhatIsLeft() {
        val notice = build(AppLanguage.RU, twoLeft)!!
        assertEquals("Осталось: 2 повторения · ~4 мин", notice.body)
        assertEquals(ReminderKind.LESSON, notice.kind)
    }

    @Test
    fun everyKindInEveryLanguage() {
        val expected = mapOf(
            AppLanguage.RU to listOf(
                "Сегодня: Ан-Наба 1–5 и 3 повторения · ~12 мин",
                "Сегодня 3 повторения · ~6 мин",
                "Сегодня: Ан-Наба 1–5 · ~5 мин",
                "Осталось: 2 повторения · ~4 мин",
                "Продолжить чтение: Аль-Кахф, аят 12",
            ),
            AppLanguage.EN to listOf(
                "Today: An-Naba 1–5 and 3 repetitions · ~12 min",
                "Today: 3 repetitions · ~6 min",
                "Today: An-Naba 1–5 · ~5 min",
                "Left: 2 repetitions · ~4 min",
                "Continue reading: Al-Kahf, ayah 12",
            ),
            AppLanguage.TG to listOf(
                "Имрӯз: An-Naba 1–5 ва 3 такрор · ~12 дақ.",
                "Имрӯз: 3 такрор · ~6 дақ.",
                "Имрӯз: An-Naba 1–5 · ~5 дақ.",
                "Боқӣ мондааст: 2 такрор · ~4 дақ.",
                "Хонишро идома диҳед: Al-Kahf, оят 12",
            ),
            AppLanguage.UZ to listOf(
                "Bugun: An-Naba 1–5 va 3 ta takror · ~12 daq",
                "Bugun: 3 ta takror · ~6 daq",
                "Bugun: An-Naba 1–5 · ~5 daq",
                "Qoldi: 2 ta takror · ~4 daq",
                "O'qishni davom ettiring: Al-Kahf, 12-oyat",
            ),
            AppLanguage.TR to listOf(
                "Bugün: An-Naba 1–5 ve 3 tekrar · ~12 dk",
                "Bugün: 3 tekrar · ~6 dk",
                "Bugün: An-Naba 1–5 · ~5 dk",
                "Kalan: 2 tekrar · ~4 dk",
                "Okumaya devam et: Al-Kahf, ayet 12",
            ),
        )
        val lessons = listOf(newAndThree, onlyThree, onlyNew, twoLeft, lesson(remainingMinutes = 0))
        AppLanguage.entries.forEach { language ->
            val texts = lessons.map { build(language, it, LastReadPosition(18, 12, null))!!.body }
            assertEquals(expected.getValue(language), texts, "texts/$language")
        }
    }

    @Test
    fun theReadingNoticeNamesTheAppAndOpensTheReaderAtTheAyah() {
        val notice = build(AppLanguage.EN, lesson(remainingMinutes = 0), LastReadPosition(18, 12, null))!!
        assertEquals(ReminderKind.READING, notice.kind)
        assertEquals("Quran Hifz", notice.title)
        assertEquals(ReminderTarget.Reader(surah = 18, ayah = 12), notice.target)
    }

    @Test
    fun theTitleOfALessonReminderIsTheTitleOfTheLessonInEveryLanguage() {
        val titles = AppLanguage.entries.associateWith { build(it, onlyThree)!!.title }
        assertEquals("Урок дня", titles[AppLanguage.RU])
        assertEquals("Today’s lesson", titles[AppLanguage.EN])
        assertEquals("Дарси рӯз", titles[AppLanguage.TG])
        assertEquals("Kunlik dars", titles[AppLanguage.UZ])
        assertEquals("Günün dersi", titles[AppLanguage.TR])
    }

    // ---- Russian plural forms ----

    @Test
    fun russianRepetitionsTakeTheRightForm() {
        val forms = mapOf(
            1 to "1 повторение", 2 to "2 повторения", 4 to "4 повторения", 5 to "5 повторений", 11 to "11 повторений",
            12 to "12 повторений", 14 to "14 повторений", 21 to "21 повторение", 22 to "22 повторения", 25 to "25 повторений",
            101 to "101 повторение", 111 to "111 повторений",
        )
        forms.forEach { (count, text) ->
            val body = reminderCopy(AppLanguage.RU).lessonBody(newRange = null, repetitions = count, minutes = 9, partlyDone = false)
            assertEquals("Сегодня $text · ~9 мин", body, "count $count")
        }
    }

    @Test
    fun englishAndTheOtherLanguagesDoNotBreakOnOne() {
        assertEquals(
            "Today: 1 repetition · ~2 min",
            reminderCopy(AppLanguage.EN).lessonBody(null, 1, 2, partlyDone = false),
        )
        assertEquals(
            "Bugun: 1 ta takror · ~2 daq",
            reminderCopy(AppLanguage.UZ).lessonBody(null, 1, 2, partlyDone = false),
        )
    }

    @Test
    fun minutesAreNeverLessThanOne() {
        val body = reminderCopy(AppLanguage.EN).lessonBody("An-Naba 1–5", 0, 0, partlyDone = false)
        assertEquals("Today: An-Naba 1–5 · ~1 min", body)
    }

    // ---- When nothing is sent ----

    @Test
    fun aLessonThatIsDoneSendsNothingEvenWhenThereIsAReadingPosition() {
        val done = lesson(
            new = newBlock(LessonNewState.DONE),
            recent = listOf(item(1, done = true)),
            remainingMinutes = 0,
            isComplete = true,
        )
        AppLanguage.entries.forEach { language ->
            assertNull(build(language, done), "done/$language")
            assertNull(build(language, done, LastReadPosition(18, 12, null)), "done+position/$language")
        }
    }

    @Test
    fun noLessonAndNoReadingPositionSendsNothing() {
        assertNull(build(AppLanguage.RU, lesson(remainingMinutes = 0)))
    }

    @Test
    fun aNewPassageThatWasDeferredIsNotInTodaysText() {
        val deferredOnly = lesson(
            new = newBlock(LessonNewState.DEFERRED),
            recent = listOf(item(1, done = false)),
            remainingMinutes = 3,
        )
        assertEquals("Today: 1 repetition · ~3 min", build(AppLanguage.EN, deferredOnly)!!.body)
    }

    @Test
    fun theNewPassageLearnedTodayCountsAsPartOfTheLessonThatIsDone() {
        val learnedAndOneLeft = lesson(
            new = newBlock(LessonNewState.DONE),
            recent = listOf(item(1, done = false)),
            remainingMinutes = 2,
        )
        assertEquals("Left: 1 repetition · ~2 min", build(AppLanguage.EN, learnedAndOneLeft)!!.body)
    }

    // ---- With the lessons the real scheduler makes ----

    private fun metrics() = TextMetrics(
        listOf(112 to 4, 113 to 5, 114 to 6).flatMap { (surah, count) ->
            (1..count).map { AyahMetric(surah, it, page = 1, chars = 100) }
        },
    )

    private fun plan() = HifzPlan(HifzScope.CUSTOM, listOf(114), 3, targetEpochDay = null, startedEpochDay = today - 10, manzilDays = 7)

    private fun built(plan: HifzPlan? = plan(), passages: List<Passage> = emptyList(), log: List<ReviewLogEntry> = emptyList()) =
        LessonBuilder.build(LessonInput(today, plan, passages, log, metrics()))

    private fun passage(id: Long, surah: Int, start: Int, end: Int) = Passage(
        id = id, surah = surah, startAyah = start, endAyah = end, learnedEpochDay = today - 5, stage = PassageStage.R1,
        nextDueEpochDay = today, lastReviewedEpochDay = today - 1, lapses = 0,
    )

    @Test
    fun aFreshLessonFromTheSchedulerIsAReminderForTheLesson() {
        val lesson = built(passages = listOf(passage(1, 113, 1, 3)))
        val notice = ReminderNotices.build(AppLanguage.EN, lesson, null) { "S$it" }!!
        assertEquals(ReminderKind.LESSON, notice.kind)
        assertTrue(notice.body.startsWith("Today: S114 1–3 and 1 repetition"), notice.body)
    }

    @Test
    fun theLessonDoneAccordingToTheLogSendsNothing() {
        // 114:1-3 was learned today (a NEW record, and its passage is due tomorrow); nothing else is due.
        val learned = Passage(
            id = 2, surah = 114, startAyah = 1, endAyah = 3, learnedEpochDay = today, stage = PassageStage.R1,
            nextDueEpochDay = today + 1, lastReviewedEpochDay = today, lapses = 0,
        )
        val log = ReviewLogEntry(1, 2, 114, 1, 3, ReviewKind.NEW, ReviewGrade.GOOD, today, timestamp = 1)
        val lesson = built(passages = listOf(learned), log = listOf(log))
        assertTrue(lesson.isComplete, "the scheduler calls this lesson complete")
        assertNull(ReminderNotices.build(AppLanguage.EN, lesson, LastReadPosition(18, 12, null)) { "S$it" })
    }

    @Test
    fun withoutAPlanAndNothingToRepeatTheReminderContinuesReading() {
        val lesson = built(plan = null)
        assertTrue(lesson.isEmpty)
        val notice = ReminderNotices.build(AppLanguage.EN, lesson, LastReadPosition(18, 12, null)) { "S$it" }!!
        assertEquals(ReminderKind.READING, notice.kind)
        assertEquals("Continue reading: S18, ayah 12", notice.body)
        assertNull(ReminderNotices.build(AppLanguage.EN, lesson, null) { "S$it" })
    }

    // ---- The reading position ----

    @Test
    fun aPositionOfAnOldVersionHasAGlobalAyahNumberThatBecomesTheNumberInTheSurah() {
        val global = QuranFacts.firstAyahNumber(18) + 12 - 1
        val target = ReminderNotices.readingTarget(LastReadPosition(18, null, global))
        assertEquals(ReminderTarget.Reader(18, 12), target)
    }

    @Test
    fun aGlobalNumberOutsideTheSurahOpensTheSurahWithoutAnAyah() {
        assertEquals(ReminderTarget.Reader(18, null), ReminderNotices.readingTarget(LastReadPosition(18, null, 3)))
        assertEquals(ReminderTarget.Reader(18, null), ReminderNotices.readingTarget(LastReadPosition(18, null, null)))
        val body = reminderCopy(AppLanguage.EN).readingBody("Al-Kahf", null)
        assertEquals("Continue reading: Al-Kahf", body)
    }

    @Test
    fun aSurahThatDoesNotExistIsNotAReadingPosition() {
        assertNull(ReminderNotices.readingTarget(LastReadPosition(0, 3, null)))
        assertNull(ReminderNotices.readingTarget(LastReadPosition(115, 3, null)))
        assertNull(build(AppLanguage.EN, lesson(remainingMinutes = 0), LastReadPosition(115, 3, null)))
    }

    // ---- Every string exists in every language ----

    @Test
    fun everyLanguageHasEveryReminderString() {
        AppLanguage.entries.forEach { language ->
            val copy = reminderCopy(language)
            listOf(
                copy.sectionTitle, copy.toggleTitle, copy.footnote, copy.timeTitle, copy.timeDialogTitle, copy.done, copy.cancel,
                copy.blockedTitle, copy.blockedBody, copy.openSettings, copy.deniedToast, copy.channelName, copy.channelDescription,
                copy.snoozeAction, copy.lessonTitle, copy.readingTitle,
            ).forEach { assertTrue(it.isNotBlank(), "blank string in $language") }
            assertTrue("{what}" in copy.todayFmt && "{min}" in copy.todayFmt, "todayFmt/$language")
            assertTrue("{what}" in copy.todayRepetitionsOnlyFmt && "{min}" in copy.todayRepetitionsOnlyFmt, "todayRepsOnly/$language")
            assertTrue("{what}" in copy.leftFmt && "{min}" in copy.leftFmt, "leftFmt/$language")
            assertTrue("{name}" in copy.readingFmt && "{a}" in copy.readingFmt, "readingFmt/$language")
            assertTrue("{name}" in copy.readingNoAyahFmt, "readingNoAyahFmt/$language")
        }
    }

    @Test
    fun theReminderTextsAreTranslated() {
        val english = reminderCopy(AppLanguage.EN)
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = reminderCopy(language)
            assertNotEquals(english.toggleTitle, other.toggleTitle, "toggleTitle/$language")
            assertNotEquals(english.footnote, other.footnote, "footnote/$language")
            assertNotEquals(english.snoozeAction, other.snoozeAction, "snoozeAction/$language")
            assertNotEquals(english.todayFmt, other.todayFmt, "todayFmt/$language")
            assertNotEquals(english.leftFmt, other.leftFmt, "leftFmt/$language")
            assertNotEquals(english.readingFmt, other.readingFmt, "readingFmt/$language")
        }
    }

    @Test
    fun thePlaceholdersAreAllFilled() {
        AppLanguage.entries.forEach { language ->
            val copy = reminderCopy(language)
            listOf(
                copy.lessonBody("An-Naba 1–5", 3, 12, partlyDone = false),
                copy.lessonBody(null, 3, 12, partlyDone = false),
                copy.lessonBody("An-Naba 1–5", 3, 12, partlyDone = true),
                copy.readingBody("Al-Kahf", 12),
                copy.readingBody("Al-Kahf", null),
            ).forEach { text -> assertTrue('{' !in text && '}' !in text, "unfilled placeholder in $language: $text") }
        }
    }

    // ---- The time on the settings row ----

    @Test
    fun theTimeIsShownInTheClockOfThePhone() {
        assertEquals("20:00", formatReminderTime(20 * 60, is24Hour = true))
        assertEquals("08:05", formatReminderTime(8 * 60 + 5, is24Hour = true))
        assertEquals("00:00", formatReminderTime(0, is24Hour = true))
        assertEquals("8:00 PM", formatReminderTime(20 * 60, is24Hour = false))
        assertEquals("12:00 AM", formatReminderTime(0, is24Hour = false))
        assertEquals("12:30 PM", formatReminderTime(12 * 60 + 30, is24Hour = false))
        assertEquals("11:59 PM", formatReminderTime(23 * 60 + 59, is24Hour = false))
    }

    // ---- The link of the notification ----

    @Test
    fun theLinkOfALessonAndOfTheReaderIsParsedFromItsExtras() {
        assertEquals(ReminderTarget.Lesson, ReminderLink.parse("lesson", 0, 0))
        assertEquals(ReminderTarget.Reader(18, 12), ReminderLink.parse("reader", 18, 12))
        assertEquals(ReminderTarget.Reader(18, null), ReminderLink.parse("reader", 18, 0))
    }

    @Test
    fun anythingElseIsNotALink() {
        assertNull(ReminderLink.parse(null, 18, 12))
        assertNull(ReminderLink.parse("settings", 0, 0))
        assertNull(ReminderLink.parse("reader", 0, 3))
        assertNull(ReminderLink.parse("reader", 200, 3))
    }

    @Test
    fun theSameTapTwiceIsTwoRequestsButAHandledOneIsGone() {
        ReminderOpenRequests.post(ReminderTarget.Lesson)
        val first = ReminderOpenRequests.pending.value!!
        ReminderOpenRequests.post(ReminderTarget.Lesson)
        val second = ReminderOpenRequests.pending.value!!
        assertNotEquals(first, second)
        // The navigation finishing the first request does not clear the second.
        ReminderOpenRequests.consume(first)
        assertEquals(second, ReminderOpenRequests.pending.value)
        ReminderOpenRequests.consume(second)
        assertNull(ReminderOpenRequests.pending.value)
    }
}
