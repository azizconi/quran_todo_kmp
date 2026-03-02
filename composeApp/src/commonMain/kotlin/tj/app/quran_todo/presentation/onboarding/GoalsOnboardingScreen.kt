package tj.app.quran_todo.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.utils.currentLocalDate

@Composable
fun GoalsOnboardingScreen(
    initialSettings: AppSettings,
    onContinue: (AppSettings) -> Unit,
) {
    val strings = LocalAppStrings.current
    val todayEpochDay = remember { currentLocalDate().toEpochDays() }
    var dailyGoal by remember { mutableStateOf(initialSettings.dailyGoal.coerceIn(1, 50)) }
    var deadlineDays by remember {
        mutableStateOf((initialSettings.targetEpochDay - todayEpochDay).coerceIn(1, 365))
    }
    var remindersEnabled by remember { mutableStateOf(initialSettings.remindersEnabled) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        backgroundColor = MaterialTheme.colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.onboardingGoalsTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.onboardingGoalsSubtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepperRow(
                        label = strings.dailyGoalLabel,
                        value = dailyGoal,
                        onMinus = { dailyGoal = (dailyGoal - 1).coerceAtLeast(1) },
                        onPlus = { dailyGoal = (dailyGoal + 1).coerceAtMost(50) }
                    )
                    StepperRow(
                        label = strings.deadlineDaysLabel,
                        value = deadlineDays,
                        onMinus = { deadlineDays = (deadlineDays - 1).coerceAtLeast(1) },
                        onPlus = { deadlineDays = (deadlineDays + 1).coerceAtMost(365) }
                    )
                    ToggleRow(
                        label = strings.remindersLabel,
                        value = remindersEnabled,
                        onToggle = { remindersEnabled = !remindersEnabled }
                    )
                    Text(
                        text = strings.remindersDefaultTimeLabel,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.mutedText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onContinue(
                        initialSettings.copy(
                            dailyGoal = dailyGoal,
                            remindersEnabled = remindersEnabled,
                            targetEpochDay = todayEpochDay + deadlineDays
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.onboardingStartLabel)
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.body2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .clickable { onMinus() }
                    .padding(6.dp)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.surface,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .clickable { onPlus() }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    value: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.body2)
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (value) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            modifier = Modifier
                .clickable { onToggle() }
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = if (value) LocalAppStrings.current.enabledLabel else LocalAppStrings.current.disabledLabel,
                style = MaterialTheme.typography.caption,
                color = if (value) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
