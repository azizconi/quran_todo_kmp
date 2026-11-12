package tj.app.quran_todo.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.hifz.schedule.SurahCompletion
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.rememberQuizCopy
import tj.app.quran_todo.ui.i18n.rememberSummaryExtrasCopy
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.OrnamentDivider
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.rememberDrillCopy
import tj.app.quran_todo.ui.motion.CelebrationBurst
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The end of the lesson: the title (the header steps already show what was done), the streak, the forecast of the plan and what waits
 * tomorrow. The celebration plays only when the lesson was closed in this visit (not when it is opened again).
 */
@Composable
internal fun LessonSummary(
    viewModel: LessonViewModel,
    copy: LessonCopy,
    summary: LessonUi.Summary,
    onHome: () -> Unit,
    onOpenDrill: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    // "Check yourself · 5 questions" (task F2b): offered when enough is memorized for the quiz.
    val hifz = koinInject<HifzRepository>()
    val quizCopy = rememberQuizCopy(language)
    val quizReady by remember(hifz) { hifz.coveredAyahs().map { QuizBuilder.isAvailable(it) }.distinctUntilChanged() }
        .collectAsState(initial = false)
    val plan = summary.plan
    val steps = remember(plan) { LessonCards.steps(plan) }
    // A surah of the plan that today's new passage completed (task U13).
    val hifzPlan by remember(hifz) { hifz.plan() }.collectAsState(initial = null)
    val covered by remember(hifz) { hifz.coveredAyahs() }.collectAsState(initial = emptyMap())
    val finishedSurah = remember(plan, hifzPlan, covered) {
        SurahCompletion.finishedToday(plan.newBlock, hifzPlan?.surahOrder.orEmpty(), covered)
    }
    val done = steps.sumOf { it.done }
    val streak by viewModel.streak.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    val names by viewModel.chapterNames.collectAsState()
    val hardCount by viewModel.hardCount.collectAsState()
    val drillCopy = rememberDrillCopy()

    // A small burst follows when the lesson was closed just now.
    var burst by remember { mutableIntStateOf(0) }
    var celebrated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(done) {
        delay(240)
        if (summary.closedNow && !celebrated) {
            delay(260)
            celebrated = true
            burst++
        }
    }

    val tomorrowNew = plan.tomorrow.newPassage?.let { range ->
        "${viewModel.surahTitle(range.surah, language, names)} ${rangeLabel(range)}"
    }
    val forecastLine = forecast?.let { plan ->
        when {
            plan.finished -> copy.planFinished
            plan.behindAyahs > 0 -> copy.forecastBehind(plan.behindAyahs)
            plan.finishEpochDay != null -> copy.forecastFinish(formatDay(plan.finishEpochDay!!, language))
            else -> null
        }
    }

    val durations by viewModel.lessonDurations.collectAsState()
    val extrasCopy = rememberSummaryExtrasCopy(language)
    val takesLine = LessonDurations.medianMinutes(durations)?.let { extrasCopy.takesAbout(it) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x6, bottom = QhSpacing.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CelebrationBurst(trigger = burst, modifier = Modifier.matchParentSize(), radius = 150.dp)
                Text(
                    text = copy.summaryTitle,
                    style = QhTheme.type.headline,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                text = copy.summaryCaption,
                style = QhTheme.type.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
            finishedSurah?.let { surah ->
                OrnamentDivider(Modifier.padding(top = QhSpacing.x2))
                Text(
                    text = extrasCopy.surahComplete(viewModel.surahTitle(surah, language, names)),
                    style = QhTheme.type.title2,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            QhCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(QhSpacing.cardLarge)) {
                Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    if (streak > 0) SummaryLine(copy.streak(streak))
                    forecastLine?.let { SummaryLine(it) }
                    // The real time of the lesson (task U3), once enough lessons were measured.
                    takesLine?.let { SummaryLine(it) }
                    Text(
                        text = copy.tomorrow(tomorrowNew, plan.tomorrow.dueCount),
                        style = QhTheme.type.title2,
                        color = colors.ink,
                    )
                }
            }

            ReminderAskCard(language = language)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(bottom = QhSpacing.x3)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hardCount > 0 || quizReady) {
                // Secondary ways to go on, as text links above the one primary button.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hardCount > 0) {
                        // The difficult ayahs have a drill of their own (task F2a).
                        QhButton(
                            text = drillCopy.train(hardCount),
                            onClick = onOpenDrill,
                            modifier = Modifier.weight(1f, fill = false),
                            variant = QhButtonVariant.Ghost,
                            size = QhButtonSize.M,
                        )
                    }
                    if (quizReady) {
                        QhButton(
                            text = quizCopy.lessonButton(QuizBuilder.DEFAULT_COUNT),
                            onClick = onOpenQuiz,
                            modifier = Modifier.weight(1f, fill = false),
                            variant = QhButtonVariant.Ghost,
                            size = QhButtonSize.M,
                        )
                    }
                }
            }
            QhButton(
                text = copy.toHome,
                onClick = onHome,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(
        text = text,
        style = QhTheme.type.callout,
        color = QhTheme.colors.inkSecondary,
    )
}

private fun rangeLabel(range: AyahRange): String =
    if (range.startAyah == range.endAyah) "${range.startAyah}" else "${range.startAyah}–${range.endAyah}"

private const val MillisPerDay = 24L * 60 * 60 * 1000

/** "14 March" in the language of the app. */
internal fun formatDay(epochDay: Int, language: AppLanguage): String {
    val locale = Locale.forLanguageTag(
        when (language) {
            AppLanguage.RU -> "ru"
            AppLanguage.EN -> "en"
            AppLanguage.TG -> "tg"
            AppLanguage.UZ -> "uz"
            AppLanguage.TR -> "tr"
        },
    )
    // The epoch day is a calendar day, so the date is formatted in UTC, whatever the time zone of the device is.
    val format = SimpleDateFormat("d MMMM", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return format.format(Date(epochDay * MillisPerDay))
}
