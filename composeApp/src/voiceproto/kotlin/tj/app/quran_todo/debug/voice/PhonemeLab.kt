package tj.app.quran_todo.debug.voice

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.voice.WordVerdict
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.VoiceProtoCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.voiceproto.PcmRecorder
import tj.app.quran_todo.voiceproto.PhonemeEngine
import tj.app.quran_todo.voiceproto.Wav
import java.io.File

/**
 * The state of the "Live (phonemes)" section of the lab (task F7, prototype): the model download, the live session and the last
 * result. Everything that blocks runs off the main thread.
 */
class PhonemeLabState(private val context: Context, private val scope: CoroutineScope) {
    private val engine = PhonemeProto.engine(context)
    private val recorder = PcmRecorder()

    var modelReady by mutableStateOf(engine.isAvailable())
        private set
    var downloadPercent by mutableStateOf<Int?>(null)
        private set

    /** An error line (download, microphone, a passage this engine cannot check). */
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set

    /** The session that runs or ran last: its state is the debug line. */
    var live by mutableStateOf<LiveRecitation?>(null)
        private set

    /** The passage on the screen and the final result for it (null while a session runs). */
    var passage by mutableStateOf<PassageText?>(null)
        private set
    var result by mutableStateOf<PhonemeResult?>(null)
        private set

    fun download(copy: VoiceProtoCopy) {
        if (downloadPercent != null) return
        downloadPercent = 0
        message = null
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    engine.download { p -> downloadPercent = if (p < 0) 0 else (p * 100).toInt() }
                }
            }
            outcome.onFailure { message = copy.downloadFailed(it.message ?: it.toString()) }
            modelReady = engine.isAvailable()
            downloadPercent = null
        }
    }

    /** Loads the passage, warms the model up and starts recording and the ticks. The microphone permission is checked by the caller. */
    fun start(surah: Int?, from: Int?, to: Int?, copy: VoiceProtoCopy) {
        if (busy || running) return
        busy = true
        message = copy.loadingModel
        result = null
        scope.launch {
            val outcome = runCatching {
                val shownPassage = PassageText.load(surah ?: 0, from ?: 0, to ?: from ?: 0)
                if (shownPassage == null) {
                    message = copy.noPassage
                    return@runCatching
                }
                val words = PhonemeProto.wordsOf(context, shownPassage)
                if (words.words == null) {
                    message = copy.unfit(words.reason.orEmpty())
                    return@runCatching
                }
                val hearing = withContext(Dispatchers.Default) {
                    PhonemeProto.hearing(context).also { it.hear(FloatArray(WARM_UP_SAMPLES)) }
                }
                if (!recorder.start()) {
                    message = copy.micFailed
                    return@runCatching
                }
                passage = shownPassage
                val session = LiveRecitation(hearing, words.words) { recorder.snapshot() }
                live = session
                running = true
                message = null
                session.start(scope)
            }
            outcome.onFailure { message = it.message ?: it.toString() }
            busy = false
        }
    }

    fun stop() {
        val session = live ?: return
        if (!running) return
        running = false
        busy = true
        scope.launch {
            val outcome = runCatching {
                val pcm = Wav.toFloat(recorder.stop())
                result = session.finish(pcm)
            }
            outcome.onFailure { message = it.message ?: it.toString() }
            busy = false
        }
    }

    /** The final check of a saved sample (no live part). [passage] is null when the sample has none. */
    fun runSample(file: File, samplePassage: PassageText?, copy: VoiceProtoCopy) {
        if (busy || running) return
        busy = true
        message = copy.analyzing
        result = null
        live = null
        scope.launch {
            val outcome = runCatching {
                val pcm = withContext(Dispatchers.IO) { Wav.read(file) } ?: error("not a 16 kHz 16 bit wav: ${file.name}")
                if (samplePassage == null) {
                    message = copy.noPassage
                    return@runCatching
                }
                val words = PhonemeProto.wordsOf(context, samplePassage)
                if (words.words == null) {
                    message = copy.unfit(words.reason.orEmpty())
                    return@runCatching
                }
                val checked = PhonemeProto.check(context, words.words, pcm)
                passage = samplePassage
                result = checked
                message = file.name
            }
            outcome.onFailure { message = it.message ?: it.toString() }
            busy = false
        }
    }

    private companion object {
        const val WARM_UP_SAMPLES = 16_000
    }
}

/** The "Live (phonemes)" section of the lab: the model, the live reading and the result of the last phoneme check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhonemeLabSection(
    state: PhonemeLabState,
    surah: String,
    from: String,
    to: String,
    copy: VoiceProtoCopy,
    arabicStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = QhTheme.colors
    val type = QhTheme.type
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.start(surah.toIntOrNull(), from.toIntOrNull(), to.toIntOrNull(), copy)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(copy.liveTitle, style = type.headline, color = colors.ink, modifier = Modifier.padding(top = QhSpacing.x2))
        Text(PhonemeEngine.MODEL_NAME, style = type.label, color = colors.inkTertiary)

        val percent = state.downloadPercent
        if (!state.modelReady) {
            QhButton(
                text = if (percent == null) copy.downloadModel else copy.downloading(percent),
                size = QhButtonSize.S,
                enabled = percent == null,
                onClick = { state.download(copy) },
            )
        } else {
            Text(copy.liveHint, style = type.callout, color = colors.inkSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                if (!state.running) {
                    QhButton(text = copy.startLive, size = QhButtonSize.S, enabled = !state.busy, onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            state.start(surah.toIntOrNull(), from.toIntOrNull(), to.toIntOrNull(), copy)
                        } else permission.launch(Manifest.permission.RECORD_AUDIO)
                    })
                } else {
                    QhButton(text = copy.stopLive, size = QhButtonSize.S, enabled = !state.busy, onClick = state::stop)
                }
            }
        }
        state.message?.let { Text(it, style = type.callout, color = colors.danger) }

        val session = state.live
        val passage = state.passage
        val liveState = session?.state?.collectAsState()?.value
        val result = state.result
        if (passage != null && result != null) {
            PhoneticPassageView(passage, passage.reference.size, result.judgement.verdicts, arabicStyle)
            val judgement = result.judgement
            val errors = judgement.missed + judgement.replaced + judgement.extras.size
            Text(
                "${copy.legendOk} / ${copy.legendMissed} / ${copy.legendReplaced}  ·  " +
                    if (errors == 0) copy.noErrors else copy.summary(judgement.missed, judgement.replaced, judgement.extras.size),
                style = type.callout,
                color = if (errors == 0) colors.learned else colors.danger,
            )
            Text(
                "${copy.model(PhonemeEngine.MODEL_NAME)}  ·  " +
                    copy.finalTimings(result.audioMillis, result.melMillis, result.modelMillis, result.judgeMillis),
                style = type.label,
                color = colors.inkSecondary,
            )
            if (liveState != null && liveState.ticks > 0) {
                Text(
                    copy.liveTimings(liveState.ticks, liveState.melMillis, liveState.modelMillis, liveState.followMillis, liveState.maxTickMillis, liveState.audioSeconds),
                    style = type.label,
                    color = colors.inkSecondary,
                )
            }
            Text(
                "${copy.heardTitle}: ${result.heard.ifBlank { copy.noSpeech }}",
                style = type.label,
                color = colors.inkTertiary,
                modifier = Modifier.padding(bottom = QhSpacing.x1),
            )
        } else if (passage != null && liveState != null) {
            PhoneticPassageView(passage, liveState.shown, liveState.verdicts, arabicStyle)
            Text(copy.wordsShown(liveState.shown, passage.reference.size), style = type.callout, color = colors.inkSecondary)
            liveState.error?.let { Text(it, style = type.callout, color = colors.danger) }
            if (liveState.ticks > 0) {
                Text(
                    copy.liveTimings(liveState.ticks, liveState.melMillis, liveState.modelMillis, liveState.followMillis, liveState.maxTickMillis, liveState.audioSeconds),
                    style = type.label,
                    color = colors.inkSecondary,
                )
            }
            Text(
                "${copy.heardTitle}: ${liveState.heard.ifBlank { copy.noSpeech }}",
                style = type.label,
                color = colors.inkTertiary,
                modifier = Modifier.padding(bottom = QhSpacing.x1),
            )
        }
    }
}

/**
 * The passage drawn like [VoiceCheckResultView] draws it, with the words from number [shown] on hidden: their text is transparent
 * over a soft rounded placeholder, so the lines do not move when a word appears. A word of [verdicts] is shown as it is when right,
 * red and underlined when missed, gold and bold when replaced.
 */
@Composable
fun PhoneticPassageView(
    passage: PassageText,
    shown: Int,
    verdicts: List<WordVerdict>,
    arabicStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var offset = 0
        passage.ayahs.forEach { ayah ->
            val start = offset
            offset += ayah.words.size
            val text = remember(ayah, start, shown, verdicts, colors) {
                buildAnnotatedString {
                    append(ayah.display)
                    ayah.words.ranges.forEachIndexed { index, range ->
                        val global = start + index
                        val style = if (global >= shown) {
                            SpanStyle(color = Color.Transparent)
                        } else {
                            when (verdicts.getOrNull(global)) {
                                WordVerdict.Missed -> SpanStyle(color = colors.danger, textDecoration = TextDecoration.Underline)
                                WordVerdict.Replaced -> SpanStyle(color = colors.gold, fontWeight = FontWeight.Bold)
                                else -> null
                            }
                        }
                        if (style != null) addStyle(style, range.first, range.last + 1)
                    }
                }
            }
            var layout by remember(ayah) { mutableStateOf<TextLayoutResult?>(null) }
            val placeholder = colors.surfaceStrong
            Text(
                text = text,
                style = arabicStyle.copy(textDirection = TextDirection.Rtl, fontSize = 24.sp, lineHeight = 44.sp),
                color = colors.ink,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().drawBehind {
                    val measured = layout ?: return@drawBehind
                    val inset = 8.dp.toPx()
                    ayah.words.ranges.forEachIndexed { index, range ->
                        if (start + index < shown || range.last + 1 > measured.layoutInput.text.length) return@forEachIndexed
                        val box = measured.getPathForRange(range.first, range.last + 1).getBounds()
                        drawRoundRect(
                            color = placeholder,
                            topLeft = Offset(box.left, box.top + inset),
                            size = Size(box.width, (box.height - 2 * inset).coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                },
            )
            Text("${ayah.surah}:${ayah.ayah}", style = type.label, color = colors.inkTertiary)
        }
    }
}
