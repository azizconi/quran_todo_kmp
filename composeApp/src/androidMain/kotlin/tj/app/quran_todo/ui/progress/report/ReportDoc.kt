package tj.app.quran_todo.ui.progress.report

import kotlinx.datetime.LocalDate
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.ReportCopy
import tj.app.quran_todo.ui.i18n.analyticsCopy
import tj.app.quran_todo.ui.i18n.ayahCountLabel
import tj.app.quran_todo.ui.i18n.lessonCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.i18n.reportCopy
import tj.app.quran_todo.ui.progress.PlanStatus

/** One line of the report: a [label], its [value] and, under it, [lines] (as bullets, or plain). */
internal class ReportRow(
    val label: String,
    val value: String,
    val lines: List<String> = emptyList(),
    val bulleted: Boolean = false,
)

/**
 * The report in words, ready for the two things made of it: the text for the messenger ([toPlainText]) and the card picture
 * (`ReportImage`). Both read this one document, so they always say the same.
 */
internal class ReportDoc(
    val title: String,
    /** "24 Oct – 30 Oct". */
    val period: String,
    /** "7%" ("%7" in Turkish, "<1%" while less than a percent is memorized). */
    val percentText: String,
    /** The ring of the card, 0..1. */
    val fraction: Float,
    /** "Memorized in total: 412 of 6236 ayahs". */
    val caption: String,
    val rows: List<ReportRow>,
    /** The heat strip of the days of the period: records per day, oldest first (empty: no strip). */
    val strip: List<Int> = emptyList(),
    /** The caption of the strip ("Days of the period"). */
    val stripLabel: String = "",
)

/** The pure function "report -> words in the language of the app", covered by unit tests. */
internal object ReportComposer {
    /** Ranges of new ayahs listed before "and 5 more": a month of short surahs can be many. */
    const val MaxNewRanges = 25

    /** Surahs named in the "reviewed" line before "and 5 more". */
    const val MaxReviewedSurahs = 15

    /** Surahs with difficult ayahs listed before "and 2 more". */
    const val MaxHardSurahs = 8

    /** Pieces ("3", "5-7") of one surah's difficult ayahs before the ellipsis. */
    const val MaxAyahPieces = 12

    fun compose(report: ProgressReport, language: AppLanguage, names: List<String>): ReportDoc {
        val copy = reportCopy(language)
        val progress = progressCopy(language)
        val plan = planCopy(language)
        val nameOf = { surah: Int -> names.getOrNull(surah - 1) ?: "№ $surah" }
        val hero = report.hero

        val percentText = if (hero.belowOnePercent) {
            if (progress.percentFirst) "<%1" else "<1%"
        } else {
            progress.percentText(hero.percent)
        }

        val rows = buildList {
            add(
                ReportRow(
                    label = copy.newLabel,
                    value = if (report.newAyahs == 0) copy.none else ayahCountLabel(report.newAyahs, language),
                    lines = newLines(report, copy, nameOf),
                    bulleted = true,
                ),
            )
            add(
                ReportRow(
                    label = copy.reviewedLabel,
                    value = if (report.repetitions == 0) {
                        copy.none
                    } else {
                        "${ayahCountLabel(report.reviewedAyahs, language)} · ${lessonCopy(language).repetitions(report.repetitions)}"
                    },
                    lines = reviewedLines(report, copy, nameOf),
                ),
            )
            report.retentionPercent?.let { percent ->
                add(ReportRow(copy.retentionLabel, progress.percentText(percent)))
            }
            add(
                ReportRow(
                    label = copy.hardLabel,
                    value = if (report.hardAyahs == 0) copy.none else ayahCountLabel(report.hardAyahs, language),
                    lines = hardLines(report, copy, nameOf),
                    bulleted = true,
                ),
            )
            add(ReportRow(copy.activeDaysLabel, "${report.activeDays}/${report.dayCount}"))
            if (report.streak > 0) add(ReportRow(copy.streakLabel, plan.daysLabel(report.streak)))
            planRow(report, copy)?.let(::add)
        }

        return ReportDoc(
            title = copy.title,
            period = "${dateOf(report.fromDay, language)} – ${dateOf(report.toDay, language)}",
            percentText = percentText,
            fraction = hero.fraction,
            caption = copy.total(hero.learnedAyahs, hero.totalAyahs),
            rows = rows,
            strip = report.strip,
            stripLabel = analyticsCopy(language).reportStripLabel,
        )
    }

    private fun dateOf(epochDay: Int, language: AppLanguage): String {
        val date = LocalDate.fromEpochDays(epochDay)
        return progressCopy(language).date(date.dayOfMonth, date.monthNumber)
    }

    /** "Al-Masad 1-5" per range of new ayahs, in the order they were learned; the ranges past the limit become "and 3 more". */
    private fun newLines(report: ProgressReport, copy: ReportCopy, nameOf: (Int) -> String): List<String> {
        val lines = report.newRanges.take(MaxNewRanges).map { passageRangeLabel(nameOf(it.surah), it.startAyah, it.endAyah) }
        val rest = report.newRanges.size - MaxNewRanges
        return if (rest > 0) lines + copy.moreSurahs(rest) else lines
    }

    /** "Surahs: A, B, C and 4 more" under the "reviewed" line; nothing when nothing was reviewed. */
    private fun reviewedLines(report: ProgressReport, copy: ReportCopy, nameOf: (Int) -> String): List<String> {
        if (report.reviewedSurahs.isEmpty()) return emptyList()
        val shown = report.reviewedSurahs.take(MaxReviewedSurahs).joinToString(", ") { nameOf(it) }
        val rest = report.reviewedSurahs.size - MaxReviewedSurahs
        return listOf(copy.surahs(if (rest > 0) "$shown ${copy.moreSurahs(rest)}" else shown))
    }

    /** "Al-Masad 3, 5-7" per surah, the most affected first; the surahs past the limit become "and 2 more". */
    private fun hardLines(report: ProgressReport, copy: ReportCopy, nameOf: (Int) -> String): List<String> {
        val lines = report.hard.take(MaxHardSurahs).map { "${nameOf(it.surah)} ${ayahNumbers(it.ayahs, MaxAyahPieces)}" }
        val rest = report.hard.size - MaxHardSurahs
        return if (rest > 0) lines + copy.moreSurahs(rest) else lines
    }

    /** The plan: the name of the preset, then where the pace leads and whether that is ahead of or behind the date. */
    private fun planRow(report: ProgressReport, copy: ReportCopy): ReportRow? {
        val line = report.plan
        if (line.status == PlanStatus.NoPlan) return null
        val language = copy.language
        val scope = line.scope?.let { progressCopy(language).scopeName(it) } ?: return null
        val finish = line.finishEpochDay?.let { copy.finish(planCopy(language).dateLabel(it, report.toDay)) }
        val lines = when (line.status) {
            PlanStatus.Finished -> listOf(copy.finishedText)
            PlanStatus.Finish -> listOfNotNull(finish)
            PlanStatus.Behind -> listOfNotNull(finish, copy.behind(line.ayahs))
            PlanStatus.NeedPace -> listOfNotNull(finish, copy.needPace(line.ayahs))
            PlanStatus.Ahead -> listOfNotNull(finish, copy.aheadText)
            PlanStatus.OnTrack -> listOfNotNull(finish, copy.onTrackText)
            PlanStatus.NoPlan -> emptyList()
        }
        return ReportRow(label = copy.planLabel, value = scope, lines = lines)
    }

    /**
     * Ayah numbers as a short list: "3, 5-7, 9". Runs of three or more become a range, the list is cut after [limit] pieces
     * with an ellipsis. [ayahs] ascending and without repeats.
     */
    fun ayahNumbers(ayahs: List<Int>, limit: Int): String {
        val pieces = ArrayList<String>()
        var index = 0
        while (index < ayahs.size) {
            var end = index
            while (end + 1 < ayahs.size && ayahs[end + 1] == ayahs[end] + 1) end++
            when (end - index) {
                0 -> pieces.add("${ayahs[index]}")
                1 -> {
                    pieces.add("${ayahs[index]}")
                    pieces.add("${ayahs[end]}")
                }

                else -> pieces.add("${ayahs[index]}–${ayahs[end]}")
            }
            index = end + 1
        }
        return if (pieces.size > limit) pieces.take(limit).joinToString(", ") + "…" else pieces.joinToString(", ")
    }
}

/**
 * The text that goes next to (or instead of) the picture: the title, the period, the total, then every row with its lines.
 * Plain text (no markup that one messenger shows and another does not), bullets are "•".
 */
internal fun ReportDoc.toPlainText(): String = buildString {
    appendLine(title)
    appendLine(period)
    appendLine()
    appendLine("$caption ($percentText)")
    for (row in rows) {
        appendLine(if (row.value.isBlank()) row.label else "${row.label}: ${row.value}")
        for (line in row.lines) appendLine(if (row.bulleted) "• $line" else line)
    }
}.trimEnd()
