package tj.app.quran_todo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhSlider
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Range of the ayah font size, section 2.2 of the redesign spec. */
internal const val MinReadingFontSize = 22
internal const val MaxReadingFontSize = 40

/**
 * The typography sheet: a live preview of Al-Fatiha 1:2 on the reader paper, the size slider (22-40,
 * a step haptic per size) and the carousel of fonts. Every control writes straight through, so the
 * whole app already looks the new way behind the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypographySheet(
    copy: SettingsCopy,
    fontStyle: ReadingFontStyle,
    fontSize: Int,
    onFontSelect: (ReadingFontStyle) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    val colors = QhTheme.colors
    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            Text(text = copy.arabicFont, style = QhTheme.type.title1, color = colors.ink)
            ReaderPreviewCard(
                caption = "Al-Fātiḥah · 1:2",
                ayahs = listOf(FatihaSecondAyah),
                fontStyle = fontStyle,
                fontSize = fontSize,
            )
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = copy.native.textSize,
                        style = QhTheme.type.title2,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedCounter(fontSize, style = QhTheme.type.title2, color = colors.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "A",
                        style = QhTheme.type.callout,
                        color = colors.inkSecondary,
                    )
                    QhSlider(
                        value = fontSize.toFloat(),
                        onValueChange = { onFontSizeChange(it.toInt().coerceIn(MinReadingFontSize, MaxReadingFontSize)) },
                        valueRange = MinReadingFontSize.toFloat()..MaxReadingFontSize.toFloat(),
                        steps = MaxReadingFontSize - MinReadingFontSize - 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = QhSpacing.x2),
                    )
                    Text(
                        text = "A",
                        style = QhTheme.type.title1,
                        color = colors.inkSecondary,
                    )
                }
            }
            Text(text = copy.native.font, style = QhTheme.type.title2, color = colors.ink)
            FontCarousel(
                selected = fontStyle,
                onSelect = onFontSelect,
                descriptions = copy.native,
                modifier = Modifier.bleed(QhSpacing.screen),
                contentPadding = PaddingValues(horizontal = QhSpacing.screen),
            )
        }
    }
}

/** The five languages as cards in a sheet; the choice applies at once (the UI crossfades behind). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSheet(
    copy: SettingsCopy,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            Text(text = copy.languageSheetTitle, style = QhTheme.type.title1, color = QhTheme.colors.ink)
            Spacer(Modifier.height(QhSpacing.x2))
            LanguageOrder.forEach { language ->
                LanguageCard(
                    language = language,
                    selected = language == selected,
                    onClick = { onSelect(language) },
                )
            }
        }
    }
}

/** Confirms the destructive "restore from backup": the danger action on top, a quiet cancel under it. */
@Composable
internal fun RestoreDialog(
    copy: SettingsCopy,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.xl
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, shape)
                .padding(QhSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            QhIconTile(Icons.Rounded.Restore, tint = colors.danger)
            Text(
                text = copy.native.restoreTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = copy.native.restoreMessage,
                style = QhTheme.type.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QhSpacing.x1))
            QhButton(
                text = copy.native.restore,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Danger,
                size = QhButtonSize.M,
            )
            QhButton(
                text = copy.native.cancel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }
}

/** Lets a child run [horizontal] past the parent's padding on both sides (an edge-to-edge carousel in a padded sheet). */
internal fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-horizontal.roundToPx(), 0)
    }
}
