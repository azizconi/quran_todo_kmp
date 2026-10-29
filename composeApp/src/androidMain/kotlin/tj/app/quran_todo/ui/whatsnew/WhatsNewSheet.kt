package tj.app.quran_todo.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.Calligraphy
import tj.app.quran_todo.ui.components.CalligraphyText
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The wait between Home being on screen and the sheet rising, so the home screen has settled behind it. */
private const val SettleMillis = 900L

/**
 * Hosts the one-time "What's new: the lesson of the day" over Home (task C1).
 *
 * - [pending]: the launch-time decision ([WhatsNewGate]); false for new people and when it was shown already.
 * - [atHome]: Home is the screen that is on top and at rest.
 * - [suppressed]: a link from the reminder was opened in this launch: the sheet is put off to the next start (it is not marked as
 *   shown), and it goes away if it is already up.
 *
 * It rises once per launch at most and writes its flag the moment it is up, so it is not repeated. A sheet is a window of its own:
 * the spotlights of C2 do not start over it.
 */
@Composable
internal fun WhatsNewHost(
    pending: Boolean,
    atHome: Boolean,
    suppressed: Boolean,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var raised by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pending, atHome, suppressed) {
        if (pending && atHome && !suppressed && !raised) {
            delay(SettleMillis)
            raised = true
            visible = true
        }
    }
    if (visible && !suppressed) {
        WhatsNewSheet(onClose = { visible = false }, onOpenLesson = onOpenLesson, onOpenPlan = onOpenPlan)
    }
}

@OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3Api::class)
@Composable
private fun WhatsNewSheet(
    onClose: () -> Unit,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val viewModel = koinViewModel<WhatsNewViewModel>()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val copy = rememberWhatsNewCopy(language)
    val home = remember(language) { homeCopy(language) }
    val colors = QhTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var answered by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.shown() }

    /** Slides the sheet out, then reports the answer once and runs [then]. */
    fun answer(action: WhatsNewAction, then: () -> Unit = {}) {
        if (answered) return
        answered = true
        viewModel.acted(action)
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onClose()
            then()
        }
    }

    QhSheet(
        onDismiss = {
            if (!answered) {
                answered = true
                viewModel.acted(WhatsNewAction.Later)
            }
            onClose()
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = QhSpacing.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            CalligraphyText(Calligraphy.Hifz, size = 40.dp, revealMillis = 900)
            Text(
                text = copy.title,
                style = QhTheme.type.headline,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.body, style = QhTheme.type.body, color = colors.inkSecondary, textAlign = TextAlign.Center)
            // Nothing is promised until the repository has answered; then the line says what the button will do.
            Text(
                text = when (hasPlan) {
                    true -> copy.withPlan
                    false -> copy.withoutPlan
                    null -> ""
                },
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
            val planExists = hasPlan == true
            QhButton(
                text = if (planExists) copy.openLesson else home.lessonMakePlan,
                onClick = {
                    if (planExists) answer(WhatsNewAction.Lesson, onOpenLesson) else answer(WhatsNewAction.Plan, onOpenPlan)
                },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Primary,
                size = QhButtonSize.L,
            )
            QhButton(
                text = copy.later,
                onClick = { answer(WhatsNewAction.Later) },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }
}
