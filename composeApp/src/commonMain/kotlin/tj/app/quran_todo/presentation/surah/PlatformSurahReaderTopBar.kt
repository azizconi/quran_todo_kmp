package tj.app.quran_todo.presentation.surah

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform-native top bar while the reader remains shared Compose content. */
@Composable
internal expect fun PlatformSurahReaderTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
