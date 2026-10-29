package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.ui.components.AyahWheel
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.plan.PaceMode
import tj.app.quran_todo.ui.plan.TargetDateDialog
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** The wheel of this step offers 1-10 ayahs a day; the plan itself allows 20 (a date that needs more is warned about). */
internal const val WheelMaxPerDay = 10

/**
 * Step 8 (only "Memorize"): the pace. A wheel of 1-10 ayahs a day, or - with "I want it by a date" - a date, from which the pace
 * follows. Under it the live forecast ("about 4 months, around 14 March") and the time a lesson takes.
 */
@Composable
internal fun PaceStep(
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    ui: OnboardingUi,
    onPerDay: (Int) -> Unit,
    onMode: (PaceMode) -> Unit,
    onTarget: (Int) -> Unit,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val selection = ui.selection
    val byDate = selection.mode == PaceMode.ByDate
    val forecast = ui.forecast
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    StepColumn {
        StepHeader(copy.paceTitle, copy.paceSubtitle)
        QhCard(contentPadding = PaddingValues(0.dp)) {
            QhSwitchRow(
                title = copy.paceByDate,
                checked = byDate,
                onCheckedChange = { on ->
                    onMode(if (on) PaceMode.ByDate else PaceMode.PerDay)
                    if (on) pickingDate = true
                },
                icon = Icons.Rounded.CalendarMonth,
            )
        }
        if (!byDate) {
            AyahWheel(
                label = planCopy.perDayLabel,
                minimum = HifzConstants.DAILY_NEW_MIN,
                maximum = WheelMaxPerDay,
                value = selection.perDay.coerceIn(HifzConstants.DAILY_NEW_MIN, WheelMaxPerDay),
                onValueChange = onPerDay,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val target = selection.targetDay ?: ui.today
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = { pickingDate = true }, shape = QhTheme.shapes.md)
                    .background(colors.surfaceMuted, QhTheme.shapes.md)
                    .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${planCopy.finishBy}: ${planCopy.dateLabel(target, ui.today)}"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.primary, modifier = Modifier.size(QhSpacing.iconBar))
                Text(planCopy.finishBy, style = QhTheme.type.callout, color = colors.inkSecondary, modifier = Modifier.weight(1f))
                Text(planCopy.dateLabel(target, ui.today), style = QhTheme.type.title2.tabular(), color = colors.primary)
            }
            forecast.requiredPerDay?.let { required ->
                Text(
                    text = planCopy.needPerDay(required.coerceAtLeast(1)),
                    style = QhTheme.type.title2,
                    color = if (forecast.tooFast) colors.due else colors.ink,
                )
            }
        }

        // The forecast: when the plan is done, and what a day costs.
        QhCard(Modifier.fillMaxWidth()) {
            val finish = forecast.finishEpochDay
            if (finish != null) {
                Text(
                    text = copy.forecast(OnboardingPlanMath.paceSpan(finish - ui.today), planCopy.dateLabel(finish, ui.today)),
                    style = QhTheme.type.title1.tabular(),
                    color = colors.ink,
                )
            }
            val minutes = ui.lessonMinutes
            if (minutes != null) {
                Row(
                    Modifier.padding(top = QhSpacing.x2),
                    horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.primary, modifier = Modifier.size(QhSpacing.iconInline))
                    Text(planCopy.minutesPerDay(minutes), style = QhTheme.type.title2, color = colors.inkSecondary)
                }
            }
            val warning = when {
                forecast.tooFast -> planCopy.tooFast(forecast.requiredPerDay ?: HifzConstants.DAILY_NEW_MAX)
                minutes != null && minutes > HifzConstants.LESSON_BUDGET_MINUTES -> planCopy.longLesson(minutes)
                else -> null
            }
            AnimatedVisibility(
                visible = warning != null,
                enter = if (reduced) fadeIn(snap()) else expandVertically(QhMotion.gentle()) + fadeIn(QhMotion.gentle()),
                exit = if (reduced) fadeOut(snap()) else shrinkVertically(QhMotion.gentle()) + fadeOut(QhMotion.gentle()),
            ) {
                Row(
                    Modifier.padding(top = QhSpacing.x3),
                    horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.due, modifier = Modifier.size(QhSpacing.iconInline))
                    // The last text stays while the line fades out.
                    Text(text = warning.orEmpty(), style = QhTheme.type.callout, color = colors.ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (pickingDate) {
        TargetDateDialog(
            initialDay = selection.targetDay ?: OnboardingPlanMath.suggestedTarget(selection, ui.today),
            today = ui.today,
            copy = planCopy,
            onPick = onTarget,
            onDismiss = { pickingDate = false },
        )
    }
}
