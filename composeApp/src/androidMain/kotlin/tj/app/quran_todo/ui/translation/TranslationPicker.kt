package tj.app.quran_todo.ui.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.data.translation.TranslationCatalog
import tj.app.quran_todo.data.translation.TranslationChoice
import tj.app.quran_todo.data.translation.TranslationEdition
import tj.app.quran_todo.data.translation.TranslationOrigin
import tj.app.quran_todo.domain.repository.QuranReferenceRepository
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.TranslationCopy
import tj.app.quran_todo.ui.i18n.rememberTranslationCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.reader.components.SheetTitle
import tj.app.quran_todo.ui.settings.nativeName
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The edition chosen for the language of the interface, as the screens see it: it changes with every choice. */
@Composable
fun rememberChosenEdition(language: AppLanguage = LocalAppLanguage.current): TranslationEdition {
    val revision by TranslationChoice.store.revision.collectAsState()
    return remember(revision, language) { TranslationChoice.store.editionFor(language) }
}

/**
 * The row "Translation: Kuliev" of the "Aa" sheet of the reader (task F4a): it opens the sheet of editions. Self-contained, like the
 * tajweed rows: the sheet only has to place it.
 */
@Composable
fun TranslationEditionRow() {
    val language = LocalAppLanguage.current
    val copy = rememberTranslationCopy(language)
    val edition = rememberChosenEdition(language)
    var open by remember { mutableStateOf(false) }
    QhNavRow(title = copy.row(edition.name.of(language)), onClick = { open = true })
    if (open) TranslationSheet(onDismiss = { open = false })
}

/**
 * The sheet of editions: first those in the language of the interface, below them "Other languages" (folded), where a translation
 * in another language can be chosen without the interface changing. An edition that is stored whole says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSheet(onDismiss: () -> Unit) {
    val language = LocalAppLanguage.current
    val copy = rememberTranslationCopy(language)
    val chosen = rememberChosenEdition(language)
    val repository = koinInject<QuranReferenceRepository>()
    val changes by repository.translationChanges.collectAsState()
    var downloaded by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(changes) { downloaded = repository.downloadedTranslations() }
    // QuranEnc asks to keep to its latest versions: the list is asked for quietly, at most once in 7 days.
    LaunchedEffect(Unit) { repository.checkTranslationUpdates() }

    // The chosen edition may be in another language: then that group is open from the start.
    var othersOpen by remember { mutableStateOf(chosen.language != language) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(top = QhSpacing.x1)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            SheetTitle(copy.sheetTitle)
            Text(text = copy.sheetHint, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)

            EditionGroup(
                editions = TranslationCatalog.editionsOf(language),
                uiLanguage = language,
                chosen = chosen,
                downloaded = downloaded,
                copy = copy,
                onPick = { edition ->
                    TranslationChoice.store.select(language, edition)
                    onDismiss()
                },
            )

            OthersHeader(open = othersOpen, copy = copy, onToggle = { othersOpen = !othersOpen })
            AnimatedVisibility(
                visible = othersOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
                    AppLanguage.entries.filter { it != language }.forEach { other ->
                        val editions = TranslationCatalog.editionsOf(other)
                        if (editions.isNotEmpty()) {
                            Text(
                                text = other.nativeName(),
                                style = QhTheme.type.title2,
                                color = QhTheme.colors.ink,
                                modifier = Modifier.semantics { heading() },
                            )
                            EditionGroup(
                                editions = editions,
                                uiLanguage = language,
                                chosen = chosen,
                                downloaded = downloaded,
                                copy = copy,
                                onPick = { edition ->
                                    TranslationChoice.store.select(language, edition)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(QhSpacing.x4))
        }
    }
}

@Composable
private fun OthersHeader(open: Boolean, copy: TranslationCopy, onToggle: () -> Unit) {
    val colors = QhTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressable(onClick = onToggle, scale = 0.99f)
            .semantics { contentDescription = copy.otherLanguages },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.otherLanguages,
            style = QhTheme.type.title2,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.inkSecondary,
        )
    }
}

@Composable
private fun EditionGroup(
    editions: List<TranslationEdition>,
    uiLanguage: AppLanguage,
    chosen: TranslationEdition,
    downloaded: Set<String>,
    copy: TranslationCopy,
    onPick: (TranslationEdition) -> Unit,
) {
    QhCard(contentPadding = PaddingValues(0.dp)) {
        editions.forEachIndexed { index, edition ->
            if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
            EditionRow(
                edition = edition,
                uiLanguage = uiLanguage,
                selected = edition.key == chosen.key,
                downloaded = edition.key in downloaded,
                copy = copy,
                onClick = { onPick(edition) },
            )
        }
    }
}

@Composable
private fun EditionRow(
    edition: TranslationEdition,
    uiLanguage: AppLanguage,
    selected: Boolean,
    downloaded: Boolean,
    copy: TranslationCopy,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val name = edition.name.of(uiLanguage)
    val author = edition.author.of(uiLanguage)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressable(onClick = onClick, scale = 0.99f, role = Role.RadioButton)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = buildString {
                    append(name)
                    if (author != name) append(", ").append(author)
                    if (downloaded) append(", ").append(copy.downloadedBadge)
                }
            }
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (edition.scriptDiffersFromInterface) "$name · ${copy.cyrillicMark}" else name,
                style = QhTheme.type.title2,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (author != name) {
                Text(text = author, style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (edition.origin == TranslationOrigin.QURAN_ENC) {
                // The terms of QuranEnc: the source and the version are named where the edition is offered.
                Text(text = copy.quranEnc(edition.version), style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 1)
            }
            if (downloaded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = colors.learned,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(text = copy.downloadedBadge, style = QhTheme.type.callout, color = colors.learned, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(QhSpacing.x3))
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.hairline,
            modifier = Modifier.size(24.dp),
        )
    }
}
