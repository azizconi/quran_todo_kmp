package tj.app.quran_todo.ui.onboarding

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.R
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.ReminderCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.reminder.ReminderTimeDialog
import tj.app.quran_todo.ui.reminder.formatReminderTime
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private const val MorningMinute = 7 * 60
private const val EveningMinute = 20 * 60

/**
 * Step 9 (only "Memorize"): the reminder. The time (evening 20:00 by default, as in the settings: the reminder does not come if the
 * lesson is done, and the evening is when it helps), a preview of the notification with the text it will really have, and - in the
 * footer of the screen - "Remind me", which asks the system for the permission HERE, and "Not now", which asks nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderStep(
    copy: OnboardingCopy,
    reminderCopy: ReminderCopy,
    lessonRange: String?,
    lessonMinutes: Int?,
    minuteOfDay: Int,
    denied: Boolean,
    onMinute: (Int) -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val reduced = LocalReducedMotion.current
    val colors = QhTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    val custom = minuteOfDay != MorningMinute && minuteOfDay != EveningMinute
    val time = formatReminderTime(minuteOfDay, is24Hour)

    StepColumn {
        StepHeader(copy.reminderTitle, copy.reminderSubtitle)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
        ) {
            QhChip(
                label = "${copy.reminderMorning} ${formatReminderTime(MorningMinute, is24Hour)}",
                selected = minuteOfDay == MorningMinute,
                onClick = { onMinute(MorningMinute) },
            )
            QhChip(
                label = "${copy.reminderEvening} ${formatReminderTime(EveningMinute, is24Hour)}",
                selected = minuteOfDay == EveningMinute,
                onClick = { onMinute(EveningMinute) },
            )
            QhChip(
                label = if (custom) "${copy.reminderOther.removeSuffix("…")} · $time" else copy.reminderOther,
                selected = custom,
                onClick = { picking = true },
            )
        }
        NotificationPreview(
            appName = "Quran Hifz",
            time = time,
            title = reminderCopy.lessonTitle,
            body = reminderCopy.lessonBody(
                newRange = lessonRange,
                repetitions = 0,
                minutes = lessonMinutes ?: 1,
                partlyDone = false,
            ),
        )
        AnimatedVisibility(
            visible = denied,
            enter = if (reduced) fadeIn(snap()) else expandVertically(QhMotion.gentle()) + fadeIn(QhMotion.gentle()),
            exit = if (reduced) fadeOut(snap()) else shrinkVertically(QhMotion.gentle()) + fadeOut(QhMotion.gentle()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(QhSpacing.iconBar))
                Text(text = copy.reminderDenied, style = QhTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
            }
        }
    }

    if (picking) {
        ReminderTimeDialog(
            copy = reminderCopy,
            minuteOfDay = minuteOfDay,
            is24Hour = is24Hour,
            onConfirm = { minute ->
                picking = false
                onMinute(minute)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The notification as the shade will show it: the small app icon, the app name and the time, the title and the text. Decorative
 * (screen readers get the title and the text of the screen instead).
 */
@Composable
private fun NotificationPreview(appName: String, time: String, title: String, body: String) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(QhSpacing.x4)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(QhTheme.shapes.full)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stat_quran),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(text = "$appName · $time", style = QhTheme.type.label, color = colors.inkSecondary, maxLines = 1)
        }
        Text(text = title, style = QhTheme.type.title2, color = colors.ink)
        Text(text = body, style = QhTheme.type.body, color = colors.inkSecondary)
    }
}
