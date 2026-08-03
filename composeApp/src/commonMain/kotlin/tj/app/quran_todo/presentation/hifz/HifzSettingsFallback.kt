package tj.app.quran_todo.presentation.hifz

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextAlign
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.presentation.home.HomeViewModel

/** Preview and non-native fallback only. Android and iOS open platform settings. */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun HifzSettingsRoute(@Suppress("UNUSED_PARAMETER") homeViewModel: HomeViewModel) {
    Text(
        text = "Настройки открываются нативно на устройстве.",
        modifier = Modifier.fillMaxSize(),
        textAlign = TextAlign.Center,
    )
}
