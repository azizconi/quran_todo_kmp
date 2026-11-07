package tj.app.quran_todo.ui.tafsir

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.tafsir.TafsirHtml
import tj.app.quran_todo.common.tafsir.TafsirPassage
import tj.app.quran_todo.common.tafsir.TafsirProvider
import tj.app.quran_todo.common.tafsir.TafsirSource
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.about.AboutScaffold
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.i18n.TafsirCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.i18n.rememberTafsirCopy
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The commentary of one ayah as a full screen (`Dest.Tafsir`, task F4b): the ayah in our text with its translation on top, the chips of
 * the sources (as-Sa'di in Russian, Ibn Kathir in English, Muyassar in Arabic), the commentary under them, the arrows to the previous and
 * the next ayah of the surah. The size of the text is the reading size of the "Aa" sheet. Without network and without a copy on the phone:
 * "An internet connection is needed to load the commentary" and "Retry".
 */
@Composable
fun TafsirScreen(route: Dest.Tafsir, onBack: () -> Unit) {
    val viewModel = koinViewModel<TafsirViewModel>(parameters = { parametersOf(route) })
    val language = LocalAppLanguage.current
    val copy = rememberTafsirCopy(language)
    val settingsCopy = rememberSettingsCopy(language)
    LaunchedEffect(language) { viewModel.onLanguage(language) }

    val ayah by viewModel.ayah.collectAsState()
    val source by viewModel.source.collectAsState()
    val load by viewModel.load.collectAsState()
    val page by viewModel.page.collectAsState()
    val surahName by viewModel.surahName.collectAsState()

    val colors = QhTheme.colors
    val type = QhTheme.type
    val quranFamily = getQuranFontFamily()
    val fontSize = LocalAppSettings.current.readingFontSize
    val arabicStyle = remember(type, quranFamily, fontSize) { type.quranBody(quranFamily, fontSize.sp) }
    // The commentary is a little smaller than the reading text; an Arabic commentary is set in the reading font.
    val bodySize = (fontSize * 0.62f).coerceIn(15f, 24f)
    val bodyStyle = remember(type, bodySize) { type.body.copy(fontSize = bodySize.sp, lineHeight = (bodySize * 1.55f).sp) }
    val arabicBodyStyle = remember(type, quranFamily, bodySize) { type.quranBody(quranFamily, (bodySize * 1.25f).sp) }

    AboutScaffold(title = copy.title, backLabel = settingsCopy.native.back, onBack = onBack) { stagger ->
        // Which ayah, and the arrows.
        Row(
            Modifier
                .fillMaxWidth()
                .staggeredEnter(0, stagger),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = copy.previousAyah,
                onClick = { viewModel.go(-1) },
                enabled = ayah > 1,
            )
            Text(
                text = "$surahName · ${copy.ayahWord} $ayah",
                style = type.title2,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = QhSpacing.x2),
            )
            QhIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = copy.nextAyah,
                onClick = { viewModel.go(1) },
                enabled = ayah < viewModel.ayahCount,
            )
        }

        // The ayah and its translation.
        page?.let { shown ->
            QhCard(Modifier.staggeredEnter(1, stagger)) {
                Text(
                    text = shown.arabic,
                    style = arabicStyle,
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth(),
                )
                shown.translation?.let { translation ->
                    Text(
                        text = translation,
                        style = type.body,
                        color = colors.inkSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = QhSpacing.x3),
                    )
                }
            }
        }

        // The sources.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .staggeredEnter(2, stagger),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            TafsirSource.chipsFor(language).forEach { entry ->
                QhChip(label = copy.chip(entry), selected = entry == source, onClick = { viewModel.select(entry) })
            }
        }

        // The commentary.
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEnter(3, stagger),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            when (val state = load) {
                TafsirLoad.Loading -> LoadingLine(copy)
                TafsirLoad.Missing -> NeedInternet(copy, onRetry = viewModel::retry)
                is TafsirLoad.Ready -> {
                    val passage = page?.passage
                    if (page != null && passage == null) {
                        Text(copy.noCommentary, style = type.callout, color = colors.inkSecondary)
                    } else if (passage != null) {
                        PassageNotes(copy, source, viewModel.surah, passage, language)
                        PassageText(
                            passage = passage,
                            source = source,
                            bodyStyle = if (source == TafsirSource.Muyassar) arabicBodyStyle else bodyStyle,
                            quranFamily = quranFamily,
                        )
                        // QuranEnc asks for its name, the publisher and the edition under the text.
                        if (source.provider == TafsirProvider.QuranEnc) {
                            Text(copy.creditLine(state.chapter.version), style = type.label, color = colors.inkTertiary)
                        }
                    }
                }
            }
        }
    }
}

/** "Commentary in Russian" when it is not in the language of the app, and "Commentary on ayahs 2:1–5" when the text holds a group. */
@Composable
private fun PassageNotes(
    copy: TafsirCopy,
    source: TafsirSource,
    surah: Int,
    passage: TafsirPassage,
    language: tj.app.quran_todo.common.i18n.AppLanguage,
) {
    val colors = QhTheme.colors
    val notes = buildList {
        if (!TafsirSource.isInLanguageOf(source, language)) add(copy.writtenIn(source))
        if (source.cyrillic) add(copy.cyrillicNote)
        if (passage.isGroup) add(copy.group(surah, passage.first, passage.last))
    }
    if (notes.isNotEmpty()) {
        Text(notes.joinToString(" · "), style = QhTheme.type.callout, color = colors.inkSecondary)
    }
}

/** The blocks of the commentary, each laid out on its own (a long text is not one piece). */
@Composable
private fun PassageText(
    passage: TafsirPassage,
    source: TafsirSource,
    bodyStyle: TextStyle,
    quranFamily: FontFamily,
) {
    val colors = QhTheme.colors
    val blocks by produceState<List<AnnotatedString>?>(null, passage.text, quranFamily) {
        value = withContext(Dispatchers.Default) { blocksOf(passage.text, quranFamily) }
    }
    val shown = blocks
    if (shown == null) {
        // The HTML of a long text takes a moment to read: the same line as for the network, no jump.
        CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        shown.forEach { block ->
            Text(text = block, style = bodyStyle, color = colors.ink, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The text as the screen draws it: plain paragraphs, or the HTML of [TafsirHtml.blocks] read by `AnnotatedString.fromHtml`. */
private fun blocksOf(text: String, quranFamily: FontFamily): List<AnnotatedString> {
    val html = TafsirHtml.isHtml(text)
    return TafsirHtml.blocks(text).map { block ->
        val annotated = if (html) {
            try {
                AnnotatedString.fromHtml(block)
            } catch (_: Exception) {
                AnnotatedString(block.replace(Regex("<[^>]*>"), ""))
            }
        } else {
            AnnotatedString(block)
        }
        withArabicFont(trimmed(annotated), quranFamily)
    }
}

/** fromHtml ends a paragraph with blank lines: cut them off, so the spacing is the column's. */
private fun trimmed(text: AnnotatedString): AnnotatedString {
    val plain = text.text
    val start = plain.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
    val end = plain.indexOfLast { !it.isWhitespace() }.let { if (it < 0) plain.length else it + 1 }
    return if (start == 0 && end == plain.length) text else text.subSequence(start, end)
}

private val ArabicRun = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF][\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF\\s]*")

/** Arabic inside a Russian or English commentary (the ayahs it quotes) is drawn in the reading font; the text keeps its direction. */
private fun withArabicFont(text: AnnotatedString, family: FontFamily): AnnotatedString {
    val runs = ArabicRun.findAll(text.text).toList()
    if (runs.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        runs.forEach { run -> addStyle(SpanStyle(fontFamily = family), run.range.first, run.range.last + 1) }
    }
}

@Composable
private fun LoadingLine(copy: TafsirCopy) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = QhTheme.colors.primary, strokeWidth = 2.dp)
        Text(copy.loading, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
    }
}

@Composable
private fun NeedInternet(copy: TafsirCopy, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        Text(copy.needInternet, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
        QhButton(text = copy.retry, onClick = onRetry, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
    }
}
