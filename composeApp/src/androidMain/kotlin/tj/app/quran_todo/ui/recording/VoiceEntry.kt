package tj.app.quran_todo.ui.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.i18n.rememberVoiceCopy
import tj.app.quran_todo.ui.theme.QhTheme

/** How many recordings the passage [key] has (0 until they are counted). */
@Composable
fun rememberRecordingCount(key: PassageKey): Int {
    val controller = koinInject<VoiceController>()
    val count by remember(controller, key) { controller.count(key) }.collectAsState(initial = 0)
    return count
}

/**
 * The entry to "My reading" under the title of the "Recall" step: "Record my reading" (a press asks for the microphone the first
 * time and starts recording) and, when the passage has recordings, "My reading · 3" that opens their list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceEntryRow(key: PassageKey, state: VoiceSheetState, modifier: Modifier = Modifier) {
    val copy = rememberVoiceCopy()
    val count = rememberRecordingCount(key)
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        QhButton(
            text = copy.recordAction,
            onClick = state::openRecording,
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.S,
            icon = Icons.Rounded.Mic,
        )
        if (count > 0) {
            QhButton(
                text = copy.myReadingCount(count),
                onClick = state::openList,
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.S,
            )
        }
    }
}

/**
 * The microphone of the top bar of "Check yourself": opens the list when the passage has recordings (it has "Record another"),
 * records at once when it has none.
 */
@Composable
fun VoiceBarButton(key: PassageKey, state: VoiceSheetState, modifier: Modifier = Modifier) {
    val copy = rememberVoiceCopy()
    val count = rememberRecordingCount(key)
    QhIconButton(
        icon = Icons.Rounded.Mic,
        contentDescription = if (count > 0) copy.myReadingCount(count) else copy.recordAction,
        onClick = { if (count > 0) state.openList() else state.openRecording() },
        filled = false,
        tint = QhTheme.colors.ink,
        modifier = modifier,
    )
}
