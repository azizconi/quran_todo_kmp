package tj.app.quran_todo.ui.hifz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.reader.components.KeepScreenOnEffect
import tj.app.quran_todo.ui.theme.QhTheme

/** The grade sheet comes up this long after the last answer, once the last ayah has folded. */
private const val GradeDelayMillis = 480L

/**
 * Everything "Check yourself" puts on top of the reader that is not a bar: the back gesture (it asks to leave the
 * mode instead of the reader), the "leave?" question, at the end of the session the grade sheet, and the toast that
 * says what was written.
 */
@Composable
fun HifzOverlays(mode: HifzMode) {
    val reduced = LocalReducedMotion.current
    val toaster = LocalToaster.current
    val copy = mode.copy
    val viewModel = mode.viewModel
    val session = mode.session
    val askingToLeave by viewModel.exitPrompt.collectAsState()
    val saving by viewModel.saving.collectAsState()

    BackHandler(enabled = session != null) { viewModel.requestExit() }
    // Reciting from memory takes time; the screen must not go dark in the middle of it.
    KeepScreenOnEffect(enabled = session != null)

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { saved ->
            toaster.show(if (saved == CheckSaved.Review) copy.savedReview else copy.savedCheck, ToastKind.Success)
        }
    }

    if (session != null && askingToLeave && !session.finished) {
        LeaveSheet(mode = mode, session = session)
    }

    val finished = session?.finished == true
    var gradeReady by remember { mutableStateOf(false) }
    LaunchedEffect(finished) {
        if (finished) {
            if (!reduced) delay(GradeDelayMillis)
            gradeReady = true
        } else {
            gradeReady = false
        }
    }
    if (session != null && finished && gradeReady) {
        val range = if (session.firstAyah == session.lastAyah) {
            "${copy.ayah} ${session.firstAyah}"
        } else {
            "${copy.ayah} ${session.firstAyah}–${session.lastAyah}"
        }
        RecallGradeSheet(
            session = session,
            title = "${mode.surahName} · $range",
            copy = copy,
            busy = saving,
            onConfirm = viewModel::finish,
            onDismiss = viewModel::exit,
        )
    }
}

/** Hides [sheetState] with its slide, then runs [after]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberHideThen(sheetState: SheetState): (after: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, scope) {
        { after -> scope.launch { sheetState.hide() }.invokeOnCompletion { after() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveSheet(mode: HifzMode, session: HifzSession) {
    val colors = QhTheme.colors
    val copy = mode.copy
    val viewModel = mode.viewModel
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen = rememberHideThen(sheetState)
    QhSheet(onDismiss = viewModel::dismissExitPrompt, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = copy.exitTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = copy.exitBody(session.markedCount, session.size),
                style = QhTheme.type.body,
                color = colors.inkSecondary,
            )
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QhButton(
                    text = copy.keepGoing,
                    onClick = { hideThen(viewModel::dismissExitPrompt) },
                    modifier = Modifier.fillMaxWidth(),
                )
                QhButton(
                    text = copy.leave,
                    onClick = { hideThen(viewModel::exit) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
            }
        }
    }
}
