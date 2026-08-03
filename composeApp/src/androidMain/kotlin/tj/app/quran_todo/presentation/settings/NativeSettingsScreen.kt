package tj.app.quran_todo.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.NativeSettingsCopy
import tj.app.quran_todo.common.i18n.downloadingLabel
import tj.app.quran_todo.common.i18n.nativeSettingsCopy
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.common.theme.nativeReaderThemeColors
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
fun NativeSettingsScreen(
    onBack: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
) {
    var language by remember { mutableStateOf(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()) }
    val copy = nativeSettingsCopy(language)
    val isSystemDark = isSystemInDarkTheme()
    var themeMode by remember {
        mutableStateOf(
            ThemeStorage.getSavedThemeMode() ?: if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT,
        )
    }
    val themePalette = remember { ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST }
    var readerPreferences by remember { mutableStateOf(SurahReaderPreferences.load()) }
    var readingFontSize by remember { mutableStateOf(UserSettingsStorage.getReadingFontSize()?.coerceIn(24, 34) ?: 28) }
    var fontStyle by remember { mutableStateOf(ThemeStorage.getSavedReadingFontStyle() ?: ReadingFontStyle.MADINAH_MUSHAF) }
    var picker by remember { mutableStateOf<SettingsPicker?>(null) }
    var confirmsRestore by remember { mutableStateOf(false) }
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeState by homeViewModel.uiState.collectAsState()

    fun savePreferences(next: SurahReaderPreferences) {
        readerPreferences = next
        next.save()
        SettingsChangeNotifier.notifyChanged()
    }

    MaterialTheme(colorScheme = settingsColorScheme(themeMode, themePalette)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(copy.settingsTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = copy.back)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    SettingsSection(copy.applicationSection, copy.languageDescription) {
                        SettingsDisclosure(copy.language, languageLabel(language)) {
                            picker = SettingsPicker.Language
                        }
                    }
                }

                item {
                    SettingsSection(copy.readingAudioSection, copy.readerDescription) {
                        SettingsSwitch(copy.showTranslation, copy.showTranslationDescription, readerPreferences.showTranslation) {
                            savePreferences(readerPreferences.copy(showTranslation = it))
                        }
                        SettingsGroupDivider()
                        SettingsSwitch(copy.autoplayOnOpen, copy.autoplayOnOpenDescription, readerPreferences.autoplayOnOpen) {
                            savePreferences(readerPreferences.copy(autoplayOnOpen = it))
                        }
                        SettingsGroupDivider()
                        SettingsSwitch(copy.autoAdvance, copy.autoAdvanceDescription, readerPreferences.autoAdvanceAyahs) {
                            savePreferences(readerPreferences.copy(autoAdvanceAyahs = it))
                        }
                        SettingsGroupDivider()
                        SettingsSwitch(copy.highlightAyah, copy.highlightAyahDescription, readerPreferences.highlightPlayingAyah) {
                            savePreferences(readerPreferences.copy(highlightPlayingAyah = it))
                        }
                        SettingsGroupDivider()
                        SettingsDisclosure(copy.speed, "${readerPreferences.playbackSpeed}×") { picker = SettingsPicker.Speed }
                        SettingsGroupDivider()
                        SettingsDisclosure(copy.repeatAyah, repeatLabel(readerPreferences.repeatCount, copy)) { picker = SettingsPicker.Repeat }
                    }
                }

                item {
                    SettingsSection(copy.appearanceSection, copy.appearanceDescription) {
                        SettingsDisclosure(copy.theme, themeLabel(themeMode, copy)) { picker = SettingsPicker.Theme }
                        SettingsGroupDivider()
                        SettingsDisclosure(
                            copy.quranText,
                            "${fontLabel(fontStyle)} · $readingFontSize pt",
                        ) { picker = SettingsPicker.ReaderTypography }
                    }
                }

                item {
                    SettingsSection(copy.dataSection, copy.dataDescription) {
                        SettingsAction(
                            title = if (homeState.offlineDownloadRunning) {
                                copy.downloadingLabel(homeState.offlineDownloadDone, homeState.offlineDownloadTotal)
                            } else {
                                copy.downloadOffline
                            },
                            description = copy.downloadDescription,
                            enabled = !homeState.offlineDownloadRunning,
                        ) { homeViewModel.startOfflinePackage(language) }
                        SettingsGroupDivider()
                        SettingsAction(
                            title = copy.sync,
                            description = copy.syncDescription,
                        ) {
                            homeViewModel.syncProgressToCloud(
                                AppSettings(
                                    dailyGoal = 5,
                                    focusMinutes = 10,
                                    remindersEnabled = false,
                                    targetAyahs = 300,
                                    targetEpochDay = 0,
                                    examModeEnabled = false,
                                    readingFontSize = readingFontSize,
                                ),
                            )
                        }
                        SettingsGroupDivider()
                        SettingsAction(
                            title = copy.restoreData,
                            description = copy.restoreDescription,
                            destructive = true,
                        ) { confirmsRestore = true }
                        homeState.syncStatusMessage?.let { message ->
                            SettingsGroupDivider()
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (picker == SettingsPicker.ReaderTypography) {
        ReaderTypographySheet(
            copy = copy,
            selectedFont = fontStyle,
            fontSize = readingFontSize,
            onFontSelected = { value ->
                ThemeStorage.saveReadingFontStyle(value)
                fontStyle = value
                SettingsChangeNotifier.notifyChanged()
            },
            onFontSizeChanged = { value ->
                UserSettingsStorage.saveReadingFontSize(value)
                readingFontSize = value
                SettingsChangeNotifier.notifyChanged()
            },
            onDismiss = { picker = null },
        )
    } else {
        picker?.let { selectedPicker ->
        SettingsChoiceSheet(
            picker = selectedPicker,
            copy = copy,
            onDismiss = { picker = null },
            selectedLanguage = language,
            selectedTheme = themeMode,
            selectedSpeed = readerPreferences.playbackSpeed,
            selectedRepeat = readerPreferences.repeatCount,
            onLanguage = { value -> LanguageStorage.saveLanguage(value); language = value; SettingsChangeNotifier.notifyChanged() },
            onTheme = { value -> ThemeStorage.saveThemeMode(value); themeMode = value; onThemeModeChanged(value); SettingsChangeNotifier.notifyChanged() },
            onSpeed = { value -> savePreferences(readerPreferences.copy(playbackSpeed = value)) },
            onRepeat = { value -> savePreferences(readerPreferences.copy(repeatCount = value)) },
        )
        }
    }

    if (confirmsRestore) {
        AlertDialog(
            onDismissRequest = { confirmsRestore = false },
            title = { Text(copy.restoreTitle) },
            text = { Text(copy.restoreMessage) },
            confirmButton = {
                OutlinedButton(onClick = {
                    confirmsRestore = false
                    homeViewModel.restoreProgressFromCloud()
                }) { Text(copy.restore) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmsRestore = false }) { Text(copy.cancel) }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f),
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(content = { content() })
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
    )
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        },
    )
}

@Composable
private fun SettingsDisclosure(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SettingsAction(
    title: String,
    description: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            headlineColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        ),
    )
}

private enum class SettingsPicker { Language, Theme, ReaderTypography, Speed, Repeat }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChoiceSheet(
    picker: SettingsPicker,
    copy: NativeSettingsCopy,
    onDismiss: () -> Unit,
    selectedLanguage: AppLanguage,
    selectedTheme: ThemeMode,
    selectedSpeed: Float,
    selectedRepeat: Int,
    onLanguage: (AppLanguage) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeat: (Int) -> Unit,
) {
    val title = when (picker) {
        SettingsPicker.Language -> copy.language
        SettingsPicker.Theme -> copy.theme
        SettingsPicker.ReaderTypography -> error("Typography uses its own native sheet")
        SettingsPicker.Speed -> copy.speed
        SettingsPicker.Repeat -> copy.repeatAyah
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            when (picker) {
                SettingsPicker.Language -> AppLanguage.entries.forEach { value ->
                    SettingsChoiceRow(
                        label = languageLabel(value),
                        selected = value == selectedLanguage,
                    ) { onLanguage(value); onDismiss() }
                }
                SettingsPicker.Theme -> ThemeMode.entries.forEach { value ->
                    SettingsChoiceRow(
                        label = themeLabel(value, copy),
                        selected = value == selectedTheme,
                    ) { onTheme(value); onDismiss() }
                }
                SettingsPicker.ReaderTypography -> Unit
                SettingsPicker.Speed -> listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                    SettingsChoiceRow(
                        label = "${value}×",
                        selected = value == selectedSpeed,
                    ) { onSpeed(value); onDismiss() }
                }
                SettingsPicker.Repeat -> listOf(1, 3, 5, SurahReaderPreferences.RepeatForever).forEach { value ->
                    SettingsChoiceRow(
                        label = repeatLabel(value, copy),
                        selected = value == selectedRepeat,
                    ) { onRepeat(value); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
        ),
    )
}

private fun settingsColorScheme(
    mode: ThemeMode,
    palette: ThemePalette,
): ColorScheme {
    val colors = nativeReaderThemeColors(mode, palette)
    return if (mode == ThemeMode.DARK) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = Color(0xFF101513),
            primaryContainer = colors.primaryWeak,
            onPrimaryContainer = colors.onSurface,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.primaryWeak,
            onPrimaryContainer = colors.onSurface,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
        )
    }
}

private fun languageLabel(value: AppLanguage): String = when (value) {
    AppLanguage.RU -> "Русский"
    AppLanguage.TG -> "Тоҷикӣ"
    AppLanguage.UZ -> "O‘zbek"
    AppLanguage.TR -> "Türkçe"
    AppLanguage.EN -> "English"
}

private fun themeLabel(value: ThemeMode, copy: NativeSettingsCopy): String = when (value) {
    ThemeMode.LIGHT -> copy.lightTheme
    ThemeMode.DARK -> copy.darkTheme
}

private fun fontLabel(value: ReadingFontStyle): String = when (value) {
    ReadingFontStyle.MADINAH_MUSHAF -> "Madinah (Mushaf)"
    ReadingFontStyle.AMIRI_QURAN -> "Amiri Quran"
    ReadingFontStyle.SCHEHERAZADE_NEW -> "Scheherazade New"
    ReadingFontStyle.NOTO_NASKH_ARABIC -> "Noto Naskh Arabic"
    ReadingFontStyle.LATEEF -> "Lateef"
    ReadingFontStyle.NOTO_NASTALIQ_URDU -> "Noto Nastaliq Urdu"
    ReadingFontStyle.AREF_RUQAA_INK -> "Aref Ruqaa Ink"
}

private fun fontDescription(value: ReadingFontStyle, copy: NativeSettingsCopy): String = when (value) {
    ReadingFontStyle.MADINAH_MUSHAF -> copy.fontMadinahDescription
    ReadingFontStyle.AMIRI_QURAN -> copy.fontAmiriDescription
    ReadingFontStyle.SCHEHERAZADE_NEW -> copy.fontScheherazadeDescription
    ReadingFontStyle.NOTO_NASKH_ARABIC -> copy.fontNotoNaskhDescription
    ReadingFontStyle.LATEEF -> copy.fontLateefDescription
    ReadingFontStyle.NOTO_NASTALIQ_URDU -> copy.fontNastaliqDescription
    ReadingFontStyle.AREF_RUQAA_INK -> copy.fontRuqaaDescription
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTypographySheet(
    copy: NativeSettingsCopy,
    selectedFont: ReadingFontStyle,
    fontSize: Int,
    onFontSelected: (ReadingFontStyle) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 4.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(copy.quranText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        copy.previewDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                QuranTypographyPreview(selectedFont, fontSize, copy)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(copy.size, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("$fontSize pt", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { onFontSizeChanged(it.toInt().coerceIn(24, 34)) },
                        valueRange = 24f..34f,
                        steps = 9,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(copy.smaller, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(copy.larger, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(copy.font, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(ReadingFontStyle.entries.size, key = { ReadingFontStyle.entries[it].storageValue }) { index ->
                val style = ReadingFontStyle.entries[index]
                ReaderFontOption(
                    style = style,
                    copy = copy,
                    selected = style == selectedFont,
                    onClick = { onFontSelected(style) },
                )
            }
        }
    }
}

@Composable
private fun QuranTypographyPreview(fontStyle: ReadingFontStyle, fontSize: Int, copy: NativeSettingsCopy) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(copy.ayahPreview, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "ٱلْحَمْدُ لِلَّٰهِ رَبِّ ٱلْعَٰلَمِينَ 1",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = getQuranFontFamily(fontStyle),
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.65f).sp,
                textAlign = TextAlign.End,
            )
            Text(
                "Al-Fātiḥah · 1",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderFontOption(
    style: ReadingFontStyle,
    copy: NativeSettingsCopy,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(fontLabel(style), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(fontDescription(style, copy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "بِسْمِ ٱللَّٰهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = getQuranFontFamily(style),
                    fontSize = 23.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.End,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = copy.selected,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun repeatLabel(value: Int, copy: NativeSettingsCopy): String =
    if (value == SurahReaderPreferences.RepeatForever) copy.repeatForever else "$value×"
