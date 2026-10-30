package tj.app.quran_todo.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.i18n.rememberDownloadsCopy
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * "Downloads and storage · 230 MB": the one row for the audio on the phone (what was downloaded and what the listening cache holds).
 * It opens the screen of the same name, where the downloads are chosen, watched and deleted. The size is read off the main thread
 * each time the settings open.
 */
@Composable
internal fun DownloadsRow(language: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val copy = rememberDownloadsCopy(language)
    var bytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        bytes = withContext(Dispatchers.IO) { DownloadStorage.shared.totalBytes() + AudioCacheStorage.shared.totalBytes() }
    }
    QhValueRow(
        title = copy.title,
        value = bytes?.let(copy::sizeLabel) ?: "…",
        onClick = onClick,
        modifier = modifier,
        icon = Icons.Rounded.CloudDownload,
        iconTint = QhTheme.colors.primary,
    )
}
