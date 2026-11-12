package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.koin.compose.koinInject
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhSlider
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.GoToCopy
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.similar.SimilarIcon
import tj.app.quran_todo.ui.i18n.rememberGoToCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.rememberThemeSwitcher
import tj.app.quran_todo.ui.motion.themeRevealOriginTracker
import tj.app.quran_todo.ui.reader.BismillahText
import tj.app.quran_todo.ui.settings.ThemeSegments
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.tajweed.TajweedSettingsRows
import tj.app.quran_todo.ui.similar.SimilarMarksSwitchRow
import tj.app.quran_todo.ui.words.WordMeaningsSwitchRow
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.translation.TranslationEditionRow
import tj.app.quran_todo.ui.theme.QhTheme

private const val MinFontSize = 22
private const val MaxFontSize = 40

/**
 * "Aa": a live preview of the text, its size (22-40, the list behind the sheet changes as the thumb
 * moves), a carousel of Arabic fonts, the translation, word highlight, tajweed colours (with "What the colours mean"), light/dark and
 * keep-screen-on. Everything applies at once through the app root's setters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    previewArabic: String,
    prefs: SurahReaderPreferences,
    copy: ReaderCopy,
    onTranslation: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current
    val themeMode = LocalThemeMode.current
    val themeSwitcher = rememberThemeSwitcher()
    val uiPrefs = koinInject<UiPrefs>()
    val followsSystem by uiPrefs.themeFollowsSystem.collectAsState()
    val settingsCopy = rememberSettingsCopy()
    val goTo = rememberGoToCopy()
    val keepScreenOn by uiPrefs.keepScreenOn.collectAsState()
    val highlightWords by uiPrefs.highlightWords.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val size = settings.readingFontSize.coerceIn(MinFontSize, MaxFontSize)
    // The thumb follows the finger directly; the app-wide setting is written whenever the whole number changes.
    var sliderValue by remember { mutableFloatStateOf(size.toFloat()) }

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                // The sheet is its own window: the root never sees the finger that hits the theme control.
                .themeRevealOriginTracker()
                .padding(top = QhSpacing.x1)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x5),
        ) {
            SheetTitle(copy.textSettings)

            // Two parts: the text itself, and everything else. The choice of the part is not kept.
            var tab by remember { mutableIntStateOf(0) }
            QhSegmentedControl(
                options = listOf(goTo.tabText, goTo.tabAdvanced),
                selectedIndex = tab,
                onSelect = { tab = it },
            )

            if (tab == 0) {

            // Preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(QhTheme.shapes.md)
                    .background(colors.readerBg)
                    .border(1.dp, colors.hairline, QhTheme.shapes.md)
                    .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x4),
            ) {
                Text(
                    text = previewArabic.ifBlank { BismillahText },
                    style = QhTheme.type.quranBody(getQuranFontFamily(), size.sp),
                    color = colors.ink,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Size
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QhSectionHeader(copy.textSize, Modifier.weight(1f))
                    AnimatedCounter(value = size, style = QhTheme.type.title2, color = colors.primary)
                }
                QhSlider(
                    value = sliderValue,
                    onValueChange = { value ->
                        sliderValue = value
                        val rounded = value.roundToInt().coerceIn(MinFontSize, MaxFontSize)
                        if (rounded != size) setSettings(settings.copy(readingFontSize = rounded))
                    },
                    valueRange = MinFontSize.toFloat()..MaxFontSize.toFloat(),
                    steps = MaxFontSize - MinFontSize - 1,
                )
            }

            // Font
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                QhSectionHeader(copy.fontStyle)
                FontCarousel()
            }

            // Translation
            QhCard(contentPadding = PaddingValues(0.dp)) {
                QhSwitchRow(
                    title = copy.translation,
                    checked = prefs.showTranslation,
                    onCheckedChange = onTranslation,
                )
                QhRowDivider(startIndent = QhSpacing.card)
                // The edition of the translation (F4a): "Translation: Kuliev" opens the sheet of editions.
                TranslationEditionRow()
            }
            } else {
            QhCard(contentPadding = PaddingValues(0.dp)) {
                QhSwitchRow(
                    title = copy.highlightWords,
                    checked = highlightWords,
                    onCheckedChange = uiPrefs::setHighlightWords,
                )
                QhRowDivider(startIndent = QhSpacing.card)
                // Tajweed colours (F3b): the switch, the state of the markup file and "What the colours mean".
                TajweedSettingsRows()
                QhRowDivider(startIndent = QhSpacing.card)
                WordMeaningsSwitchRow() // F4b: "Word meanings (English)", off by default.
                QhRowDivider(startIndent = QhSpacing.card)
                SimilarMarksSwitchRow() // F6: "Similar verse marks", on by default.
                QhRowDivider(startIndent = QhSpacing.card)
                QhSwitchRow(
                    title = copy.keepScreenOn,
                    checked = keepScreenOn,
                    onCheckedChange = uiPrefs::setKeepScreenOn,
                )
            }


            // Theme
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                QhSectionHeader(copy.theme)
                // The same Light / Dark / System control and preference as the settings screen.
                ThemeSegments(
                    copy = settingsCopy,
                    selectedIndex = when {
                        followsSystem -> 2
                        themeMode == ThemeMode.DARK -> 1
                        else -> 0
                    },
                    onSelect = { index ->
                        when (index) {
                            0 -> themeSwitcher.switch(ThemeMode.LIGHT)
                            1 -> themeSwitcher.switch(ThemeMode.DARK)
                            else -> themeSwitcher.reveal { uiPrefs.setThemeFollowsSystem(true) }
                        }
                    },
                )
            }

            MarksLegend(goTo)
            }
        }
    }
}

/** Seven cards, each with "بِسْمِ ٱللَّهِ" in its own font; the chosen one is raised. Snaps to a card. */
@Composable
private fun FontCarousel() {
    val strings = LocalAppStrings.current
    val current = LocalReadingFontStyle.current
    val setStyle = LocalReadingFontStyleSetter.current
    val entries = ReadingFontStyle.entries
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = entries.indexOf(current).coerceAtLeast(0))
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        contentPadding = PaddingValues(vertical = QhSpacing.x2),
    ) {
        items(entries, key = { it.storageValue }) { style ->
            FontCard(
                style = style,
                label = style.label(strings),
                selected = style == current,
                onClick = { if (style != current) setStyle(style) },
            )
        }
    }
}

@Composable
private fun FontCard(style: ReadingFontStyle, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "font-card",
    )
    Column(
        Modifier
            .width(132.dp)
            .graphicsLayer {
                val scale = 1f + 0.05f * lift
                scaleX = scale
                scaleY = scale
            }
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.readerBg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.hairline, shape)
            .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "بِسْمِ ٱللَّهِ",
            style = QhTheme.type.quranInline(getQuranFontFamily(style), 22.sp),
            color = colors.ink,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(QhSpacing.x1))
        Text(
            text = label,
            style = QhTheme.type.label,
            color = if (selected) colors.onPrimaryContainer else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Ayah marks" (Ч8): what the small signs next to an ayah in the list mean. The signs are drawn the way the rows draw them: the
 * terracotta dot of a hard ayah, the two rings of similar ayahs and the gold note mark.
 */
@Composable
private fun MarksLegend(copy: GoToCopy) {
    val colors = QhTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        QhSectionHeader(copy.legendTitle)
        LegendRow(copy.legendHard) {
            Box(Modifier.size(10.dp).background(colors.due, CircleShape))
        }
        LegendRow(copy.legendSimilar) {
            Icon(SimilarIcon, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(22.dp))
        }
        LegendRow(copy.legendNote) {
            Icon(Icons.AutoMirrored.Rounded.StickyNote2, contentDescription = null, tint = colors.gold, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LegendRow(text: String, mark: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { mark() }
        Text(text, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary, modifier = Modifier.padding(start = QhSpacing.x2))
    }
}
