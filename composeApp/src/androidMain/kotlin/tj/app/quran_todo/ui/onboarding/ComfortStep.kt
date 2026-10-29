package tj.app.quran_todo.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSlider
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.settings.FontCarousel
import tj.app.quran_todo.ui.settings.MaxReadingFontSize
import tj.app.quran_todo.ui.settings.MinReadingFontSize
import tj.app.quran_todo.ui.settings.PaletteSwatches
import tj.app.quran_todo.ui.settings.PreviewAyah
import tj.app.quran_todo.ui.settings.ReaderPreviewCard
import tj.app.quran_todo.ui.settings.ThemeSegments
import tj.app.quran_todo.ui.settings.bleed
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Step 10: how to read, on one screen instead of two. A live preview of an ayah on the reader's paper, the font carousel, the size,
 * the theme (light / dark / like the system) and the palette. Every control applies at once through the root's setters, so the
 * preview, and the whole app behind it, answers the finger.
 */
@Composable
internal fun ComfortStep(
    copy: OnboardingCopy,
    settingsCopy: SettingsCopy,
    ayahs: List<PreviewAyah>,
    fontStyle: ReadingFontStyle,
    fontSize: Int,
    onSelectFont: (ReadingFontStyle) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    themeIndex: Int,
    onSelectTheme: (Int) -> Unit,
    palette: ThemePalette,
    onSelectPalette: (ThemePalette) -> Unit,
) {
    val colors = QhTheme.colors
    StepColumn {
        StepHeader(copy.comfortTitle, copy.comfortSubtitle)
        ReaderPreviewCard(
            caption = "Ar-Raḥmān · 55:1–4",
            ayahs = ayahs,
            fontStyle = fontStyle,
            fontSize = fontSize,
        )
        QhSectionHeader(settingsCopy.native.font)
        FontCarousel(
            selected = fontStyle,
            onSelect = onSelectFont,
            descriptions = settingsCopy.native,
            modifier = Modifier.bleed(QhSpacing.screen),
            contentPadding = PaddingValues(horizontal = QhSpacing.screen),
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
        QhSectionHeader(settingsCopy.theme)
        ThemeSegments(copy = settingsCopy, selectedIndex = themeIndex, onSelect = onSelectTheme)
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
