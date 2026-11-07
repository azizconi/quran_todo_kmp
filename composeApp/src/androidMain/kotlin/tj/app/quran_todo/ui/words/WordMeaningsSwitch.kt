package tj.app.quran_todo.ui.words

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.rememberTafsirCopy
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * "Word meanings (English)" in the "Aa" sheet (task F4b): off by default. While it is on, a tap on an Arabic word of the text reader shows
 * the meaning of the word; the meanings of a surah are fetched at the first tap in it, so the switch itself asks for nothing.
 */
@Composable
fun WordMeaningsSwitchRow() {
    val prefs = koinInject<UiPrefs>()
    val copy = rememberTafsirCopy()
    val enabled by prefs.wordMeanings.collectAsState()
    QhSwitchRow(
        title = copy.wordsSwitchTitle,
        checked = enabled,
        onCheckedChange = prefs::setWordMeanings,
        description = copy.wordsSwitchHint,
    )
}
