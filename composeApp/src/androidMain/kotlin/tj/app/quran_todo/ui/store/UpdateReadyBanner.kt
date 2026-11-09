package tj.app.quran_todo.ui.store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * A card over the bottom of every screen once a flexible Play update is downloaded ([AppUpdates.readyToInstall]): "Restart"
 * installs it, "Later" hides the card until the next launch (Play then installs the update on its own in the background).
 */
@Composable
fun UpdateReadyBanner(modifier: Modifier = Modifier) {
    val ready by AppUpdates.readyToInstall.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val copy = updateCopy(LocalAppLanguage.current)
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg

    AnimatedVisibility(
        visible = ready && !dismissed,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = QhSpacing.x4, end = QhSpacing.x4, bottom = QhSpacing.x4),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, shape)
                .padding(QhSpacing.x4)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(copy.title, style = QhTheme.type.title2, color = colors.ink)
                    Text(copy.body, style = QhTheme.type.callout, color = colors.inkSecondary)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2, Alignment.End),
            ) {
                QhButton(
                    text = copy.later,
                    onClick = { dismissed = true },
                    variant = QhButtonVariant.Ghost,
                    size = QhButtonSize.M,
                )
                QhButton(
                    text = copy.restart,
                    onClick = { AppUpdates.installNow() },
                    size = QhButtonSize.M,
                )
            }
        }
    }
}

private data class UpdateCopy(val title: String, val body: String, val restart: String, val later: String)

private fun updateCopy(language: AppLanguage): UpdateCopy = when (language) {
    AppLanguage.RU -> UpdateCopy(
        title = "Новая версия загружена",
        body = "Перезапустите приложение, чтобы установить обновление.",
        restart = "Перезапустить",
        later = "Позже",
    )
    AppLanguage.EN -> UpdateCopy(
        title = "New version downloaded",
        body = "Restart the app to install the update.",
        restart = "Restart",
        later = "Later",
    )
    AppLanguage.TG -> UpdateCopy(
        title = "Версияи нав боргирӣ шуд",
        body = "Барои насби навсозӣ барномаро аз нав оғоз кунед.",
        restart = "Аз нав оғоз",
        later = "Баъдтар",
    )
    AppLanguage.UZ -> UpdateCopy(
        title = "Yangi versiya yuklab olindi",
        body = "Yangilanishni o‘rnatish uchun ilovani qayta ishga tushiring.",
        restart = "Qayta ishga tushirish",
        later = "Keyinroq",
    )
    AppLanguage.TR -> UpdateCopy(
        title = "Yeni sürüm indirildi",
        body = "Güncellemeyi yüklemek için uygulamayı yeniden başlatın.",
        restart = "Yeniden başlat",
        later = "Sonra",
    )
}
