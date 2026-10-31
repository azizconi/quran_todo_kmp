package tj.app.quran_todo.debug.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.voice.WordMatching
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.VoiceProtoCopy
import tj.app.quran_todo.ui.lesson.rememberLessonArabicStyle
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.voiceproto.PcmRecorder
import tj.app.quran_todo.voiceproto.Wav

private sealed interface PanelState {
    data object Idle : PanelState
    data object Recording : PanelState
    data object Analyzing : PanelState
    class Done(val result: VoiceCheckResult) : PanelState
    class Failed(val message: String) : PanelState
}

/**
 * "Check by voice (prototype)" in the "Recall" step of a lesson (debug builds only, task F7): reads the passage by memory, "Stop",
 * the analysis, the words coloured. Nothing leaves the phone. Also a link to the lab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCheckPanel(ayahs: List<AyahEntity>) {
    val context = LocalContext.current
    val copy = remember { VoiceProtoCopy.forApp() }
    val colors = QhTheme.colors
    val scope = rememberCoroutineScope()
    val passage = remember(ayahs) { PassageText.of(ayahs) }
    val recorder = remember { PcmRecorder() }
    var state by remember(ayahs) { mutableStateOf<PanelState>(PanelState.Idle) }
    var model by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(0f) }
    val arabic = rememberLessonArabicStyle()

    DisposableEffect(Unit) { onDispose { if (state is PanelState.Recording) recorder.stop() } }
    LaunchedEffect(state) {
        seconds = 0
        while (state is PanelState.Recording) {
            level = recorder.level
            delay(100)
            seconds++
        }
    }

    fun start() {
        val models = VoiceProto.engine(context).availableModels()
        val chosen = model?.takeIf { it in models } ?: VoiceProto.defaultModel(models)
        if (chosen == null) {
            state = PanelState.Failed(copy.noModel)
            return
        }
        model = chosen
        state = if (recorder.start()) PanelState.Recording else PanelState.Failed(copy.micFailed)
    }

    fun stopAndCheck() {
        val pcm = Wav.toFloat(recorder.stop())
        val chosen = model ?: return
        state = PanelState.Analyzing
        scope.launch {
            state = runCatching { VoiceProto.check(context, passage, pcm, chosen, WordMatching.Fuzzy) }
                .fold({ PanelState.Done(it) }, { PanelState.Failed(it.message ?: it.toString()) })
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else state = PanelState.Failed(copy.noMic)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x2),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        when (val current = state) {
            PanelState.Idle, is PanelState.Failed -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
                    QhButton(
                        text = copy.check,
                        size = QhButtonSize.S,
                        variant = QhButtonVariant.Tonal,
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) start() else permission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                    QhButton(
                        text = copy.lab,
                        size = QhButtonSize.S,
                        variant = QhButtonVariant.Ghost,
                        onClick = { context.startActivity(Intent(context, VoiceLabActivity::class.java)) },
                    )
                }
                if (current is PanelState.Failed) Text(current.message, style = QhTheme.type.callout, color = colors.danger)
            }
            PanelState.Recording -> {
                Text(copy.reading, style = QhTheme.type.callout, color = colors.ink)
                Box(Modifier.fillMaxWidth(fraction = level.coerceIn(0.03f, 1f)).height(6.dp).background(colors.primary))
                QhButton(text = "${copy.stop}  ${seconds / 10}.${seconds % 10} ${copy.secondsUnit}", size = QhButtonSize.S, onClick = ::stopAndCheck)
            }
            PanelState.Analyzing -> Text(copy.analyzing, style = QhTheme.type.callout, color = colors.inkSecondary)
            is PanelState.Done -> {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    VoiceCheckResultView(passage, current.result, copy, arabic)
                }
                QhButton(text = copy.check, size = QhButtonSize.S, variant = QhButtonVariant.Tonal, onClick = { state = PanelState.Idle })
            }
        }
    }
}
