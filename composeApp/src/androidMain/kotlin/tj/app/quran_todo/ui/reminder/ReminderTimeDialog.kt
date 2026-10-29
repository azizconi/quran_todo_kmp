package tj.app.quran_todo.ui.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.i18n.ReminderCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Picks the time of the reminder: the Material 3 clock in a card dialog of the app (the same frame as the other dialogs of the
 * settings). "Done" returns the minutes after midnight, "Cancel" and a tap outside change nothing. [is24Hour] follows the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(
    copy: ReminderCopy,
    minuteOfDay: Int,
    is24Hour: Boolean,
    onConfirm: (minuteOfDay: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.xl
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = is24Hour,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = QhSpacing.x5)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, shape)
                .verticalScroll(rememberScrollState())
                .padding(QhSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            QhIconTile(Icons.Rounded.Schedule, tint = colors.primary)
            Text(
                text = copy.timeDialogTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.surfaceMuted,
                    clockDialSelectedContentColor = colors.onPrimary,
                    clockDialUnselectedContentColor = colors.ink,
                    selectorColor = colors.primary,
                    containerColor = colors.surface,
                    periodSelectorBorderColor = colors.hairline,
                    periodSelectorSelectedContainerColor = colors.primaryContainer,
                    periodSelectorUnselectedContainerColor = colors.surface,
                    periodSelectorSelectedContentColor = colors.primary,
                    periodSelectorUnselectedContentColor = colors.inkSecondary,
                    timeSelectorSelectedContainerColor = colors.primaryContainer,
                    timeSelectorUnselectedContainerColor = colors.surfaceMuted,
                    timeSelectorSelectedContentColor = colors.primary,
                    timeSelectorUnselectedContentColor = colors.ink,
                ),
            )
            QhButton(
                text = copy.done,
                onClick = { onConfirm(state.hour * 60 + state.minute) },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Primary,
                size = QhButtonSize.M,
            )
            QhButton(
                text = copy.cancel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }
}
