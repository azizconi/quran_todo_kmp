package tj.app.quran_todo.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.i18n.TrustCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.i18n.rememberTrustCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.settings.SettingsSection
import tj.app.quran_todo.ui.settings.openLink
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The "Sources" screen (`Dest.Sources`): where the Quran text, translations, surah names, audio, fonts
 * and libraries come from, who made them and on what terms. The data is in `SourcesData.kt`.
 */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val language = LocalAppLanguage.current
    val copy = rememberTrustCopy(language)
    val settingsCopy = rememberSettingsCopy(language)
    val context = LocalContext.current
    val toaster = LocalToaster.current

    AboutScaffold(
        title = copy.sourcesTitle,
        backLabel = settingsCopy.native.back,
        onBack = onBack,
    ) { stagger ->
        Text(
            text = copy.sourcesIntro,
            style = QhTheme.type.body,
            color = QhTheme.colors.inkSecondary,
            modifier = Modifier
                .padding(horizontal = QhSpacing.x1)
                .staggeredEnter(0, stagger),
        )
        SourceGroups.forEachIndexed { index, group ->
            SettingsSection(
                title = copy.groupTitle(group.id),
                modifier = Modifier.staggeredEnter(index + 1, stagger),
            ) {
                group.items.forEachIndexed { itemIndex, item ->
                    if (itemIndex > 0) QhRowDivider(startIndent = QhSpacing.card)
                    SourceRow(
                        item = item,
                        copy = copy,
                        onOpen = { url ->
                            if (!openLink(context, url)) {
                                toaster.show(settingsCopy.linkUnavailable, ToastKind.Error)
                            }
                        },
                    )
                }
            }
        }
        Text(
            text = copy.sourcesFooter,
            style = QhTheme.type.callout,
            color = QhTheme.colors.inkSecondary,
            modifier = Modifier
                .padding(horizontal = QhSpacing.x1)
                .staggeredEnter(SourceGroups.size + 1, stagger),
        )
    }
}

@Composable
private fun SourceRow(item: SourceItem, copy: TrustCopy, onOpen: (String) -> Unit) {
    val colors = QhTheme.colors
    val url = item.url
    val clickable = if (url != null) {
        Modifier.pressable(onClick = { onOpen(url) }, scale = 0.99f, onClickLabel = copy.openLink)
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(clickable)
            .semantics(mergeDescendants = true) { }
            .heightIn(min = 56.dp)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = item.title, style = QhTheme.type.title2, color = colors.ink)
            if (item.author != null) {
                Text(text = item.author, style = QhTheme.type.callout, color = colors.ink)
            }
            if (item.provider != null) {
                Text(text = item.provider, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
            if (item.license != null) {
                Text(
                    text = "${copy.licenseLabel}: ${item.license}",
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                )
            }
            if (item.note != null) {
                Text(text = copy.noteText(item.note), style = QhTheme.type.callout, color = colors.inkSecondary)
            }
            if (url != null) {
                Text(text = displayHost(url), style = QhTheme.type.label, color = colors.primary)
            }
        }
        if (url != null) {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(QhSpacing.iconInline),
            )
        }
    }
}

internal fun TrustCopy.groupTitle(id: SourceGroupId): String = when (id) {
    SourceGroupId.QuranText -> groupQuranText
    SourceGroupId.Translations -> groupTranslations
    SourceGroupId.SurahNames -> groupSurahNames
    SourceGroupId.Audio -> groupAudio
    SourceGroupId.Fonts -> groupFonts
    SourceGroupId.Libraries -> groupLibraries
}

internal fun TrustCopy.noteText(note: SourceNote): String = when (note) {
    SourceNote.TanzilTerms -> noteTanzilTerms
    SourceNote.TranslatorRights -> noteTranslatorRights
    SourceNote.AppTeamNames -> noteAppTeamNames
    SourceNote.ReciterRights -> noteReciterRights
}
