package tj.app.quran_todo.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.i18n.TrustCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.settings.ReaderPreviewCard
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Step 11: the plan is ready. "Memorize": the first lesson, what is known, when the plan ends, the reminder; "Read and listen":
 * "Start with Al-Fatihah". Both carry the switch of the anonymous statistics (the same one as in the settings). The buttons are
 * in the footer of the screen.
 */
@Composable
internal fun ReadyStep(
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    trustCopy: TrustCopy,
    ui: OnboardingUi,
    memorize: Boolean,
    reminderTime: String?,
    fontStyle: ReadingFontStyle,
    fontSize: Int,
    analyticsEnabled: Boolean,
    onAnalytics: (Boolean) -> Unit,
) {
    val colors = QhTheme.colors
    StepColumn {
        if (memorize) {
            StepHeader(copy.readyTitle, copy.readySubtitle)
            QhCard(contentPadding = PaddingValues(QhSpacing.cardLarge)) {
                PlanSummary(ui, copy, planCopy, reminderTime)
            }
        } else {
            StepHeader(copy.readyReadTitle, copy.readyReadBody)
            // Where the person will start, in the font and the size they just chose.
            ReaderPreviewCard(
                caption = passageRangeLabel(ui.surahName(1) ?: copy.fatihaName, 1, 3),
                ayahs = ui.fatiha,
                fontStyle = fontStyle,
                fontSize = fontSize,
            )
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

/** The lines of the plan card: the first lesson, what is known, when the plan ends, the reminder. */
@Composable
private fun PlanSummary(
    ui: OnboardingUi,
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    reminderTime: String?,
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
        if (reminderTime != null) {
            SummaryRow(Icons.Rounded.NotificationsActive, colors.gold, copy.readyReminder(reminderTime))
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
