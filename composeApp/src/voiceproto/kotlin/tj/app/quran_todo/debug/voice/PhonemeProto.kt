package tj.app.quran_todo.debug.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.voice.LogMel
import tj.app.quran_todo.common.voice.PhoneticCheck
import tj.app.quran_todo.common.voice.SilenceSplitter
import tj.app.quran_todo.voiceproto.PhonemeEngine
import tj.app.quran_todo.voiceproto.WhisperEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What the model heard in a piece of audio: the [script] (collapsed) and how long the mel and the model took. */
class Heard(val script: String, val melMillis: Long, val modelMillis: Long)

/**
 * The phoneme model with the transform in front of it (task F7, prototype). [PhonemeEngine] takes a finished mel; the padding and the
 * log-mel are made here, because [LogMel] lives in `composeApp`.
 */
class PhonemeHearing(private val engine: PhonemeEngine, private val mel: LogMel) {
    /** Hears [pcm] (16 kHz mono). Audio longer than 28 s is cut into pieces of 28 s first (the model takes 30 s at most). Blocking. */
    fun hear(pcm: FloatArray): Heard {
        val piece = PhonemeEngine.PIECE_SECONDS * WhisperEngine.SAMPLE_RATE
        var script = ""
        var melMillis = 0L
        var modelMillis = 0L
        var from = 0
        do {
            val to = minOf(pcm.size, from + piece)
            val padded = FloatArray(to - from + 2 * PAD)
            pcm.copyInto(padded, PAD, from, to)
            val started = System.nanoTime()
            val all = mel.compute(padded)
            val total = mel.frameCount(padded.size)
            // An even number of frames, 3000 at the most: what the model was trained on.
            val frames = minOf(total / 2 * 2, PhonemeEngine.MAX_FRAMES)
            val trimmed = if (frames == total) all else FloatArray(LogMel.Mels * frames).also {
                for (row in 0 until LogMel.Mels) all.copyInto(it, row * frames, row * total, row * total + frames)
            }
            val afterMel = System.nanoTime()
            val heard = if (frames > 0) engine.hear(trimmed, frames) else ""
            val done = System.nanoTime()
            melMillis += (afterMel - started) / 1_000_000
            modelMillis += (done - afterMel) / 1_000_000
            script = PhoneticCheck.collapse(script + heard)
            from = to
        } while (from < pcm.size)
        return Heard(script, melMillis, modelMillis)
    }

    companion object {
        /** 0.4 s of zeros at both ends of the audio, as the model was trained. */
        const val PAD = 6400
    }
}

/** The phonetic script of the words of a passage, or why there is none ([reason]). */
class PhoneticWords(val words: List<String>?, val reason: String?)

/** What the final phoneme check of a recording gave. */
class PhonemeResult(
    val judgement: PhoneticCheck.Judgement,
    val heard: String,
    val audioMillis: Long,
    val melMillis: Long,
    val modelMillis: Long,
    val judgeMillis: Long,
)

/** The phoneme engine of the lab: the model, the transform and the phonetic script of the Quran, loaded once. */
object PhonemeProto {
    private var engine: PhonemeEngine? = null
    private var hearing: PhonemeHearing? = null
    private var scripts: Map<Int, List<String>?>? = null

    @Synchronized
    fun engine(context: Context): PhonemeEngine = engine ?: PhonemeEngine(context.applicationContext).also { engine = it }

    @Synchronized
    fun hearing(context: Context): PhonemeHearing = hearing ?: run {
        val engine = engine(context)
        val bytes = engine.openMelFilters().use { it.readBytes() }
        val filters = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(filters)
        PhonemeHearing(engine, LogMel(filters)).also { hearing = it }
    }

    /** `surah * 1000 + ayah` -> the collapsed words of the ayah; null for an ayah without a script (`!`). */
    @Synchronized
    private fun scripts(context: Context): Map<Int, List<String>?> = scripts ?: run {
        val map = HashMap<Int, List<String>?>(7000)
        engine(context).openPhonetics().bufferedReader().useLines { lines ->
            for (line in lines) {
                val fields = line.split('\t')
                if (fields.size < 3) continue
                val key = fields[0].toInt() * 1000 + fields[1].toInt()
                map[key] = if (fields[2] == "!") null else fields[2].split(' ')
            }
        }
        map.also { scripts = it }
    }

    /**
     * The expected phonetic words of [passage], one to one with its normalized words. An ayah without a script, or one whose word count
     * differs from the app's, makes the passage unfit for this engine.
     */
    fun wordsOf(context: Context, passage: PassageText): PhoneticWords {
        val table = scripts(context)
        val words = ArrayList<String>()
        for (ayah in passage.ayahs) {
            val script = table[ayah.surah * 1000 + ayah.ayah]
                ?: return PhoneticWords(null, "${ayah.surah}:${ayah.ayah}")
            if (script.size != ayah.words.size) return PhoneticWords(null, "${ayah.surah}:${ayah.ayah}")
            words += script
        }
        return PhoneticWords(words, null)
    }

    /** Hears everything in [pcm]: the recording is cut at its pauses (like the whisper check does) and the pieces are heard one by one. */
    fun hearAll(context: Context, pcm: FloatArray): Heard {
        val hearing = hearing(context)
        var script = ""
        var mel = 0L
        var model = 0L
        for (range in SilenceSplitter.split(pcm)) {
            val heard = hearing.hear(pcm.copyOfRange(range.first, range.last + 1))
            script = PhoneticCheck.collapse(script + heard.script)
            mel += heard.melMillis
            model += heard.modelMillis
        }
        return Heard(script, mel, model)
    }

    /** The final check of a whole recording against [words]. Blocking: run it off the main thread. */
    suspend fun check(context: Context, words: List<String>, pcm: FloatArray): PhonemeResult = withContext(Dispatchers.Default) {
        val heard = hearAll(context, pcm)
        judge(words, heard, pcm.size)
    }

    fun judge(words: List<String>, heard: Heard, samples: Int): PhonemeResult {
        val started = System.nanoTime()
        val judgement = PhoneticCheck.judge(words, heard.script)
        return PhonemeResult(
            judgement = judgement,
            heard = heard.script,
            audioMillis = samples * 1000L / WhisperEngine.SAMPLE_RATE,
            melMillis = heard.melMillis,
            modelMillis = heard.modelMillis,
            judgeMillis = (System.nanoTime() - started) / 1_000_000,
        )
    }
}
