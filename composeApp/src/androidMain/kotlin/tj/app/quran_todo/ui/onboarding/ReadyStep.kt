package tj.app.quran_todo.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.i18n.TrustCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** The pace row of this step offers 1-10 ayahs a day; the plan itself allows 20 (the "by a date" mode stays in "My plan"). */
internal const val WheelMaxPerDay = 10

/**
 * Step 4: the plan is ready. The first lesson, what is known, when the plan ends, how long a lesson takes (a range, not a promise),
 * and the pace: a row with - / + that moves the forecast and the first lesson at once. The switch of the anonymous statistics
 * (the same one as in the settings) closes the screen. The buttons are in the footer of the screen.
 */
@Composable
internal fun ReadyStep(
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    trustCopy: TrustCopy,
    ui: OnboardingUi,
    analyticsEnabled: Boolean,
    onAnalytics: (Boolean) -> Unit,
    onPerDay: (Int) -> Unit,
) {
    val colors = QhTheme.colors
    StepColumn {
        StepHeader(copy.readyTitle, copy.readySubtitle)
        QhCard(contentPadding = PaddingValues(QhSpacing.cardLarge)) {
            PlanSummary(ui, copy, planCopy)
        }
        QhCard(contentPadding = PaddingValues(horizontal = QhSpacing.cardLarge, vertical = QhSpacing.x2)) {
            PerDayRow(copy = copy, perDay = ui.selection.perDay, onPerDay = onPerDay)
        }
        QhCard(contentPadding = PaddingValues(0.dp)) {
            QhSwitchRow(
                title = trustCopy.analyticsTitle,
                description = trustCopy.analyticsDescription,
                checked = analyticsEnabled,
                onCheckedChange = onAnalytics,
                icon = Icons.Rounded.Shield,
                iconTint = colors.inkSecondary,
            )
        }
    }
}

/** "Ayahs a day" with a minus, the number and a plus; both buttons are 48dp targets and switch off at the ends of 1-10. */
@Composable
private fun PerDayRow(copy: OnboardingCopy, perDay: Int, onPerDay: (Int) -> Unit) {
    val colors = QhTheme.colors
    val value = perDay.coerceIn(HifzConstants.DAILY_NEW_MIN, WheelMaxPerDay)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.readyPerDay,
            style = QhTheme.type.title2,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        QhIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = copy.readyFewer,
            onClick = { onPerDay(value - 1) },
            enabled = value > HifzConstants.DAILY_NEW_MIN,
        )
        Text(
            text = value.toString(),
            style = QhTheme.type.title1.tabular(),
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        QhIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = copy.readyMore,
            onClick = { onPerDay(value + 1) },
            enabled = value < WheelMaxPerDay,
        )
    }
}

/** The lines of the plan card: the first lesson, what is known, when the plan ends, how long a day takes. */
@Composable
private fun PlanSummary(
    ui: OnboardingUi,
    copy: OnboardingCopy,
    planCopy: PlanCopy,
) {
    val colors = QhTheme.colors
    val lesson = ui.firstLesson
    val selection = ui.selection
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
        if (lesson != null) {
            val name = ui.surahName(lesson.surah) ?: lesson.surah.toString()
            SummaryRow(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                tint = colors.primary,
                text = copy.readyLesson(passageRangeLabel(name, lesson.startAyah, lesson.endAyah), ui.lessonMinutes),
                strong = true,
            )
        }
        if (selection.known.isNotEmpty()) {
            SummaryRow(Icons.Rounded.CheckCircle, colors.learned, copy.readyKnown(selection.known.size))
        }
        val finish = ui.forecast.finishEpochDay
        if (finish != null) {
            val presetName = when (selection.preset) {
                PresetChoice.JuzAmma -> planCopy.scopeJuz30
                PresetChoice.JuzTabarak -> planCopy.scopeJuz29
                PresetChoice.FromStart -> planCopy.scopeFromStart
                PresetChoice.Custom, null -> planCopy.scopeCustom
            }
            SummaryRow(Icons.Rounded.CalendarMonth, colors.gold, copy.readyForecast(presetName, planCopy.dateLabel(finish, ui.today)))
        }
        ui.lessonMinutes?.let { minutes ->
            SummaryRow(Icons.Rounded.Schedule, colors.gold, copy.readyMinutes(minutes))
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, tint: Color, text: String, strong: Boolean = false) {
    val colors = QhTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconBar))
        Text(
            text = text,
            style = if (strong) QhTheme.type.title2 else QhTheme.type.body,
            color = if (strong) colors.ink else colors.inkSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
