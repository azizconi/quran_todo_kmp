package tj.app.quran_todo.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.sync.SettingsSnapshot
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.OrnamentDivider
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.rememberHaptics
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.i18n.rememberTrustCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.StaggerState
import tj.app.quran_todo.ui.motion.rememberSharedSettled
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.rememberThemeSwitcher
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private enum class SettingsSheet { Typography, Language }

private val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val RepeatOptions = listOf(1, 3, 5, SurahReaderPreferences.RepeatForever)

private const val MinDailyGoal = 1
private const val MaxDailyGoal = 50

/**
 * The settings screen (docs/redesign/ANDROID_REDESIGN.md, 6.6): a large title over grouped cards of
 * rows with colored icon tiles. Language, theme, palette, font and `AppSettings` go through the
 * root's setters, reader and Android-only preferences through [SettingsViewModel], so the whole UI
 * follows each change at once. A language switch crossfades the content.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit, onOpenSources: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val language = LocalAppLanguage.current
    val setLanguage = LocalAppLanguageSetter.current
    val fontStyle = LocalReadingFontStyle.current
    val setFontStyle = LocalReadingFontStyleSetter.current
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current
    val toaster = LocalToaster.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val copy = rememberSettingsCopy(language)
    val latestCopy by rememberUpdatedState(copy)
    val latestSettings by rememberUpdatedState(settings)

    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var confirmsRestore by remember { mutableStateOf(false) }

    // Settings that a restored backup carried come back into the root state, then are consumed.
    val restored by viewModel.restoredSettings.collectAsState()
    LaunchedEffect(restored) {
        val snapshot = restored ?: return@LaunchedEffect
        setSettings(latestSettings.mergedWith(snapshot))
        viewModel.consumeRestoredSettings()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = latestCopy
            when (event) {
                SettingsEvent.OfflineStarted -> toaster.show(text.native.downloadStarted, ToastKind.Info)
                SettingsEvent.QuranNotReady -> toaster.show(text.offlineQuranNotReady, ToastKind.Error)
                SettingsEvent.BackupDone -> toaster.show(text.backupDone, ToastKind.Success)
                SettingsEvent.RestoreDone -> toaster.show(text.restoreDone, ToastKind.Success)
                SettingsEvent.NoBackup -> toaster.show(text.noBackup, ToastKind.Info)
                SettingsEvent.BackupFailed -> toaster.show(text.backupFailed, ToastKind.Error)
                SettingsEvent.RestoreFailed -> toaster.show(text.restoreFailed, ToastKind.Error)
                is SettingsEvent.Raw -> toaster.show(event.message, ToastKind.Info)
            }
        }
    }

    // The bar floats over the list: reserve its expanded height (the estimate is exact for one-line
    // titles, so the list does not jump when the first measurement arrives).
    val scrollState = rememberScrollState()
    val topBarState = rememberQhTopBarState(scrollState)
    val stagger = rememberStaggerState()
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) {
        (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx()
    }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val listTopPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2

    // The bar title swaps halfway through the content crossfade.
    var barTitle by remember { mutableStateOf(copy.native.settingsTitle) }
    LaunchedEffect(copy.native.settingsTitle) {
        if (barTitle != copy.native.settingsTitle) {
            if (!reduced) delay(CrossfadeMillis / 2L)
            barTitle = copy.native.settingsTitle
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        Crossfade(
            targetState = language,
            animationSpec = if (reduced) snap() else tween(CrossfadeMillis),
            label = "settings-language",
        ) { shownLanguage ->
            SettingsContent(
                language = shownLanguage,
                viewModel = viewModel,
                scrollState = scrollState,
                topPadding = listTopPadding,
                stagger = stagger,
                onOpenTypography = { sheet = SettingsSheet.Typography },
                onOpenLanguage = { sheet = SettingsSheet.Language },
                onConfirmRestore = { confirmsRestore = true },
                onOpenPrivacy = onOpenPrivacy,
                onOpenSources = onOpenSources,
            )
        }
        QhLargeTopBar(
            title = barTitle,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size ->
                // Only an unscrolled bar is fully expanded.
                if (scrollState.value == 0) expandedBarPx = size.height
            },
            navigation = {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = copy.native.back,
                    onClick = onBack,
                    filled = false,
                )
            },
            actions = { HeaderGear() },
        )
    }

    when (sheet) {
        SettingsSheet.Typography -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            TypographySheet(
                copy = copy,
                fontStyle = fontStyle,
                fontSize = settings.readingFontSize.coerceIn(MinReadingFontSize, MaxReadingFontSize),
                onFontSelect = setFontStyle,
                onFontSizeChange = { size -> setSettings(latestSettings.copy(readingFontSize = size)) },
                onDismiss = { sheet = null },
                sheetState = sheetState,
            )
        }

        SettingsSheet.Language -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            LanguageSheet(
                copy = copy,
                selected = language,
                onSelect = { next ->
                    setLanguage(next)
                    // Let the check land before the sheet leaves.
                    scope.launch {
                        delay(if (reduced) 0L else 260L)
                        sheetState.hide()
                    }.invokeOnCompletion { sheet = null }
                },
                onDismiss = { sheet = null },
                sheetState = sheetState,
            )
        }

        null -> Unit
    }

    if (confirmsRestore) {
        RestoreDialog(
            copy = copy,
            onConfirm = {
                confirmsRestore = false
                viewModel.restore()
            },
            onDismiss = { confirmsRestore = false },
        )
    }
}

private const val CrossfadeMillis = 240

@Composable
private fun SettingsContent(
    language: AppLanguage,
    viewModel: SettingsViewModel,
    scrollState: ScrollState,
    topPadding: Dp,
    stagger: StaggerState,
    onOpenTypography: () -> Unit,
    onOpenLanguage: () -> Unit,
    onConfirmRestore: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSources: () -> Unit,
) {
    val copy = rememberSettingsCopy(language)
    val trust = rememberTrustCopy(language)
    val colors = QhTheme.colors
    val context = LocalContext.current
    val toaster = LocalToaster.current

    val themeMode = LocalThemeMode.current
    val palette = LocalThemePalette.current
    // Theme and palette changes ripple out from the finger (circular reveal).
    val themeSwitcher = rememberThemeSwitcher()
    val fontStyle = LocalReadingFontStyle.current
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current

    val reader by viewModel.readerPreferences.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val hintMode by viewModel.hifzHintMode.collectAsState()
    val followsSystem by viewModel.themeFollowsSystem.collectAsState()
    val offline by viewModel.offline.collectAsState()
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsState()

    val hapticsDemo = rememberHaptics(enabled = true)
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val versionLabel = remember { appVersionLabel(context) }
    val themeIndex = when {
        followsSystem -> 2
        themeMode == ThemeMode.DARK -> 1
        else -> 0
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = navigationBottom + QhSpacing.x8)
            .padding(horizontal = QhSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
    ) {
        // 1. Appearance
        SettingsSection(copy.sectionAppearance, Modifier.staggeredEnter(0, stagger)) {
            SettingsBlock(title = copy.theme, icon = Icons.Rounded.Contrast, tint = colors.gold) {
                ThemeSegments(
                    copy = copy,
                    selectedIndex = themeIndex,
                    onSelect = { index ->
                        when (index) {
                            0 -> themeSwitcher.switch(ThemeMode.LIGHT)
                            1 -> themeSwitcher.switch(ThemeMode.DARK)
                            else -> themeSwitcher.reveal { viewModel.setThemeFollowsSystem(true) }
                        }
                    },
                )
            }
            QhRowDivider()
            SettingsBlock(title = copy.palette, icon = Icons.Rounded.Palette, tint = colors.primary) {
                PaletteSwatches(
                    selected = palette,
                    onSelect = themeSwitcher::switchPalette,
                    labelOf = { copy.paletteLabel(it) },
                )
            }
            QhRowDivider()
            QhValueRow(
                title = copy.arabicFont,
                value = "${fontStyle.shortName()} · ${settings.readingFontSize}",
                onClick = onOpenTypography,
                icon = Icons.Rounded.TextFields,
                iconTint = colors.due,
            )
        }

        // 2. Reading
        SettingsSection(copy.sectionReading, Modifier.staggeredEnter(1, stagger)) {
            QhSwitchRow(
                title = copy.translation,
                description = copy.translationDescription,
                checked = reader.showTranslation,
                onCheckedChange = { on -> viewModel.updateReaderPreferences { it.copy(showTranslation = on) } },
                icon = Icons.Rounded.Translate,
                iconTint = colors.primary,
            )
            QhRowDivider()
            QhSwitchRow(
                title = copy.highlightAyah,
                description = copy.highlightAyahDescription,
                checked = reader.highlightPlayingAyah,
                onCheckedChange = { on -> viewModel.updateReaderPreferences { it.copy(highlightPlayingAyah = on) } },
                icon = Icons.Rounded.Highlight,
                iconTint = colors.gold,
            )
            QhRowDivider()
            QhSwitchRow(
                title = copy.keepScreenOn,
                description = copy.keepScreenOnDescription,
                checked = keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
                icon = Icons.Rounded.Visibility,
                iconTint = colors.learned,
            )
        }

        // 3. Sound
        SettingsSection(copy.sectionSound, Modifier.staggeredEnter(2, stagger)) {
            QhSwitchRow(
                title = copy.autoplay,
                description = copy.autoplayDescription,
                checked = reader.autoplayOnOpen,
                onCheckedChange = { on -> viewModel.updateReaderPreferences { it.copy(autoplayOnOpen = on) } },
                icon = Icons.Rounded.PlayCircle,
                iconTint = colors.primary,
            )
            QhRowDivider()
            QhSwitchRow(
                title = copy.autoAdvance,
                description = copy.autoAdvanceDescription,
                checked = reader.autoAdvanceAyahs,
                onCheckedChange = { on -> viewModel.updateReaderPreferences { it.copy(autoAdvanceAyahs = on) } },
                icon = Icons.Rounded.SkipNext,
                iconTint = colors.gold,
            )
            QhRowDivider()
            ChipChoiceBlock(
                title = copy.speed,
                icon = Icons.Rounded.Speed,
                tint = colors.due,
                options = SpeedOptions,
                selected = reader.playbackSpeed,
                labelOf = ::speedLabel,
                onSelect = { speed -> viewModel.updateReaderPreferences { it.copy(playbackSpeed = speed) } },
            )
            QhRowDivider()
            ChipChoiceBlock(
                title = copy.repeatAyah,
                icon = Icons.Rounded.Repeat,
                tint = colors.learned,
                options = RepeatOptions,
                selected = reader.repeatCount,
                labelOf = ::repeatLabel,
                onSelect = { count -> viewModel.updateReaderPreferences { it.copy(repeatCount = count) } },
            )
            QhRowDivider()
            SoundVoiceRows(language = language, reciterTitle = copy.reciter)
        }

        // 4. Memorization
        SettingsSection(copy.sectionMemorization, Modifier.staggeredEnter(3, stagger)) {
            StepperRow(
                title = copy.dailyGoal,
                description = copy.dailyGoalDescription,
                icon = Icons.Rounded.Flag,
                tint = colors.gold,
                value = settings.dailyGoal,
                onDecrease = {
                    setSettings(settings.copy(dailyGoal = (settings.dailyGoal - 1).coerceAtLeast(MinDailyGoal)))
                },
                onIncrease = {
                    setSettings(settings.copy(dailyGoal = (settings.dailyGoal + 1).coerceAtMost(MaxDailyGoal)))
                },
                canDecrease = settings.dailyGoal > MinDailyGoal,
                canIncrease = settings.dailyGoal < MaxDailyGoal,
                decreaseLabel = copy.decrease,
                increaseLabel = copy.increase,
            )
            QhRowDivider()
            QhSwitchRow(
                title = copy.hintFirstWord,
                description = copy.hintFirstWordDescription,
                checked = hintMode == HifzHintMode.FIRST_WORD,
                onCheckedChange = { on ->
                    viewModel.setHifzHintMode(if (on) HifzHintMode.FIRST_WORD else HifzHintMode.NONE)
                },
                icon = Icons.Rounded.Lightbulb,
                iconTint = colors.primary,
            )
        }

        // 5. Language
        SettingsSection(copy.sectionLanguage, Modifier.staggeredEnter(4, stagger)) {
            QhValueRow(
                title = copy.language,
                value = language.nativeName(),
                onClick = onOpenLanguage,
                icon = Icons.Rounded.Language,
                iconTint = colors.primary,
            )
        }

        // 6. Data
        SettingsSection(
            title = copy.sectionData,
            modifier = Modifier.staggeredEnter(5, stagger),
            footnote = copy.dataFootnote,
        ) {
            OfflineDownloadRow(
                state = offline.toUi(),
                copy = copy,
                onClick = { viewModel.startOfflineDownload(language) },
            )
            QhRowDivider()
            QhNavRow(
                title = copy.backupTitle,
                description = copy.native.syncDescription,
                onClick = { viewModel.backup(settings) },
                icon = Icons.Rounded.CloudUpload,
                iconTint = colors.gold,
            )
            QhRowDivider()
            QhNavRow(
                title = copy.native.restoreData,
                description = copy.native.restoreDescription,
                onClick = onConfirmRestore,
                icon = Icons.Rounded.Restore,
                iconTint = colors.danger,
            )
        }

        // 7. Comfort
        SettingsSection(copy.sectionComfort, Modifier.staggeredEnter(6, stagger)) {
            QhSwitchRow(
                title = copy.haptics,
                description = copy.hapticsDescription,
                checked = hapticsEnabled,
                onCheckedChange = { on ->
                    viewModel.setHapticsEnabled(on)
                    // The theme's own haptics are still off at this moment: let the person feel the new state.
                    if (on) hapticsDemo.perform(HapticKind.Success)
                },
                icon = Icons.Rounded.Vibration,
                iconTint = colors.primary,
            )
            QhRowDivider()
            QhSwitchRow(
                title = copy.reduceMotion,
                description = copy.reduceMotionDescription,
                checked = reduceMotion,
                onCheckedChange = viewModel::setReduceMotion,
                icon = Icons.Rounded.Animation,
                iconTint = colors.gold,
            )
        }

        // 8. Privacy
        SettingsSection(trust.sectionPrivacy, Modifier.staggeredEnter(7, stagger)) {
            QhSwitchRow(
                title = trust.analyticsTitle,
                description = trust.analyticsDescription,
                checked = analyticsEnabled,
                onCheckedChange = viewModel::setAnalyticsEnabled,
                icon = Icons.Rounded.Insights,
                iconTint = colors.learned,
            )
            QhRowDivider()
            QhNavRow(
                title = copy.privacyPolicy,
                description = trust.privacyRowDescription,
                onClick = onOpenPrivacy,
                icon = Icons.Rounded.PrivacyTip,
                iconTint = colors.gold,
            )
            QhRowDivider()
            QhNavRow(
                title = trust.sourcesRowTitle,
                description = trust.sourcesRowDescription,
                onClick = onOpenSources,
                icon = Icons.Rounded.Source,
                iconTint = colors.primary,
            )
        }

        // 9. About
        SettingsSection(copy.sectionAbout, Modifier.staggeredEnter(8, stagger)) {
            if (versionLabel.isNotEmpty()) {
                QhValueRow(
                    title = copy.version,
                    value = versionLabel,
                    icon = Icons.Rounded.Info,
                    iconTint = colors.primary,
                )
                QhRowDivider()
            }
            QhNavRow(
                title = copy.rateApp,
                description = copy.rateAppDescription,
                onClick = {
                    if (!openStoreListing(context)) toaster.show(copy.storeUnavailable, ToastKind.Error)
                },
                icon = Icons.Rounded.ThumbUp,
                iconTint = colors.gold,
            )
        }

        SettingsFooter(versionLabel)
    }
}

/**
 * The settings icon in the header: the landing point of the home screen's shared gear. It is a pure
 * decoration, so it is drawn as a soft icon tile like the ones in the rows (not as a round button),
 * has no press behavior and is hidden from accessibility services.
 *
 * Only the gear flies (`SharedKeys.SettingsIcon`). The tile behind it is not shared: it fades in once the
 * gear has landed and fades out as soon as the way back starts, so no shape jumps in flight. The gear
 * leaves in ink and turns emerald together with the tile.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeaderGear() {
    val colors = QhTheme.colors
    val settled = rememberSharedSettled()
    val tile by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(QhMotion.Medium, delayMillis = if (settled) 40 else 0, easing = QhMotion.Emphasized),
        label = "settings-gear-tile",
    )
    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer { alpha = tile }
                .clip(QhTheme.shapes.sm)
                .background(colors.primary.copy(alpha = 0.14f)),
        )
        Icon(
            Icons.Rounded.Settings,
            contentDescription = null,
            tint = lerp(colors.ink, colors.primary, tile),
            modifier = Modifier
                .size(18.dp)
                .sharedElementKey(SharedKeys.SettingsIcon),
        )
    }
}

@Composable
private fun SettingsFooter(versionLabel: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        OrnamentDivider(Modifier.padding(horizontal = QhSpacing.x10))
        Text(
            text = if (versionLabel.isEmpty()) "Quran Hifz" else "Quran Hifz · $versionLabel",
            style = QhTheme.type.callout,
            color = QhTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun SettingsCopy.paletteLabel(palette: ThemePalette): String = when (palette) {
    ThemePalette.FOREST -> paletteForest
    ThemePalette.SAND -> paletteSand
    ThemePalette.OCEAN -> paletteOcean
}

internal fun speedLabel(speed: Float): String {
    val whole = speed.toInt()
    return (if (speed == whole.toFloat()) whole.toString() else speed.toString()) + "×"
}

internal fun repeatLabel(count: Int): String =
    if (count == SurahReaderPreferences.RepeatForever) "∞" else "$count×"

/** The app settings after a restore: everything the backup carries replaces the current value. */
private fun tj.app.quran_todo.common.settings.AppSettings.mergedWith(restored: SettingsSnapshot) = copy(
    dailyGoal = restored.dailyGoal.coerceIn(MinDailyGoal, MaxDailyGoal),
    focusMinutes = restored.focusMinutes,
    remindersEnabled = restored.remindersEnabled,
    targetAyahs = restored.targetAyahs,
    targetEpochDay = restored.targetEpochDay,
    examModeEnabled = restored.examModeEnabled,
    readingFontSize = restored.readingFontSize.coerceIn(MinReadingFontSize, MaxReadingFontSize),
)
