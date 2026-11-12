package tj.app.quran_todo.debug.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.voice.PhoneticCheck
import tj.app.quran_todo.common.voice.SilenceSplitter
import tj.app.quran_todo.common.voice.WordVerdict
import tj.app.quran_todo.voiceproto.Wav
import tj.app.quran_todo.voiceproto.WhisperEngine

/**
 * What the live check shows: the number of words the reciter has [shown] so far and their [verdicts], the [heard] script, and the
 * milliseconds of the last tick (the mel, the model and the matching apart) and of the slowest one.
 */
class LiveState(
    val shown: Int = 0,
    val verdicts: List<WordVerdict> = emptyList(),
    val heard: String = "",
    val melMillis: Long = 0,
    val modelMillis: Long = 0,
    val followMillis: Long = 0,
    val maxTickMillis: Long = 0,
    val ticks: Int = 0,
    val audioSeconds: Double = 0.0,
    val error: String? = null,
)

/**
 * The live check of a recitation (task F7, prototype): while the person recites, about once a second the audio recorded so far is
 * heard by the phoneme model and matched with the beginning of the passage ([PhoneticCheck.follow]), so the words can be revealed as
 * they are said. A run never overlaps the next one: if it takes longer than a second the next starts right after it.
 *
 * The audio is kept as *finished pieces* (cut at a pause, heard once, the script cached) and an *open tail* after the last of them that
 * is heard again on every tick. A tail longer than [MaxTailSeconds] is finished by force. The number of shown words never goes down.
 * [finish] hears everything once more and judges the whole passage.
 */
class LiveRecitation(
    private val hearing: PhonemeHearing,
    private val words: List<String>,
    private val snapshot: () -> ShortArray,
) {
    private class Piece(val end: Int, val script: String)

    private val finished = ArrayList<Piece>()
    private var finishedEnd = 0
    private var job: Job? = null
    private var maxTick = 0L
    private var ticks = 0

    private val mutableState = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = mutableState

    fun start(scope: CoroutineScope) {
        check(job == null) { "already started" }
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val started = System.nanoTime()
                try {
                    tick()
                } catch (e: Exception) {
                    mutableState.value = mutableState.value.let { LiveState(it.shown, it.verdicts, it.heard, error = e.message ?: e.toString()) }
                }
                val spent = (System.nanoTime() - started) / 1_000_000
                delay((TickMillis - spent).coerceAtLeast(0))
            }
        }
    }

    /**
     * Stops the ticks (waits for the one that runs), hears the whole recording [pcm] once more (the finished pieces are kept, the rest
     * is cut at its pauses and heard) and judges the passage.
     */
    suspend fun finish(pcm: FloatArray): PhonemeResult = withContext(Dispatchers.Default) {
        job?.cancelAndJoin()
        job = null
        var script = finished.joinToString("") { it.script }
        var mel = 0L
        var model = 0L
        val rest = pcm.copyOfRange(minOf(finishedEnd, pcm.size), pcm.size)
        for (range in SilenceSplitter.split(rest)) {
            val heard = hearing.hear(rest.copyOfRange(range.first, range.last + 1))
            script += heard.script
            mel += heard.melMillis
            model += heard.modelMillis
        }
        val result = PhonemeProto.judge(words, Heard(PhoneticCheck.collapse(script), mel, model), pcm.size)
        mutableState.value = LiveState(
            shown = words.size,
            verdicts = result.judgement.verdicts,
            heard = result.heard,
            melMillis = mutableState.value.melMillis,
            modelMillis = mutableState.value.modelMillis,
            followMillis = mutableState.value.followMillis,
            maxTickMillis = maxTick,
            ticks = ticks,
            audioSeconds = pcm.size / WhisperEngine.SAMPLE_RATE.toDouble(),
        )
        result
    }

    /** Stops the ticks without a result. */
    suspend fun cancel() {
        job?.cancelAndJoin()
        job = null
    }

    private fun tick() {
        val started = System.nanoTime()
        val pcm = Wav.toFloat(snapshot())
        if (pcm.size - finishedEnd < MinTailSamples) return
        var melMillis = 0L
        var modelMillis = 0L
        // Finish the pieces before the last pause; what follows it is the open tail.
        var tail = pcm.copyOfRange(finishedEnd, pcm.size)
        fun closePiece(piece: FloatArray) {
            val heard = hearing.hear(piece)
            melMillis += heard.melMillis
            modelMillis += heard.modelMillis
            finishedEnd += piece.size
            finished += Piece(finishedEnd, heard.script)
        }
        val pieces = SilenceSplitter.split(tail)
        for (piece in pieces.dropLast(1)) closePiece(tail.copyOfRange(piece.first, piece.last + 1))
        tail = tail.copyOfRange(pieces.last().first, tail.size)
        val forced = MaxTailSeconds * WhisperEngine.SAMPLE_RATE
        while (tail.size > forced) {
            closePiece(tail.copyOfRange(0, forced))
            tail = tail.copyOfRange(forced, tail.size)
        }
        var script = finished.joinToString("") { it.script }
        if (tail.size >= MinTailSamples) {
            val heard = hearing.hear(tail)
            melMillis += heard.melMillis
            modelMillis += heard.modelMillis
            script += heard.script
        }
        script = PhoneticCheck.collapse(script)
        val followStarted = System.nanoTime()
        val progress = PhoneticCheck.follow(words, script)
        val followMillis = (System.nanoTime() - followStarted) / 1_000_000
        maxTick = maxOf(maxTick, (System.nanoTime() - started) / 1_000_000)
        ticks++
        val before = mutableState.value
        // The shown words only grow: a later, shorter reading does not hide what was shown.
        val grown = progress.shown >= before.shown
        mutableState.value = LiveState(
            shown = if (grown) progress.shown else before.shown,
            verdicts = if (grown) progress.verdicts else before.verdicts,
            heard = script,
            melMillis = melMillis,
            modelMillis = modelMillis,
            followMillis = followMillis,
            maxTickMillis = maxTick,
            ticks = ticks,
            audioSeconds = pcm.size / WhisperEngine.SAMPLE_RATE.toDouble(),
        )
    }

    companion object {
        const val TickMillis = 1000L

        /** An open tail longer than this is finished by force (the model takes 30 s at most). */
        const val MaxTailSeconds = 25

        /** Less than half a second of new audio is not worth a run. */
        const val MinTailSamples = WhisperEngine.SAMPLE_RATE / 2
    }
}
