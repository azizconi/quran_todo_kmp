package tj.app.quran_todo.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.i18n.rememberTrustCopy
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.settings.PrivacyPolicyUrl
import tj.app.quran_todo.ui.settings.configuredContactEmail
import tj.app.quran_todo.ui.settings.openLink
import tj.app.quran_todo.ui.settings.openMailTo
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The short privacy summary (`Dest.Privacy`): the main facts in a few lines. "Full text" opens
 * [PrivacyPolicyUrl] when the owner has set it; the contact button shows once a contact address is set.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val language = LocalAppLanguage.current
    val copy = rememberTrustCopy(language)
    val settingsCopy = rememberSettingsCopy(language)
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val colors = QhTheme.colors
    val icons = remember(colors) {
        listOf(
            Icons.Rounded.PersonOff to colors.primary,
            Icons.Rounded.PhoneAndroid to colors.gold,
            Icons.Rounded.Public to colors.due,
            Icons.Rounded.BarChart to colors.learned,
            Icons.Rounded.Lock to colors.primary,
            Icons.Rounded.Block to colors.gold,
            Icons.Rounded.DeleteOutline to colors.danger,
        )
    }
    val fullTextUrl = PrivacyPolicyUrl
    val contactEmail = configuredContactEmail()

    AboutScaffold(
        title = copy.privacyTitle,
        backLabel = settingsCopy.native.back,
        onBack = onBack,
    ) { stagger ->
        Text(
            text = copy.privacyIntro,
            style = QhTheme.type.body,
            color = colors.inkSecondary,
            modifier = Modifier
                .padding(horizontal = QhSpacing.x1)
                .staggeredEnter(0, stagger),
        )
        QhCard(
            modifier = Modifier.staggeredEnter(1, stagger),
            contentPadding = PaddingValues(0.dp),
        ) {
            copy.privacyPoints.forEachIndexed { index, point ->
                if (index > 0) QhRowDivider()
                val (icon, tint) = icons[index % icons.size]
                PrivacyPointRow(icon = icon, tint = tint, title = point.title, text = point.text)
            }
        }
        if (fullTextUrl != null || contactEmail != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .staggeredEnter(2, stagger),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) {
                if (fullTextUrl != null) {
                    QhButton(
                        text = copy.privacyFullText,
                        onClick = {
                            if (!openLink(context, fullTextUrl)) {
                                toaster.show(settingsCopy.linkUnavailable, ToastKind.Error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.OpenInBrowser,
                    )
                }
                if (contactEmail != null) {
                    QhButton(
                        text = copy.privacyContact,
                        onClick = {
                            if (!openMailTo(context, contactEmail)) {
                                toaster.show(settingsCopy.linkUnavailable, ToastKind.Error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = QhButtonVariant.Tonal,
                        icon = Icons.Rounded.Mail,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyPointRow(icon: ImageVector, tint: Color, title: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.Top,
    ) {
        QhIconTile(icon, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
            Text(text = title, style = QhTheme.type.title2, color = QhTheme.colors.ink)
            Text(text = text, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        }
    }
}
