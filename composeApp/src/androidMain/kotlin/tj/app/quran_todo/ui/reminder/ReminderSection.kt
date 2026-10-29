package tj.app.quran_todo.ui.reminder

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.koin.compose.koinInject
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.i18n.ReminderCopy
import tj.app.quran_todo.ui.i18n.rememberReminderCopy
import tj.app.quran_todo.ui.settings.SettingsSection
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The "Reminder" section of the settings: the switch "Remind me about the lesson", the time, and the footnote that it does not
 * come when the lesson is done.
 *
 * - Turning it on asks for the notification permission at that moment (Android 13 and later). A refusal puts the switch back, says so
 *   in a toast, and keeps a row under the switch that explains it and opens the system settings of the app.
 * - The same row shows whenever the reminder is on but notifications are off in the system (the permission was taken back, the
 *   channel switched off, or a copy with the reminder on was restored on a phone that never allowed it). It is checked again each
 *   time the person comes back to the screen, for example from the system settings.
 */
@Composable
internal fun ReminderSection(language: AppLanguage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = koinInject<UiPrefs>()
    val controller = remember(prefs) { ReminderController(context.applicationContext, prefs) }
    val copy = rememberReminderCopy(language)
    val toaster = LocalToaster.current
    val colors = QhTheme.colors

    val enabled by prefs.reminderEnabled.collectAsState()
    val minuteOfDay by prefs.reminderMinuteOfDay.collectAsState()

    // The system state is read when the section is shown and each time the screen is resumed (back from the system settings).
    var systemRevision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { systemRevision++ }
    val blocked = remember(systemRevision, enabled) { ReminderPermission.isBlocked(context) }
    var refused by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemRevision++
        if (granted) {
            refused = false
            controller.setEnabled(true)
        } else {
            refused = true
            toaster.show(copy.deniedToast, ToastKind.Error)
        }
    }

    SettingsSection(title = copy.sectionTitle, modifier = modifier, footnote = copy.footnote) {
        QhSwitchRow(
            title = copy.toggleTitle,
            checked = enabled,
            onCheckedChange = { on ->
                when {
                    !on -> {
                        refused = false
                        controller.setEnabled(false)
                    }
                    ReminderPermission.needsRequest(context) -> permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else -> {
                        refused = false
                        controller.setEnabled(true)
                    }
                }
            },
            icon = Icons.Rounded.NotificationsActive,
            iconTint = colors.gold,
        )
        QhRowDivider()
        QhValueRow(
            title = copy.timeTitle,
            value = formatReminderTime(minuteOfDay, DateFormat.is24HourFormat(context)),
            onClick = { pickingTime = true },
            icon = Icons.Rounded.Schedule,
            iconTint = colors.primary,
        )
        if (blocked && (enabled || refused)) {
            QhRowDivider()
            BlockedNotice(copy = copy, onOpenSettings = { ReminderPermission.openSystemSettings(context) })
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            copy = copy,
            minuteOfDay = minuteOfDay,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { minute ->
                pickingTime = false
                controller.setMinuteOfDay(minute)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** Why the reminder cannot come, and the button to the system settings. */
@Composable
private fun BlockedNotice(copy: ReminderCopy, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.Top,
    ) {
        QhIconTile(Icons.Rounded.NotificationsOff, tint = QhTheme.colors.danger)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            Text(text = copy.blockedTitle, style = QhTheme.type.title2, color = QhTheme.colors.ink)
            Text(text = copy.blockedBody, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
            QhButton(
                text = copy.openSettings,
                onClick = onOpenSettings,
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.S,
            )
        }
    }
}
