package tj.app.quran_todo.debug.voice

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qurantodo.composeapp.generated.resources.Res
import tj.app.quran_todo.common.similar.NormalizedWords
import tj.app.quran_todo.common.similar.normalizedWords
import tj.app.quran_todo.common.voice.RecitationCheck
import tj.app.quran_todo.common.voice.SilenceSplitter
import tj.app.quran_todo.common.voice.WordMatching
import tj.app.quran_todo.common.voice.WordVerdict
import tj.app.quran_todo.common.voice.alignRecitation
import tj.app.quran_todo.common.voice.heardWords
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.i18n.VoiceProtoCopy
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.voiceproto.Transcript
import tj.app.quran_todo.voiceproto.WhisperEngine

/** An ayah of a passage as the screens draw it, with its normalized words. */
class AyahText(val surah: Int, val ayah: Int, val display: String, val words: NormalizedWords)

/** The passage to be recited: its ayahs and the flat list of normalized words that the check compares with. */
class PassageText(val ayahs: List<AyahText>) {
    val reference: List<String> = ayahs.flatMap { it.words.words }

    companion object {
        fun of(entities: List<AyahEntity>): PassageText = PassageText(
            entities.map { entity ->
                val display = splitBismillah(entity.surahNumber, entity.numberInSurah, entity.text)
                AyahText(entity.surahNumber, entity.numberInSurah, display, normalizedWords(display))
            },
        )

        /** The ayahs [from]..[to] of [surah] from the bundled text (the lab has no lesson to take them from). */
        suspend fun load(surah: Int, from: Int, to: Int): PassageText? {
            val tsv = withContext(Dispatchers.Default) { Res.readBytes("files/quran/quran-uthmani.tsv").decodeToString() }
            val ayahs = ArrayList<AyahText>()
            for (line in tsv.lineSequence()) {
                if (!line.startsWith("A\t")) continue
                val fields = line.split('\t')
                val s = fields[2].toInt()
                val a = fields[3].toInt()
                if (s != surah || a !in from..to) continue
                val display = splitBismillah(s, a, fields.last())
                ayahs += AyahText(s, a, display, normalizedWords(display))
            }
            return ayahs.takeIf { it.isNotEmpty() }?.let(::PassageText)
        }
    }
}

/** What a check gave: the recognized text, the verdicts on the words of the passage, the timing. */
class VoiceCheckResult(val transcript: Transcript, val heard: List<String>, val check: RecitationCheck)

object VoiceProto {
    private var engine: WhisperEngine? = null

    @Synchronized
    fun engine(context: Context): WhisperEngine = engine ?: WhisperEngine(context.applicationContext).also { engine = it }

    /** The quantization to use when none is chosen: the small one is the candidate for a release. */
    fun defaultModel(models: List<String>): String? = models.firstOrNull { it == "q5_0" } ?: models.firstOrNull()

    /**
     * Writes [pcm] (16 kHz mono) down. With [split] the recording is cut at its pauses first and the pieces are recognized one by one
     * (the model runs the ayahs together otherwise, see [SilenceSplitter]); the time is the sum. Blocking: run it off the main thread.
     */
    fun transcribe(context: Context, pcm: FloatArray, model: String, split: Boolean): Transcript {
        val engine = engine(context)
        if (!split) return engine.transcribe(pcm, model)
        val parts = SilenceSplitter.split(pcm).map { engine.transcribe(pcm.copyOfRange(it.first, it.last + 1), model) }
        return Transcript(
            text = parts.joinToString(" ") { it.text },
            millis = parts.sumOf { it.millis },
            audioMillis = pcm.size * 1000L / WhisperEngine.SAMPLE_RATE,
            model = if (parts.size > 1) "$model, ${parts.size} pieces" else model,
        )
    }

    /** Recognizes [pcm] (16 kHz mono) and compares it with [passage]. */
    suspend fun check(
        context: Context,
        passage: PassageText,
        pcm: FloatArray,
        model: String,
        matching: WordMatching,
        split: Boolean = true,
    ): VoiceCheckResult = withContext(Dispatchers.Default) {
        val transcript = transcribe(context, pcm, model, split)
        val heard = heardWords(transcript.text)
        VoiceCheckResult(transcript, heard, alignRecitation(passage.reference, heard, matching))
    }
}

/**
 * The passage with every word coloured by the verdict (right: as it is; missed: red and underlined; replaced: gold and bold) and, under
 * it, the replaced words with what was heard instead, the extra words, the counts and the timing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCheckResultView(
    passage: PassageText,
    result: VoiceCheckResult,
    copy: VoiceProtoCopy,
    arabicStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val check = result.check
    val texts = remember(passage, check, colors) {
        var offset = 0
        passage.ayahs.map { ayah ->
            val start = offset
            offset += ayah.words.size
            buildAnnotatedString {
                append(ayah.display)
                ayah.words.ranges.forEachIndexed { index, range ->
                    val style = when (check.words[start + index].verdict) {
                        WordVerdict.Correct -> null
                        WordVerdict.Missed -> SpanStyle(color = colors.danger, textDecoration = TextDecoration.Underline)
                        WordVerdict.Replaced -> SpanStyle(color = colors.gold, fontWeight = FontWeight.Bold)
                    }
                    if (style != null) addStyle(style, range.first, range.last + 1)
                }
            } to ayah
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        texts.forEach { (text: AnnotatedString, ayah) ->
            Text(
                text = text,
                style = arabicStyle.copy(textDirection = TextDirection.Rtl, fontSize = 24.sp, lineHeight = 44.sp),
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${ayah.surah}:${ayah.ayah}", style = type.label, color = colors.inkTertiary)
        }
        Text(
            "${copy.legendOk} / ${copy.legendMissed} / ${copy.legendReplaced}  ·  " +
                if (check.errors == 0) copy.noErrors else copy.summary(check.missed, check.replaced, check.extras.size),
            style = type.callout,
            color = if (check.errors == 0) colors.learned else colors.danger,
        )
        val replaced = check.words.withIndex().filter { it.value.verdict == WordVerdict.Replaced }
        if (replaced.isNotEmpty()) {
            Text(copy.replacedTitle, style = type.label, color = colors.inkSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val words = passage.ayahs.flatMap { it.words.words }
                replaced.forEach { Text("${words[it.index]} → ${it.value.heard}", style = type.callout, color = colors.gold) }
            }
        }
        if (check.extras.isNotEmpty()) {
            Text(copy.extraTitle, style = type.label, color = colors.inkSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                check.extras.forEach { Text(it.word, style = type.callout, color = colors.danger) }
            }
        }
        Text(
            "${copy.model(result.transcript.model)}  ·  ${copy.timing(result.transcript.millis, result.transcript.audioMillis)}",
            style = type.label,
            color = colors.inkSecondary,
        )
        Text(
            "${copy.heardTitle}: ${result.transcript.text.ifBlank { copy.noSpeech }}",
            style = type.label,
            color = colors.inkTertiary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
