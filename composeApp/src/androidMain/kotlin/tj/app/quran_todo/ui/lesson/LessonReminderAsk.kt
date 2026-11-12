package tj.app.quran_todo.ui.lesson

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import org.koin.compose.koinInject
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.i18n.rememberReminderCopy
import tj.app.quran_todo.ui.i18n.rememberSummaryExtrasCopy
import tj.app.quran_todo.ui.reminder.ReminderAsk
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.reminder.ReminderTimeDialog
import tj.app.quran_todo.ui.reminder.formatReminderTime
import tj.app.quran_todo.ui.reminder.rememberReminderPermissionRequest
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * "Remind you about tomorrow's lesson?" on the summary of a finished lesson (task U2), instead of asking during the onboarding.
 * It shows while the reminder is off and the question has never been answered ([ReminderAsk.shouldShow]).
 *
 * - "Remind me" asks for the notification permission where the system needs it, then turns the reminder on at the chosen time
 *   through [ReminderController] (the same path as the settings switch, so the alarm is planned). A refusal replaces the card by the
 *   note the settings use for it.
 * - "Not now" closes the card for good; the settings stay the only place to turn the reminder on later.
 * - The time chip starts at the current time rounded to the half hour; "Other…" opens the time dialog of the settings.
 */
@Composable
internal fun ReminderAskCard(language: AppLanguage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = koinInject<UiPrefs>()
    val controller = remember(prefs) { ReminderController(context.applicationContext, prefs) }
    val copy = rememberSummaryExtrasCopy(language)
    val reminderCopy = rememberReminderCopy(language)
    val colors = QhTheme.colors

    val enabled by prefs.reminderEnabled.collectAsState()
    val asked by prefs.reminderAsked.collectAsState()
    var denied by rememberSaveable { mutableStateOf(false) }
    var shownLogged by rememberSaveable { mutableStateOf(false) }
    var minuteOfDay by rememberSaveable { mutableIntStateOf(ReminderAsk.proposedMinute(currentMinuteOfDay())) }
    var pickingTime by remember { mutableStateOf(false) }
    val is24Hour = DateFormat.is24HourFormat(context)

    val visible = ReminderAsk.shouldShow(lessonComplete = true, reminderEnabled = enabled, alreadyAnswered = asked)
    LaunchedEffect(visible) {
        if (visible && !shownLogged) {
            shownLogged = true
            AppTelemetry.logEvent("reminder_ask_shown")
        }
    }

    val requestPermission = rememberReminderPermissionRequest { granted ->
        prefs.setReminderAsked(true)
        if (granted) {
            controller.setMinuteOfDay(minuteOfDay, track = false)
            controller.setEnabled(true)
            AppTelemetry.logEvent("reminder_ask_answer", mapOf("answer" to "yes"))
        } else {
            denied = true
            AppTelemetry.logEvent("reminder_ask_answer", mapOf("answer" to "denied"))
        }
    }

    if (denied) {
        Text(
            text = reminderCopy.deniedToast,
            style = QhTheme.type.callout,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    if (!visible) return

    QhCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(QhSpacing.cardLarge)) {
        Text(text = copy.askTitle, style = QhTheme.type.title2, color = colors.ink)
        Row(
            Modifier.fillMaxWidth().padding(top = QhSpacing.x3),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            QhChip(label = formatReminderTime(minuteOfDay, is24Hour), selected = true, onClick = {})
            QhChip(label = copy.askOtherTime, selected = false, onClick = { pickingTime = true })
        }
        Row(
            Modifier.fillMaxWidth().padding(top = QhSpacing.x2),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            QhButton(
                text = copy.askYes,
                onClick = requestPermission,
                modifier = Modifier.weight(1f),
                variant = QhButtonVariant.Primary,
                size = QhButtonSize.M,
            )
            QhButton(
                text = copy.askNo,
                onClick = {
                    prefs.setReminderAsked(true)
                    AppTelemetry.logEvent("reminder_ask_answer", mapOf("answer" to "no"))
                },
                modifier = Modifier.weight(1f),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            copy = reminderCopy,
            minuteOfDay = minuteOfDay,
            is24Hour = is24Hour,
            onConfirm = { minute ->
                pickingTime = false
                minuteOfDay = minute
            },
            onDismiss = { pickingTime = false },
        )
    }
}

private fun currentMinuteOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}
