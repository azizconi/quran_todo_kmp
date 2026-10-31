package tj.app.quran_todo.ui.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.translation.SearchText
import tj.app.quran_todo.data.translation.TranslationHit
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.i18n.TranslationCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The list of the tab "In translation" (task F4a), as items of the list of the home screen: a line saying which translation is
 * searched, then one of: the offer to download it whole, "Downloading…", the failure with "Retry", the hint "type two letters", the
 * results (the surah and the ayah, the line of the translation with the words that were searched marked), or "Nothing found".
 *
 * [surahName] gives the name of a surah in the language of the interface. [onOpen] opens the reader at (surah, ayah in the surah).
 */
internal fun LazyListScope.translationSearchItems(
    ui: TranslationSearchUi,
    query: String,
    copy: TranslationCopy,
    language: AppLanguage,
    sizeLabel: (Long) -> String,
    surahName: (Int) -> String,
    onOpen: (surah: Int, ayah: Int) -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    val editionName = ui.edition.name.of(language)
    item(key = "translation-scope", contentType = "translation-note") {
        Text(
            text = copy.searchIn(editionName),
            style = QhTheme.type.callout,
            color = QhTheme.colors.inkSecondary,
            modifier = Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x2),
        )
    }
    when (ui.phase) {
        TranslationSearchPhase.NeedDownload -> item(key = "translation-need", contentType = "translation-card") {
            StatusCard(
                text = copy.needDownload(sizeLabel(ui.edition.wireBytes)),
                action = copy.download,
                onAction = onDownload,
            )
        }
        TranslationSearchPhase.Downloading -> item(key = "translation-downloading", contentType = "translation-card") {
            StatusCard(text = copy.downloading)
        }
        TranslationSearchPhase.Failed -> item(key = "translation-failed", contentType = "translation-card") {
            StatusCard(text = copy.downloadFailed, action = copy.retry, onAction = onRetry, failed = true)
        }
        TranslationSearchPhase.Loading -> item(key = "translation-loading", contentType = "translation-card") {
            StatusCard(text = copy.loading)
        }
        TranslationSearchPhase.Ready -> {
            val hits = ui.result.hits
            when {
                query.isBlank() || !ui.searched || !SearchText.isSearchable(query) ->
                    item(key = "translation-hint", contentType = "translation-note") {
                        Text(
                            text = copy.searchTooShort,
                            style = QhTheme.type.body,
                            color = QhTheme.colors.inkSecondary,
                            modifier = Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x2),
                        )
                    }
                hits.isEmpty() -> item(key = "translation-empty", contentType = "translation-empty") {
                    EmptyState(
                        title = copy.noResultsTitle,
                        message = copy.noResultsBody,
                        modifier = Modifier.padding(top = QhSpacing.x8),
                    )
                }
                else -> {
                    items(hits.size, key = { hits[it].ayahNumber }, contentType = { "translation-hit" }) { position ->
                        val hit = hits[position]
                        HitRow(hit = hit, surahName = surahName(hit.surah), onClick = { onOpen(hit.surah, hit.ayahInSurah) })
                    }
                    if (ui.result.truncated) {
                        item(key = "translation-truncated", contentType = "translation-note") {
                            Text(
                                text = copy.truncatedLabel(hits.size),
                                style = QhTheme.type.callout,
                                color = QhTheme.colors.inkSecondary,
                                modifier = Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(text: String, action: String? = null, onAction: () -> Unit = {}, failed: Boolean = false) {
    QhCard(modifier = Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x1)) {
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            Text(
                text = text,
                style = QhTheme.type.body,
                color = if (failed) QhTheme.colors.danger else QhTheme.colors.ink,
            )
            if (action != null) {
                QhButton(text = action, onClick = onAction, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
            }
        }
    }
}

/** "Al-Baqarah 2:255" and under it the translation of that ayah with the searched words marked. */
@Composable
private fun HitRow(hit: TranslationHit, surahName: String, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val title = "$surahName ${hit.surah}:${hit.ayahInSurah}"
    val marked = highlighted(hit, colors.primary, colors.primaryContainer)
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, scale = 0.995f)
            .semantics(mergeDescendants = true) { contentDescription = "$title. ${hit.text}" },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x3),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
        ) {
            Text(text = title, style = QhTheme.type.title2, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = marked, style = QhTheme.type.body, color = colors.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        QhRowDivider(startIndent = QhSpacing.screen)
    }
}

/** The text of the ayah with the places of the searched words in bold, on a soft background. */
private fun highlighted(hit: TranslationHit, color: Color, background: Color): AnnotatedString =
    buildAnnotatedString {
        append(hit.text)
        hit.ranges.forEach { range ->
            val end = (range.last + 1).coerceAtMost(hit.text.length)
            if (range.first in 0 until end) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, background = background), range.first, end)
            }
        }
    }
