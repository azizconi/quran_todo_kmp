package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSlider
import tj.app.quran_todo.ui.components.StarLattice
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.settings.FontCarousel
import tj.app.quran_todo.ui.settings.LanguageCard
import tj.app.quran_todo.ui.settings.LanguageOrder
import tj.app.quran_todo.ui.settings.MaxReadingFontSize
import tj.app.quran_todo.ui.settings.MinReadingFontSize
import tj.app.quran_todo.ui.settings.PaletteSwatches
import tj.app.quran_todo.ui.settings.PreviewAyah
import tj.app.quran_todo.ui.settings.ReaderPreviewCard
import tj.app.quran_todo.ui.settings.ValueStepper
import tj.app.quran_todo.ui.settings.bleed
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Bits of the goals the person ticks in step 4; they map to `OnboardingGoal` strings on finish. */
internal const val GoalRead = 1
internal const val GoalListen = 2
internal const val GoalMemorize = 4

/** The daily goals offered next to "Memorize" (the settings screen allows 1-50). */
internal val DailyGoalPresets = listOf(1, 3, 5, 10)

// -- Shared pieces ---------------------------------------------------------------------------------

/** A screen-sized, scrolling column with the 20dp screen inset; every step is one of these. */
@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(bottom = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
    ) { content() }
}

/** Text that crossfades when [text] changes (a language switch re-renders the screen's copy). */
@Composable
internal fun CrossfadeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val reduced = LocalReducedMotion.current
    Crossfade(
        targetState = text,
        modifier = modifier.animateContentSize(QhMotion.gentle<androidx.compose.ui.unit.IntSize>().orSnap()),
        animationSpec = if (reduced) snap() else tween(QhMotion.Medium),
        label = "onboarding-copy",
    ) { shown ->
        Text(
            text = shown,
            style = style,
            color = color,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        CrossfadeText(
            text = title,
            style = QhTheme.type.headline,
            color = QhTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        CrossfadeText(text = subtitle, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
    }
}

// -- Step 1: welcome and language ------------------------------------------------------------------

@Composable
internal fun WelcomeStep(
    copy: OnboardingCopy,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    val colors = QhTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(bottom = QhSpacing.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The rosette sits on a faint star lattice that fades out toward every edge.
        Box(
            Modifier
                .fillMaxWidth()
                .bleed(QhSpacing.screen),
            contentAlignment = Alignment.Center,
        ) {
            StarLattice(Modifier.matchParentSize(), alpha = 0.09f)
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.bg,
                            0.35f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to colors.bg,
                        ),
                    ),
            )
            Column(
                Modifier.padding(vertical = QhSpacing.x6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                WelcomeRosette()
                Spacer(Modifier.height(QhSpacing.x2))
                Text(
                    text = "Quran Hifz",
                    style = QhTheme.type.display,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                CrossfadeText(
                    text = copy.welcomeSubtitle,
                    style = QhTheme.type.body,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x4))
        Crossfade(
            targetState = copy.chooseLanguage,
            modifier = Modifier.fillMaxWidth(),
            animationSpec = if (LocalReducedMotion.current) snap() else tween(QhMotion.Medium),
            label = "choose-language",
        ) { title -> QhSectionHeader(title) }
        Spacer(Modifier.height(QhSpacing.x3))
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            LanguageOrder.forEach { option ->
                LanguageCard(
                    language = option,
                    selected = option == language,
                    onClick = { onSelectLanguage(option) },
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x3))
        CrossfadeText(
            text = copy.chooseLanguageHint,
            style = QhTheme.type.callout,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

// -- Step 2: reading -------------------------------------------------------------------------------

@Composable
internal fun ReadingStep(
    copy: OnboardingCopy,
    settingsCopy: SettingsCopy,
    ayahs: List<PreviewAyah>,
    fontStyle: ReadingFontStyle,
    fontSize: Int,
    onSelectFont: (ReadingFontStyle) -> Unit,
    onFontSizeChange: (Int) -> Unit,
) {
    val colors = QhTheme.colors
    StepColumn {
        StepHeader(copy.readingTitle, copy.readingSubtitle)
        ReaderPreviewCard(
            caption = "Ar-Raḥmān · 55:1–4",
            ayahs = ayahs,
            fontStyle = fontStyle,
            fontSize = fontSize,
        )
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = settingsCopy.native.textSize,
                    style = QhTheme.type.title2,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                AnimatedCounter(fontSize, style = QhTheme.type.title2, color = colors.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "A", style = QhTheme.type.callout, color = colors.inkSecondary)
                QhSlider(
                    value = fontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.toInt().coerceIn(MinReadingFontSize, MaxReadingFontSize)) },
                    valueRange = MinReadingFontSize.toFloat()..MaxReadingFontSize.toFloat(),
                    steps = MaxReadingFontSize - MinReadingFontSize - 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = QhSpacing.x2),
                )
                Text(text = "A", style = QhTheme.type.title1, color = colors.inkSecondary)
            }
        }
        QhSectionHeader(settingsCopy.native.font)
        FontCarousel(
            selected = fontStyle,
            onSelect = onSelectFont,
            descriptions = settingsCopy.native,
            modifier = Modifier.bleed(QhSpacing.screen),
            contentPadding = PaddingValues(horizontal = QhSpacing.screen),
        )
    }
}

// -- Step 3: look ----------------------------------------------------------------------------------

@Composable
internal fun LookStep(
    copy: OnboardingCopy,
    settingsCopy: SettingsCopy,
    fontStyle: ReadingFontStyle,
    themeIndex: Int,
    onSelectTheme: (Int) -> Unit,
    palette: ThemePalette,
    onSelectPalette: (ThemePalette) -> Unit,
) {
    StepColumn {
        StepHeader(copy.lookTitle, copy.lookSubtitle)
        HeroPreviewCard(copy, fontStyle)
        QhSegmentedControl(
            options = listOf(settingsCopy.themeLight, settingsCopy.themeDark, settingsCopy.themeSystem),
            selectedIndex = themeIndex,
            onSelect = onSelectTheme,
            icons = listOf(Icons.Rounded.LightMode, Icons.Rounded.DarkMode, Icons.Rounded.SettingsBrightness),
        )
        QhSectionHeader(settingsCopy.palette)
        PaletteSwatches(
            selected = palette,
            onSelect = onSelectPalette,
            labelOf = { option ->
                when (option) {
                    ThemePalette.FOREST -> settingsCopy.paletteForest
                    ThemePalette.SAND -> settingsCopy.paletteSand
                    ThemePalette.OCEAN -> settingsCopy.paletteOcean
                }
            },
        )
    }
}

/**
 * A miniature of the home screen's hero card in the live theme: it changes the moment the theme or
 * the palette does. Purely decorative, so it is hidden from screen readers.
 */
@Composable
private fun HeroPreviewCard(copy: OnboardingCopy, fontStyle: ReadingFontStyle) {
    val colors = QhTheme.colors
    val family = getQuranFontFamily(fontStyle)
    QhCard(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        style = QhCardStyle.Hero,
        contentPadding = PaddingValues(QhSpacing.cardLarge),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(copy.heroOverline.uppercase(), style = QhTheme.type.overline, color = colors.inkSecondary)
                Spacer(Modifier.height(QhSpacing.x1))
                Text(copy.heroSurah, style = QhTheme.type.title1, color = colors.ink)
            }
            Text(
                text = "البقرة",
                style = QhTheme.type.quranInline(family),
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(QhSpacing.x1))
        Text(copy.heroProgress, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        QhLinearProgress(HeroPreviewProgress)
        Spacer(Modifier.height(QhSpacing.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            MockButton(copy.heroRead, filled = true, modifier = Modifier.weight(1f))
            MockButton(copy.heroListen, filled = false, modifier = Modifier.weight(1f))
        }
    }
}

private const val HeroPreviewProgress = 255f / 286f

@Composable
private fun MockButton(label: String, filled: Boolean, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(QhTheme.shapes.full)
            .background(if (filled) colors.primary else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = QhTheme.type.label,
            color = if (filled) colors.onPrimary else colors.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

// -- Step 4: goals ---------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalsStep(
    copy: OnboardingCopy,
    settingsCopy: SettingsCopy,
    goals: Int,
    onToggleGoal: (Int) -> Unit,
    dailyGoal: Int,
    onDailyGoalChange: (Int) -> Unit,
) {
    val colors = QhTheme.colors
    StepColumn {
        StepHeader(copy.goalsTitle, copy.goalsSubtitle)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            GoalChip(copy.goalRead, Icons.AutoMirrored.Rounded.MenuBook, goals and GoalRead != 0) { onToggleGoal(GoalRead) }
            GoalChip(copy.goalListen, Icons.Rounded.Headphones, goals and GoalListen != 0) { onToggleGoal(GoalListen) }
            GoalChip(copy.goalMemorize, Icons.Rounded.Psychology, goals and GoalMemorize != 0) { onToggleGoal(GoalMemorize) }
        }
        val reduced = LocalReducedMotion.current
        AnimatedVisibility(
            visible = goals and GoalMemorize != 0,
            enter = if (reduced) fadeIn(snap()) else expandVertically(QhMotion.gentle()) + fadeIn(QhMotion.gentle()),
            exit = if (reduced) fadeOut(snap()) else shrinkVertically(QhMotion.gentle()) + fadeOut(QhMotion.gentle()),
        ) {
            QhCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = copy.dailyGoalTitle,
                        style = QhTheme.type.title2,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    ValueStepper(
                        value = dailyGoal,
                        onDecrease = { onDailyGoalChange(DailyGoalPresets.previousOf(dailyGoal)) },
                        onIncrease = { onDailyGoalChange(DailyGoalPresets.nextOf(dailyGoal)) },
                        canDecrease = dailyGoal > DailyGoalPresets.first(),
                        canIncrease = dailyGoal < DailyGoalPresets.last(),
                        decreaseLabel = settingsCopy.decrease,
                        increaseLabel = settingsCopy.increase,
                    )
                }
                Spacer(Modifier.height(QhSpacing.x1))
                Text(copy.dailyGoalHint, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
    }
}

/** The preset after [current] (or [current] when it is the last), for the daily goal stepper. */
internal fun List<Int>.nextOf(current: Int): Int = firstOrNull { it > current } ?: last()

/** The preset before [current] (or [current] when it is the first). */
internal fun List<Int>.previousOf(current: Int): Int = lastOrNull { it < current } ?: first()

/** The preset closest to [value]; ties go to the smaller one. */
internal fun List<Int>.nearestTo(value: Int): Int =
    minByOrNull { kotlin.math.abs(it - value) } ?: value

/** A large multi-select chip for the goals: icon, label and a check that slides in when selected. */
@Composable
private fun GoalChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val reduced = LocalReducedMotion.current
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "goal-container",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "goal-outline",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.ink,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "goal-content",
    )
    Row(
        Modifier
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, highlight = false, role = Role.Checkbox)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(container)
            .border(1.5.dp, outline, shape)
            .animateContentSize(QhMotion.gentle<androidx.compose.ui.unit.IntSize>().orSnap())
            .heightIn(min = 56.dp)
            .padding(horizontal = QhSpacing.x5),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(label, style = QhTheme.type.title2, color = content, maxLines = 1)
        AnimatedVisibility(
            visible = selected,
            enter = if (reduced) fadeIn(snap()) else expandHorizontally(QhMotion.snappy()) + fadeIn(QhMotion.snappy()),
            exit = if (reduced) fadeOut(snap()) else shrinkHorizontally(QhMotion.snappy()) + fadeOut(QhMotion.snappy()),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

/** Index of the theme segment: 0 light, 1 dark, 2 follow the system. */
internal fun themeSegment(followsSystem: Boolean, mode: ThemeMode): Int = when {
    followsSystem -> 2
    mode == ThemeMode.DARK -> 1
    else -> 0
}
