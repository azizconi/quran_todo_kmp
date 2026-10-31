package tj.app.quran_todo.ui.similar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.SimilarCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.i18n.rememberSimilarCopy
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.reader.components.SheetTitle
import tj.app.quran_todo.ui.reader.components.rememberSheetCloser
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Origin of a reader opened from the sheet of similar ayahs: it matches no shared element, so the reader simply slides in. */
internal const val SimilarOrigin = "similar"

/**
 * The one host of the sheet "Similar verses" (task F6), at the root of the app: the ayah sheet, the mark of an ayah and the line of the
 * lesson only ask the [SimilarStore] to open it, and it appears over whatever screen is there. A tap on a similar ayah opens the reader on
 * it ([onOpenReader]); "Tell apart" opens the drill of the group ([onTellApart]).
 */
@Composable
fun SimilarSheetHost(
    onOpenReader: (Dest.Reader) -> Unit,
    onTellApart: (AyahKey) -> Unit,
) {
    val store = koinInject<SimilarStore>()
    val language = LocalAppLanguage.current
    LaunchedEffect(language) { store.loadNames(language) }
    val key by store.sheetAyah.collectAsState()
    val ui by store.sheet.collectAsState()
    val open = ui
    val ayah = key
    if (ayah != null && open != null) {
        SimilarSheet(
            ui = open,
            onOpen = { target ->
                store.closeSheet()
                onOpenReader(Dest.Reader(surah = target.surah, ayah = target.ayah, origin = SimilarOrigin))
            },
            onTellApart = {
                store.closeSheet()
                onTellApart(ayah)
            },
            onDismiss = store::closeSheet,
        )
    }
}

/** "Al-Baqara 35" for a place, by the one rule of the names of surahs. */
@Composable
internal fun rememberPlaceLabel(): (AyahKey) -> String {
    val store = koinInject<SimilarStore>()
    val language = LocalAppLanguage.current
    val names by store.chapterNames.collectAsState()
    return remember(language, names) { { key -> passageRangeLabel(store.surahTitle(key.surah, language, names), key.ayah, key.ayah) } }
}

/**
 * The sheet: the ayah on top, then every similar ayah with the words that differ from it in the accent colour, which of them are
 * learned, the places of a refrain, and "Tell apart" when the ayah and one of its likes are memorized. No tajweed here: the colour
 * says only "this word is not in the other".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SimilarSheet(
    ui: SimilarSheetUi,
    onOpen: (AyahKey) -> Unit,
    onTellApart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = rememberSimilarCopy()
    val colors = QhTheme.colors
    val type = QhTheme.type
    val store = koinInject<SimilarStore>()
    val language = LocalAppLanguage.current
    val names by store.chapterNames.collectAsState()
    val label = rememberPlaceLabel()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(sheetState, onDismiss)
    val family = getQuranFontFamily()
    val arabicStyle = remember(type, family) { type.quranBody(family, 24.sp) }
    val accent = colors.primary

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                QhIconTile(SimilarIcon)
                SheetTitle(copy.title)
            }
            Text(text = copy.legend, style = type.callout, color = colors.inkSecondary)

            PlaceCard(
                place = ui.current,
                title = label(ui.current.key),
                tag = copy.thisAyah,
                copy = copy,
                arabicStyle = arabicStyle,
                accent = accent,
                onClick = null,
            )

            ui.repeats?.let { repeats ->
                Spacer(Modifier.height(QhSpacing.x1))
                QhSectionHeader(
                    if (repeats.inOneSurah) {
                        copy.repeatsInSurah(repeats.total, store.surahTitle(ui.current.key.surah, language, names))
                    } else {
                        copy.repeats(repeats.total)
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                    verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
                ) {
                    repeats.places.forEach { place ->
                        QhChip(
                            label = if (repeats.inOneSurah) place.ayah.toString() else label(place),
                            selected = false,
                            onClick = { onOpen(place) },
                        )
                    }
                }
            }

            if (ui.similar.isNotEmpty()) {
                Spacer(Modifier.height(QhSpacing.x1))
                QhSectionHeader(copy.places)
                ui.similar.forEach { place ->
                    PlaceCard(
                        place = place,
                        title = label(place.key),
                        tag = null,
                        copy = copy,
                        arabicStyle = arabicStyle,
                        accent = accent,
                        onClick = { onOpen(place.key) },
                    )
                }
                if (ui.canTellApart) {
                    QhButton(
                        text = copy.tellApart,
                        onClick = {
                            onTellApart()
                            close()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = QhButtonSize.M,
                    )
                } else {
                    Text(text = copy.tellApartNeedsTwo, style = type.callout, color = colors.inkSecondary)
                }
            }
            Spacer(Modifier.height(QhSpacing.x2))
        }
    }
}

/** One ayah of the sheet: where it is, "learned" when it is, its text with [SimilarPlace.differing] in the accent colour. */
@Composable
private fun PlaceCard(
    place: SimilarPlace,
    title: String,
    tag: String?,
    copy: SimilarCopy,
    arabicStyle: androidx.compose.ui.text.TextStyle,
    accent: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)?,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val text = remember(place.text, place.differing, accent) { differenceText(place.text, place.differing, accent) }
    QhCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(title)
                    if (place.learned) append(", ").append(copy.learned)
                    if (onClick != null) append(". ").append(copy.openInReader)
                }
            },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            Text(text = title, style = type.title2, color = colors.ink)
            if (tag != null) Text(text = "· $tag", style = type.callout, color = colors.inkSecondary)
            Spacer(Modifier.weight(1f))
            if (place.learned) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.learned, modifier = Modifier.size(16.dp))
                Text(text = copy.learned, style = type.callout, color = colors.learned)
            }
        }
        Text(
            text = text,
            style = arabicStyle,
            color = colors.ink,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QhSpacing.x2),
        )
    }
}

/**
 * [text] with the words at [ranges] (both ends inclusive) in the accent colour and a faint wash behind them. It is the colour of the app,
 * not a red "error": a difference is not a mistake. A range is a whole word, so a colour never starts inside a letter and its signs.
 */
internal fun differenceText(text: String, ranges: List<IntRange>, accent: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in ranges) {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.14f)), start, end)
    }
}
