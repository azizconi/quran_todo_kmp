package tj.app.quran_todo.debug.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.voice.WordMatching
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.VoiceProtoCopy
import tj.app.quran_todo.ui.lesson.rememberLessonArabicStyle
import tj.app.quran_todo.ui.settings.TransferIo
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.voiceproto.PcmRecorder
import tj.app.quran_todo.voiceproto.Wav
import tj.app.quran_todo.voiceproto.WhisperEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * "Voice: lab" (debug builds only, task F7): runs a wav of `files/voice/samples/` through the model and shows the verdicts and the
 * time; records new samples (wav + json: the passage and the date) and sends them all as one zip through the share sheet. That is how
 * the owner measures a real phone and collects the recordings of the testers. Nothing goes to the network.
 *
 * `adb shell am start -n tj.app.quran_todo/.debug.voice.VoiceLabActivity --ez bench true [--ez split false]` runs every sample with every model and writes
 * `files/voice/bench.json` and `VoiceBench` lines to the log, with no one to touch the screen.
 */
class VoiceLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bench = intent?.getBooleanExtra("bench", false) == true
        val split = intent?.getBooleanExtra("split", true) != false
        setContent {
            QhTheme(mode = ThemeMode.LIGHT, palette = ThemePalette.FOREST) {
                Box(Modifier.fillMaxSize().background(QhTheme.colors.bg).statusBarsPadding()) {
                    VoiceLab(autoBench = bench, splitAtPauses = split)
                }
            }
        }
    }
}

private class SampleItem(val file: File, val surah: Int?, val from: Int?, val to: Int?, val date: String, val seconds: Double)

private fun readSamples(dir: File): List<SampleItem> =
    dir.listFiles().orEmpty().filter { it.extension == "wav" }.sortedBy { it.name }.map { wav ->
        val meta = File(dir, wav.nameWithoutExtension + ".json").takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        SampleItem(
            file = wav,
            surah = meta?.optInt("surah", 0)?.takeIf { it > 0 },
            from = meta?.optInt("from", 0)?.takeIf { it > 0 },
            to = meta?.optInt("to", 0)?.takeIf { it > 0 },
            date = meta?.optString("date").orEmpty(),
            seconds = (wav.length() - 44).coerceAtLeast(0) / 2.0 / WhisperEngine.SAMPLE_RATE,
        )
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceLab(autoBench: Boolean, splitAtPauses: Boolean) {
    val context = LocalContext.current
    val copy = remember { VoiceProtoCopy.forApp() }
    val colors = QhTheme.colors
    val type = QhTheme.type
    val scope = rememberCoroutineScope()
    val engine = remember { VoiceProto.engine(context) }
    val samplesDir = remember { WhisperEngine.samplesDir(context) }
    val arabic = rememberLessonArabicStyle()
    val recorder = remember { PcmRecorder() }
    val phoneme = remember { PhonemeLabState(context, scope) }

    var models by remember { mutableStateOf(engine.availableModels()) }
    var model by remember { mutableStateOf(VoiceProto.defaultModel(models)) }
    var matching by remember { mutableStateOf(WordMatching.Fuzzy) }
    var split by remember { mutableStateOf(splitAtPauses) }
    var surah by remember { mutableStateOf("112") }
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf("4") }
    val samples = remember { mutableStateListOf<SampleItem>().also { it.addAll(readSamples(samplesDir)) } }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Pair<PassageText, VoiceCheckResult>?>(null) }

    fun refresh() {
        models = engine.availableModels()
        if (model !in models) model = VoiceProto.defaultModel(models)
        samples.clear()
        samples.addAll(readSamples(samplesDir))
    }

    suspend fun passageOf(item: SampleItem?): PassageText? {
        val s = item?.surah ?: surah.toIntOrNull() ?: return null
        val a = item?.from ?: from.toIntOrNull() ?: return null
        val b = item?.to ?: to.toIntOrNull() ?: a
        return PassageText.load(s, a, b)
    }

    fun run(item: SampleItem) {
        val chosen = model ?: run { status = copy.noModel; return }
        busy = true
        status = copy.analyzing
        scope.launch {
            val outcome = runCatching {
                val pcm = withContext(Dispatchers.IO) { Wav.read(item.file) } ?: error("not a 16 kHz 16 bit wav: ${item.file.name}")
                val passage = passageOf(item) ?: error("no passage")
                passage to VoiceProto.check(context, passage, pcm, chosen, matching, split)
            }
            outcome.onSuccess { shown = it; status = item.file.name }.onFailure { status = it.message ?: it.toString() }
            busy = false
        }
    }

    fun bench() {
        if (models.isEmpty()) { status = copy.noModel; return }
        busy = true
        status = copy.analyzing
        scope.launch {
            val rows = JSONArray()
            withContext(Dispatchers.Default) {
                for (item in readSamples(samplesDir)) {
                    val pcm = Wav.read(item.file) ?: continue
                    val passage = if (item.surah != null && item.from != null) PassageText.load(item.surah, item.from, item.to ?: item.from) else null
                    for (name in models) {
                        val transcript = VoiceProto.transcribe(context, pcm, name, split)
                        val errors = passage?.let { p -> tj.app.quran_todo.common.voice.alignRecitation(p.reference, tj.app.quran_todo.common.voice.heardWords(transcript.text), matching).errors }
                        val row = JSONObject()
                            .put("sample", item.file.name).put("model", name).put("audio_ms", transcript.audioMillis)
                            .put("analysis_ms", transcript.millis).put("rtf", transcript.realtimeFactor)
                            .put("threads", WhisperEngine.defaultThreads()).put("split", split).put("errors", errors ?: JSONObject.NULL).put("text", transcript.text)
                        rows.put(row)
                        Log.i("VoiceBench", "sample=${item.file.name} model=$name audio_ms=${transcript.audioMillis} analysis_ms=${transcript.millis} " +
                            "rtf=%.2f errors=$errors threads=${WhisperEngine.defaultThreads()}".format(Locale.US, transcript.realtimeFactor))
                    }
                }
                File(WhisperEngine.voiceDir(context), "bench.json").writeText(JSONObject().put("system", engine.systemInfo()).put("rows", rows).toString(1))
            }
            status = copy.benchDone
            busy = false
        }
    }

    fun saveSample() {
        val pcm = recorder.stop()
        recording = false
        val name = "s_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        Wav.write(File(samplesDir, "$name.wav"), pcm)
        File(samplesDir, "$name.json").writeText(
            JSONObject().put("surah", surah.toIntOrNull() ?: 0).put("from", from.toIntOrNull() ?: 0).put("to", to.toIntOrNull() ?: 0)
                .put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())).put("source", "mic").toString(),
        )
        status = "${copy.saved}: $name (${pcm.size / 16000} s)"
        refresh()
    }

    fun shareAll() {
        val files = samplesDir.listFiles().orEmpty()
        if (files.isEmpty()) { status = copy.noSamples; return }
        val dir = File(context.cacheDir, TransferIo.SHARE_DIR).apply { mkdirs() }
        val zip = File(dir, "voice-samples.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            files.forEach { f -> out.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(out) }; out.closeEntry() }
        }
        val uri = FileProvider.getUriForFile(context, TransferIo.authority(context), zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            setType("application/zip")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, copy.shareAll))
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start()) recording = true else status = copy.noMic
    }

    LaunchedEffect(Unit) { if (autoBench) bench() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        Text(copy.labTitle, style = type.headline, color = colors.ink)
        Text(engine.systemInfo().take(110), style = type.label, color = colors.inkTertiary)

        Text(copy.models, style = type.label, color = colors.inkSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            if (models.isEmpty()) Text(copy.noModel, style = type.callout, color = colors.danger)
            models.forEach { name ->
                QhButton(text = name, size = QhButtonSize.S, variant = if (name == model) QhButtonVariant.Primary else QhButtonVariant.Tonal, onClick = { model = name })
            }
        }
        Text(copy.matching, style = type.label, color = colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            QhButton(text = copy.strict, size = QhButtonSize.S, variant = if (matching == WordMatching.Strict) QhButtonVariant.Primary else QhButtonVariant.Tonal, onClick = { matching = WordMatching.Strict })
            QhButton(text = copy.fuzzy, size = QhButtonSize.S, variant = if (matching == WordMatching.Fuzzy) QhButtonVariant.Primary else QhButtonVariant.Tonal, onClick = { matching = WordMatching.Fuzzy })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            QhButton(text = copy.splitOn, size = QhButtonSize.S, variant = if (split) QhButtonVariant.Primary else QhButtonVariant.Tonal, onClick = { split = true })
            QhButton(text = copy.splitOff, size = QhButtonSize.S, variant = if (!split) QhButtonVariant.Primary else QhButtonVariant.Tonal, onClick = { split = false })
        }

        Text(copy.passage, style = type.label, color = colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            NumberField(copy.surah, surah, { surah = it }, Modifier.width(96.dp))
            NumberField(copy.from, from, { from = it }, Modifier.width(96.dp))
            NumberField(copy.to, to, { to = it }, Modifier.width(96.dp))
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
            if (!recording) {
                QhButton(text = copy.record, size = QhButtonSize.S, enabled = !busy && !phoneme.running, onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        if (recorder.start()) recording = true else status = copy.micFailed
                    } else permission.launch(Manifest.permission.RECORD_AUDIO)
                })
            } else {
                QhButton(text = copy.saveSample, size = QhButtonSize.S, onClick = ::saveSample)
            }
            QhButton(text = copy.shareAll, size = QhButtonSize.S, variant = QhButtonVariant.Tonal, enabled = !busy, onClick = ::shareAll)
            QhButton(text = copy.bench, size = QhButtonSize.S, variant = QhButtonVariant.Tonal, enabled = !busy, onClick = ::bench)
        }
        if (status.isNotEmpty()) Text(status, style = type.callout, color = colors.inkSecondary)

        Text("${copy.samples} (${samples.size})", style = type.label, color = colors.inkSecondary)
        if (samples.isEmpty()) Text(copy.noSamples, style = type.callout, color = colors.inkTertiary)
        samples.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.file.name, style = type.callout, color = colors.ink)
                    val place = if (item.surah != null) "${item.surah}:${item.from}-${item.to}  " else ""
                    Text("$place%.1f s  ${item.date}".format(Locale.US, item.seconds), style = type.label, color = colors.inkTertiary)
                }
                QhButton(text = copy.run, size = QhButtonSize.S, variant = QhButtonVariant.Tonal, enabled = !busy, onClick = { run(item) })
                QhButton(text = copy.runPhonemes, size = QhButtonSize.S, variant = QhButtonVariant.Tonal, enabled = !busy && !phoneme.busy && !phoneme.running, onClick = {
                    scope.launch { phoneme.runSample(item.file, passageOf(item), copy) }
                })
                QhButton(text = copy.delete, size = QhButtonSize.S, variant = QhButtonVariant.Ghost, enabled = !busy, onClick = {
                    item.file.delete(); File(samplesDir, item.file.nameWithoutExtension + ".json").delete(); refresh()
                })
            }
        }

        shown?.let { (passage, result) -> VoiceCheckResultView(passage, result, copy, arabic) }

        PhonemeLabSection(phoneme, surah, from, to, copy, arabic)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
