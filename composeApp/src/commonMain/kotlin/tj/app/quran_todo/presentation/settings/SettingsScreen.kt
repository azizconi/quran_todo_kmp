package tj.app.quran_todo.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemeModeSetter
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.presentation.components.LanguagePicker

@Composable
fun SettingsScreen() {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val setLanguage = LocalAppLanguageSetter.current
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalThemeModeSetter.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )

            SettingsCard(title = strings.languageLabel) {
                LanguagePicker(current = language, onSelected = setLanguage)
            }

            SettingsCard(title = strings.themeLabel) {
                ThemeToggle(
                    selected = themeMode,
                    onSelected = setThemeMode,
                    lightLabel = strings.themeLight,
                    darkLabel = strings.themeDark
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun ThemeToggle(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    lightLabel: String,
    darkLabel: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeOptionChip(
            label = lightLabel,
            isSelected = selected == ThemeMode.LIGHT,
            onClick = { onSelected(ThemeMode.LIGHT) }
        )
        ThemeOptionChip(
            label = darkLabel,
            isSelected = selected == ThemeMode.DARK,
            onClick = { onSelected(ThemeMode.DARK) }
        )
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) {
        MaterialTheme.colors.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.04f)
    }
    val textColor = if (isSelected) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
