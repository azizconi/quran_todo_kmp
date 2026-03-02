package tj.app.quran_todo.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
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
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemeModeSetter
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.LocalThemePaletteSetter
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.LocalAyahCardStyle
import tj.app.quran_todo.common.theme.LocalAyahCardStyleSetter
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.AyahCardStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.softSurface
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface
import tj.app.quran_todo.common.utils.currentLocalDate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.presentation.components.LanguagePicker
import tj.app.quran_todo.presentation.onboarding.featureGuideItems

@Composable
fun SettingsScreen() {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val setLanguage = LocalAppLanguageSetter.current
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalThemeModeSetter.current
    val themePalette = LocalThemePalette.current
    val setThemePalette = LocalThemePaletteSetter.current
    val readingFont = LocalReadingFontStyle.current
    val setReadingFont = LocalReadingFontStyleSetter.current
    val ayahCardStyle = LocalAyahCardStyle.current
    val setAyahCardStyle = LocalAyahCardStyleSetter.current
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current
    val todayEpochDay = currentLocalDate().toEpochDays()
    val targetDaysLeft = (settings.targetEpochDay - todayEpochDay).coerceAtLeast(1)
    val guideItems = featureGuideItems(strings)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        backgroundColor = MaterialTheme.colors.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SettingsCard(title = strings.languageLabel) {
                    LanguagePicker(current = language, onSelected = setLanguage)
                }
            }

            item {
                SettingsCard(title = strings.themeLabel) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeToggle(
                            selected = themeMode,
                            onSelected = setThemeMode,
                            lightLabel = strings.themeLight,
                            darkLabel = strings.themeDark
                        )
                        ThemePaletteToggle(
                            selected = themePalette,
                            onSelected = setThemePalette,
                            sandLabel = strings.themeSand,
                            oceanLabel = strings.themeOcean,
                            forestLabel = strings.themeForest,
                            title = strings.themeStyleLabel
                        )
                    }
                }
            }

            item {
                SettingsCard(title = strings.readingFontLabel) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReadingFontSelector(
                            selected = readingFont,
                            onSelected = setReadingFont
                        )
                        Text(
                            text = "${strings.ayahSizeLabel}: ${settings.readingFontSize}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.mutedText
                        )
                        Slider(
                            value = settings.readingFontSize.toFloat(),
                            onValueChange = { value ->
                                setSettings(settings.copy(readingFontSize = value.toInt().coerceIn(18, 34)))
                            },
                            onValueChangeFinished = {
                                AppTelemetry.logEvent(
                                    name = "settings_reading_size_changed",
                                    params = mapOf("size" to settings.readingFontSize.toString())
                                )
                            },
                            valueRange = 18f..34f
                        )
                    }
                }
            }

            item {
                SettingsCard(title = strings.cardStyleLabel) {
                    AyahCardStyleSelector(
                        selected = ayahCardStyle,
                        onSelected = setAyahCardStyle
                    )
                }
            }

            item {
                SettingsCard(title = strings.planTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepperRow(
                            label = strings.dailyGoalLabel,
                            value = settings.dailyGoal,
                            min = 1,
                            max = 50,
                            onChange = { setSettings(settings.copy(dailyGoal = it)) }
                        )
                        StepperRow(
                            label = strings.focusMinutesLabel,
                            value = settings.focusMinutes,
                            min = 5,
                            max = 60,
                            onChange = { setSettings(settings.copy(focusMinutes = it)) }
                        )
                        ToggleRow(
                            label = strings.remindersLabel,
                            enabled = settings.remindersEnabled,
                            onToggle = { setSettings(settings.copy(remindersEnabled = it)) }
                        )
                        StepperRow(
                            label = strings.targetAyahsLabel,
                            value = settings.targetAyahs,
                            min = 50,
                            max = 6236,
                            onChange = { setSettings(settings.copy(targetAyahs = it)) }
                        )
                        StepperRow(
                            label = strings.deadlineDaysLabel,
                            value = targetDaysLeft,
                            min = 1,
                            max = 365,
                            onChange = { days ->
                                setSettings(settings.copy(targetEpochDay = todayEpochDay + days))
                            }
                        )
                        ToggleRow(
                            label = strings.examModeDefaultLabel,
                            enabled = settings.examModeEnabled,
                            onToggle = { setSettings(settings.copy(examModeEnabled = it)) }
                        )
                    }
                }
            }

            item {
                SettingsCard(title = strings.featureGuideTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.featureGuideSubtitle,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.mutedText
                        )
                        guideItems.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.subtitle2,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.mutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingFontSelector(
    selected: ReadingFontStyle,
    onSelected: (ReadingFontStyle) -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadingFontStyle.values().toList().chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { style ->
                    ThemeOptionChip(
                        label = style.label(strings),
                        isSelected = selected == style,
                        onClick = { onSelected(style) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AyahCardStyleSelector(
    selected: AyahCardStyle,
    onSelected: (AyahCardStyle) -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AyahCardStyle.values().toList().chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { style ->
                    ThemeOptionChip(
                        label = style.label(strings),
                        isSelected = selected == style,
                        onClick = { onSelected(style) }
                    )
                }
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
        border = BorderStroke(1.dp, MaterialTheme.colors.subtleBorder),
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
private fun ThemePaletteToggle(
    selected: ThemePalette,
    onSelected: (ThemePalette) -> Unit,
    sandLabel: String,
    oceanLabel: String,
    forestLabel: String,
    title: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.mutedText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOptionChip(
                label = sandLabel,
                isSelected = selected == ThemePalette.SAND,
                onClick = { onSelected(ThemePalette.SAND) }
            )
            ThemeOptionChip(
                label = oceanLabel,
                isSelected = selected == ThemePalette.OCEAN,
                onClick = { onSelected(ThemePalette.OCEAN) }
            )
            ThemeOptionChip(
                label = forestLabel,
                isSelected = selected == ThemePalette.FOREST,
                onClick = { onSelected(ThemePalette.FOREST) }
            )
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colors
    val background = if (isSelected) {
        colors.tintedSurface(colors.primary, emphasis = 0.2f)
    } else {
        colors.softSurface
    }
    val textColor = if (isSelected) {
        colors.primary
    } else {
        colors.onSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
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
                tint = MaterialTheme.colors.mutedText,
                modifier = Modifier
                    .clickable(enabled = value > min) {
                        onChange((value - 1).coerceAtLeast(min))
                    }
                    .padding(6.dp)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colors.mutedText,
                modifier = Modifier
                    .clickable(enabled = value < max) {
                        onChange((value + 1).coerceAtMost(max))
                    }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.body2)
        val chipLabel = if (enabled) strings.enabledLabel else strings.disabledLabel
        ThemeOptionChip(
            label = chipLabel,
            isSelected = enabled,
            onClick = { onToggle(!enabled) }
        )
    }
}
