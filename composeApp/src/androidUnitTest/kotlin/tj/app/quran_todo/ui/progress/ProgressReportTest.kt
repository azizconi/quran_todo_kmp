package tj.app.quran_todo.ui.progress

import kotlinx.datetime.LocalDate
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.ui.i18n.reportCopy
import tj.app.quran_todo.ui.progress.report.ProgressReport
import tj.app.quran_todo.ui.progress.report.ReportBuilder
import tj.app.quran_todo.ui.progress.report.ReportComposer
import tj.app.quran_todo.ui.progress.report.ReportDoc
import tj.app.quran_todo.ui.progress.report.ReportHard
import tj.app.quran_todo.ui.progress.report.ReportPeriod
import tj.app.quran_todo.ui.progress.report.ReportRange
import tj.app.quran_todo.ui.progress.report.toPlainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The report for the teacher: the model built from the review log, the coverage and the plan (`ReportBuilder`), the words in
 * the five languages (`ReportComposer`) and the plain text that goes to the messenger (`toPlainText`).
 */
class ProgressReportTest {
    /** 30 October 2026: the week is 24-30 October. */
    private val today = LocalDate(2026, 10, 30).toEpochDays()

    /** Surahs 111-114 get their Russian names; the rest are "S<n>". */
    private val ruNames = List(114) { index ->
        when (index + 1) {
            110 -> "Ан-Наср"
            111 -> "Аль-Масад"
            112 -> "Аль-Ихлас"
            113 -> "Аль-Фаляк"
            114 -> "Ан-Нас"
            else -> "S${index + 1}"
        }
    }

    private fun entry(kind: ReviewKind, surah: Int, start: Int, end: Int, day: Int, grade: ReviewGrade = ReviewGrade.GOOD) =
        Fx.log(null, surah, start, end, kind, grade, day)

    /** A week of study: Al-Masad learned over two days, Al-Ikhlas today, some repetitions, one of them forgotten. */
    private val log: List<ReviewLogEntry> = listOf(
        entry(ReviewKind.NEW, 111, 1, 3, today - 2),
        entry(ReviewKind.NEW, 111, 4, 5, today - 1),
        entry(ReviewKind.NEW, 112, 1, 4, today),
        entry(ReviewKind.RECENT, 111, 1, 3, today - 1),
        entry(ReviewKind.RECENT, 113, 1, 5, today),
        entry(ReviewKind.CHECK, 114, 1, 6, today - 3, ReviewGrade.HARD),
        entry(ReviewKind.RECENT, 113, 1, 5, today - 4, ReviewGrade.FORGOT),
        // Ten days ago: only in the report of a month.
        entry(ReviewKind.NEW, 110, 1, 3, today - 10),
        entry(ReviewKind.OLD, 114, 1, 6, today - 12),
    )

    private val passages: List<Passage> = listOf(
        Fx.passage(1, 111, 1, 5, PassageStage.R1, due = today + 1, hard = mapOf(3 to 2, 5 to 2)),
        Fx.passage(2, 112, 1, 4, PassageStage.R1, due = today + 1),
        Fx.passage(3, 113, 1, 5),
        Fx.passage(4, 114, 1, 6),
    )

    private val plan: HifzPlan = Fx.plan(listOf(114, 113, 112, 111, 110), daily = 5, started = today - 20, scope = HifzScope.CUSTOM)

    private fun sources(
        passages: List<Passage> = this.passages,
        log: List<ReviewLogEntry> = this.log,
        plan: HifzPlan? = this.plan,
        streak: Int = 3,
        names: List<String> = ruNames,
        language: AppLanguage = AppLanguage.RU,
    ): ProgressSources {
        val metrics = Fx.flat
        val coverage = Coverage(passages, metrics)
        return ProgressSources(
            today = today,
            language = language,
            names = names,
            statuses = HifzStatuses.all(passages, plan, metrics),
            covered = (1..114).filter { coverage.coveredCount(it) > 0 }.associateWith { coverage.coveredAyahs(it) },
            hard = HifzStatuses.hardAyahs(passages),
            log = log,
            streak = streak,
            lesson = null,
            plan = plan,
            forecast = plan?.let { Forecast.compute(it, coverage, log.filter { e -> e.kind == ReviewKind.NEW }, today) },
        )
    }

    private fun report(period: ReportPeriod = ReportPeriod.Week, s: ProgressSources = sources()) = ReportBuilder.build(s, period)

    // -- The model ------------------------------------------------------------------------------------

    @Test
    fun theWeekIsTheSevenDaysEndingToday() {
        val week = report(ReportPeriod.Week)
        assertEquals(today - 6, week.fromDay)
        assertEquals(today, week.toDay)
        assertEquals(7, week.dayCount)
        val month = report(ReportPeriod.Month)
        assertEquals(today - 29, month.fromDay)
        assertEquals(30, month.dayCount)
    }

    @Test
    fun whatWasLearnedIsListedPerSurahWithNeighbouringRangesMerged() {
        val week = report()
        // Al-Masad over two days is one range 1-5; surahs in the order they were first learned.
        assertEquals(listOf(ReportRange(111, 1, 5), ReportRange(112, 1, 4)), week.newRanges)
        assertEquals(9, week.newAyahs)
    }

    @Test
    fun aMonthReachesFurtherBackAndKeepsTheOrderOfLearning() {
        val month = report(ReportPeriod.Month)
        assertEquals(
            listOf(ReportRange(110, 1, 3), ReportRange(111, 1, 5), ReportRange(112, 1, 4)),
            month.newRanges,
        )
        assertEquals(12, month.newAyahs)
        assertEquals(listOf(111, 113, 114), report(ReportPeriod.Week).reviewedSurahs)
        assertEquals(listOf(111, 113, 114), month.reviewedSurahs) // the OLD record of 114 is the same surah
        assertEquals(5, report().activeDays)
        assertEquals(7, month.activeDays)
    }

    @Test
    fun repetitionsAreCountedByRecordsAndAyahsOnce() {
        val week = report()
        assertEquals(4, week.repetitions)
        // 111:1-3, 113:1-5 (twice: once), 114:1-6.
        assertEquals(3 + 5 + 6, week.reviewedAyahs)
        assertEquals(75, week.retentionPercent)
        assertEquals(null, report(s = sources(log = emptyList())).retentionPercent)
    }

    @Test
    fun adjacentOverlappingAndSeparateRangesAreMergedCorrectly() {
        val records = listOf(
            entry(ReviewKind.NEW, 78, 1, 5, 10),
            entry(ReviewKind.NEW, 78, 6, 9, 11), // right next to it
            entry(ReviewKind.NEW, 78, 8, 12, 12), // overlaps
            entry(ReviewKind.NEW, 78, 20, 25, 13), // a gap
            entry(ReviewKind.NEW, 77, 1, 3, 14),
        )
        assertEquals(
            listOf(ReportRange(78, 1, 12), ReportRange(78, 20, 25), ReportRange(77, 1, 3)),
            ReportBuilder.mergedRanges(records),
        )
        assertTrue(ReportBuilder.mergedRanges(emptyList()).isEmpty())
    }

    @Test
    fun theDifficultAyahsStreakAndPlanComeFromTheSameSourcesAsTheScreen() {
        val week = report()
        assertEquals(listOf(ReportHard(111, listOf(3, 5))), week.hard)
        assertEquals(2, week.hardAyahs)
        assertEquals(3, week.streak)
        assertEquals(PlanStatus.Finish, week.plan.status)
        assertEquals(HifzScope.CUSTOM, week.plan.scope)
        assertEquals(20, week.hero.learnedAyahs)
        assertEquals(PlanLineUi(PlanStatus.NoPlan), report(s = sources(plan = null)).plan)
    }

    // -- The words ------------------------------------------------------------------------------------

    private fun doc(language: AppLanguage, names: List<String> = ruNames, r: ProgressReport = report()): ReportDoc =
        ReportComposer.compose(r, language, names)

    @Test
    fun theRussianTextIsReadyForTelegram() {
        val expected = """
            Отчёт по хифзу
            24 окт – 30 окт

            Выучено всего: 20 из 6236 аятов (<1%)
            Выучено новое: 9 аятов
            • Аль-Масад 1–5
            • Аль-Ихлас 1–4
            Повторено: 14 аятов · 4 повторения
            Суры: Аль-Масад, Аль-Фаляк, Ан-Нас
            Вспомнено без ошибок: 75%
            Трудные аяты: 2 аята
            • Аль-Масад 3, 5
            Дней с занятиями: 5/7
            Серия: 3 дня
            План: Свои суры
            Прогноз окончания: ~1 ноября
        """.trimIndent()
        assertEquals(expected, doc(AppLanguage.RU).toPlainText())
    }

    @Test
    fun theEnglishTextHasTheSameShape() {
        val text = doc(AppLanguage.EN, names = List(114) { "Surah ${it + 1}" }).toPlainText()
        val lines = text.lines()
        assertEquals(
            listOf("Hifz report", "Oct 24 – Oct 30", ""),
            lines.take(3),
        )
        assertEquals("Memorized in total: 20 of 6236 ayahs (<1%)", lines[3])
        assertEquals("Newly memorized: 9 ayahs", lines[4])
        assertEquals("• Surah 111 1–5", lines[5])
        assertEquals("• Surah 112 1–4", lines[6])
        assertEquals("Reviewed: 14 ayahs · 4 repetitions", lines[7])
        assertEquals("Surahs: Surah 111, Surah 113, Surah 114", lines[8])
        assertEquals("Recalled without mistakes: 75%", lines[9])
        assertEquals("Hard ayahs: 2 ayahs", lines[10])
        assertEquals("• Surah 111 3, 5", lines[11])
        assertEquals("Days with practice: 5/7", lines[12])
        assertEquals("Streak: 3 days", lines[13])
        assertEquals("Plan: My surahs", lines[14])
        assertEquals("Expected finish: ~November 1", lines[15])
        assertEquals(16, lines.size)
    }

    @Test
    fun everyLanguageFillsEveryPartAndLeavesNothingUnreplaced() {
        for (language in AppLanguage.entries) {
            for (period in ReportPeriod.entries) {
                val text = doc(language, r = report(period)).toPlainText()
                assertFalse(text.contains('{') || text.contains('}'), "${language.name}/$period: $text")
                assertTrue(text.lines().size >= 12, "${language.name}/$period")
                assertTrue("Аль-Масад" in text, "${language.name}/$period names the surahs: $text")
                assertTrue("1–5" in text, "${language.name}/$period gives the ranges: $text")
                assertTrue("5/7" in text || "7/30" in text || "/30" in text, "${language.name}/$period counts the days")
            }
        }
    }

    @Test
    fun theTextAndThePictureNeverUseStarsOrSparkles() {
        val forbidden = listOf(
            "★", "☆", "⭐", "✨", "✦", "✧", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "✰", "⁂", "✡", "☪", "✴", "✵", "✶", "✷", "✸", "✹",
            "*", "✱", "✲", "✳", "❂", "❇", "❈", "❉", "❊", "❋",
        )
        for (language in AppLanguage.entries) {
            val d = doc(language, r = report(ReportPeriod.Month))
            val everything = d.toPlainText() + d.title + d.period + d.caption + d.percentText +
                d.rows.joinToString { it.label + it.value + it.lines.joinToString() }
            forbidden.forEach { star -> assertFalse(star in everything, "${language.name} has $star") }
        }
    }

    @Test
    fun numbersTakeTheFormsOfEachLanguage() {
        fun newText(language: AppLanguage, ayahs: Int): String {
            val r = report(s = sources(log = listOf(entry(ReviewKind.NEW, 78, 1, ayahs, today))))
            return doc(language, r = r).rows.first().value
        }
        assertEquals("1 аят", newText(AppLanguage.RU, 1))
        assertEquals("2 аята", newText(AppLanguage.RU, 2))
        assertEquals("5 аятов", newText(AppLanguage.RU, 5))
        assertEquals("11 аятов", newText(AppLanguage.RU, 11))
        assertEquals("21 аят", newText(AppLanguage.RU, 21))
        assertEquals("1 ayah", newText(AppLanguage.EN, 1))
        assertEquals("2 ayahs", newText(AppLanguage.EN, 2))
        assertEquals("3 оят", newText(AppLanguage.TG, 3))
        assertEquals("3 oyat", newText(AppLanguage.UZ, 3))
        assertEquals("3 ayet", newText(AppLanguage.TR, 3))
    }

    @Test
    fun aSingleAyahIsNamedWithoutARange() {
        val r = report(s = sources(log = listOf(entry(ReviewKind.NEW, 111, 3, 3, today))))
        assertEquals(listOf("Аль-Масад 3"), doc(AppLanguage.RU, r = r).rows.first().lines)
    }

    @Test
    fun anEmptyWeekSaysNoneInsteadOfInventingNumbers() {
        val r = report(s = sources(log = emptyList(), streak = 0, plan = null))
        val rows = doc(AppLanguage.RU, r = r).rows.associateBy { it.label }
        assertEquals("нет", rows.getValue("Выучено новое").value)
        assertEquals("нет", rows.getValue("Повторено").value)
        assertTrue(rows.getValue("Выучено новое").lines.isEmpty())
        assertTrue(rows.getValue("Повторено").lines.isEmpty())
        assertEquals("0/7", rows.getValue("Дней с занятиями").value)
        assertFalse("Вспомнено без ошибок" in rows) // nothing to measure
        assertFalse("Серия" in rows)
        assertFalse("План" in rows)
        val english = doc(AppLanguage.EN, r = r).rows.associateBy { it.label }
        assertEquals("none", english.getValue("Newly memorized").value)
    }

    @Test
    fun theSurahsRepeatedAreCutAfterTheLimit() {
        val many = (1..40).map { surah -> entry(ReviewKind.RECENT, surah, 1, 1, today) }
        val r = report(s = sources(log = many))
        val line = doc(AppLanguage.RU, r = r).rows[1].lines.single()
        assertTrue(line.startsWith("Суры: S1, S2, "), line)
        assertTrue(line.endsWith("и ещё ${40 - ReportComposer.MaxReviewedSurahs}"), line)
    }

    @Test
    fun manyNewRangesAreCutAfterTheLimit() {
        val records = (1..40).map { surah -> entry(ReviewKind.NEW, surah, 1, 1, today) }
        val lines = doc(AppLanguage.RU, r = report(s = sources(log = records))).rows.first().lines
        assertEquals(ReportComposer.MaxNewRanges + 1, lines.size)
        assertEquals("S1 1", lines.first())
        assertEquals("и ещё ${40 - ReportComposer.MaxNewRanges}", lines.last())
    }

    @Test
    fun difficultAyahsAreListedPerSurahAndCutAfterTheLimit() {
        val passages = (1..12).map { surah -> Fx.passage(surah.toLong(), surah + 100, 1, 3, hard = mapOf(2 to 1)) }
        val r = report(s = sources(passages = passages))
        val lines = doc(AppLanguage.RU, r = r).rows.first { it.label == "Трудные аяты" }.lines
        assertEquals(ReportComposer.MaxHardSurahs + 1, lines.size)
        assertEquals("и ещё ${12 - ReportComposer.MaxHardSurahs}", lines.last())
    }

    @Test
    fun ayahNumbersMakeRangesOfRunsOfThree() {
        assertEquals("3", ReportComposer.ayahNumbers(listOf(3), 12))
        assertEquals("1, 2", ReportComposer.ayahNumbers(listOf(1, 2), 12))
        assertEquals("1–3", ReportComposer.ayahNumbers(listOf(1, 2, 3), 12))
        assertEquals("3, 5–7, 9", ReportComposer.ayahNumbers(listOf(3, 5, 6, 7, 9), 12))
        assertEquals("1, 3, 5…", ReportComposer.ayahNumbers(listOf(1, 3, 5, 7, 9), 3))
        assertEquals("", ReportComposer.ayahNumbers(emptyList(), 12))
    }

    @Test
    fun theRingOfTheCardCarriesThePercentInTheLanguageOfTheText() {
        assertEquals("<1%", doc(AppLanguage.RU).percentText)
        assertEquals("<%1", doc(AppLanguage.TR).percentText)
        val big = sources(passages = listOf(Fx.passage(1, 2, 1, 286), Fx.passage(2, 3, 1, 200)))
        assertEquals("8%", doc(AppLanguage.RU, r = report(s = big)).percentText) // 486 of 6236 = 7.8%
        assertEquals("%8", doc(AppLanguage.TR, r = report(s = big)).percentText)
        assertTrue(doc(AppLanguage.RU, r = report(s = big)).fraction > 0.07f)
    }

    // -- The plan in the report -----------------------------------------------------------------------

    private fun planLines(language: AppLanguage, line: PlanLineUi): List<String> {
        val base = report()
        val d = doc(language, r = base.copy(plan = line))
        return d.rows.first { it.label == reportCopy(language).planLabel }.lines
    }

    @Test
    fun everyStatusOfThePlanIsToldInEveryLanguage() {
        val finish = today + 40
        val lines = listOf(
            PlanLineUi(PlanStatus.Finished, HifzScope.JUZ_30),
            PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_29, finish),
            PlanLineUi(PlanStatus.Behind, HifzScope.FROM_START, finish, ayahs = 12),
            PlanLineUi(PlanStatus.NeedPace, HifzScope.CUSTOM, finish, ayahs = 7),
            PlanLineUi(PlanStatus.Ahead, HifzScope.JUZ_30, finish),
            PlanLineUi(PlanStatus.OnTrack, HifzScope.JUZ_30, finish),
        )
        for (language in AppLanguage.entries) {
            for (line in lines) {
                val text = planLines(language, line)
                assertTrue(text.isNotEmpty(), "${language.name} ${line.status}")
                text.forEach { assertFalse('{' in it || '}' in it, "${language.name} ${line.status}: $it") }
            }
        }
        assertEquals(
            listOf("Прогноз окончания: ~9 декабря", "Отставание от срока: 12 аятов"),
            planLines(AppLanguage.RU, lines[2]),
        )
        assertEquals(listOf("Прогноз окончания: ~9 декабря", "Идёт по графику"), planLines(AppLanguage.RU, lines[5]))
        assertEquals(listOf("Прогноз окончания: ~9 декабря", "Опережает срок"), planLines(AppLanguage.RU, lines[4]))
        assertEquals(listOf("Прогноз окончания: ~9 декабря", "Для срока нужно 7 аятов в день"), planLines(AppLanguage.RU, lines[3]))
        assertEquals(listOf("все суры плана выучены"), planLines(AppLanguage.RU, lines[0]))
    }

    @Test
    fun aDateInAnotherYearGetsItsYear() {
        val line = PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_30, today + 120)
        assertEquals(listOf("Прогноз окончания: ~27 февраля 2027"), planLines(AppLanguage.RU, line))
    }
}
