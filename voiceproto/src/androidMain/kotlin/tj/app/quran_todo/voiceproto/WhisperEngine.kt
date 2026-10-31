package tj.app.quran_todo.voiceproto

import android.content.Context
import java.io.File

/** The JNI of `whisper_jni.cpp`. */
internal object WhisperNative {
    init {
        System.loadLibrary("voiceproto")
    }

    external fun init(path: String): Long
    external fun free(handle: Long)
    external fun transcribe(handle: Long, samples: FloatArray, threads: Int, prompt: String?): String
    external fun systemInfo(): String
}

/** What one recognition gave: the [text] of the model and how long it took ([millis]) for [audioMillis] of audio. */
class Transcript(val text: String, val millis: Long, val audioMillis: Long, val model: String) {
    /** Time of the analysis divided by the length of the audio: below 1 the analysis is faster than the speech. */
    val realtimeFactor: Double get() = if (audioMillis == 0L) 0.0 else millis.toDouble() / audioMillis
}

/**
 * Whisper on the phone. The models are ggml files that are pushed to `files/voice/` of the app (`adb push` + `run-as`, see
 * docs/redesign/API.md, "Voice: prototype (F7)"): there is no hosting for them yet. A file is named `ggml-quran-<quantization>.bin`.
 * One model stays loaded; [transcribe] blocks (call it off the main thread).
 */
class WhisperEngine(private val context: Context) {
    private var handle = 0L
    private var loaded: String? = null

    /** The models that lie in `files/voice/`: `q5_0`, `q8_0`, ... */
    fun availableModels(): List<String> = modelFiles().map { it.first }

    fun systemInfo(): String = WhisperNative.systemInfo()

    @Synchronized
    fun transcribe(pcm: FloatArray, model: String, threads: Int = defaultThreads(), prompt: String? = null): Transcript {
        ensureLoaded(model)
        val started = System.nanoTime()
        val text = WhisperNative.transcribe(handle, pcm, threads, prompt)
        val millis = (System.nanoTime() - started) / 1_000_000
        return Transcript(text.trim(), millis, pcm.size * 1000L / SAMPLE_RATE, model)
    }

    @Synchronized
    fun close() {
        if (handle != 0L) WhisperNative.free(handle)
        handle = 0L
        loaded = null
    }

    private fun ensureLoaded(model: String) {
        if (handle != 0L && loaded == model) return
        close()
        val file = modelFiles().firstOrNull { it.first == model }?.second
            ?: throw IllegalStateException("no model '$model' in ${voiceDir(context)}")
        handle = WhisperNative.init(file.absolutePath)
        check(handle != 0L) { "whisper could not load ${file.name}" }
        loaded = model
    }

    private fun modelFiles(): List<Pair<String, File>> =
        voiceDir(context).listFiles().orEmpty()
            .filter { it.name.startsWith("ggml-quran-") && it.name.endsWith(".bin") }
            .map { it.name.removePrefix("ggml-quran-").removeSuffix(".bin") to it }
            .sortedBy { it.first }

    companion object {
        /** Whisper wants 16 kHz mono. */
        const val SAMPLE_RATE = 16_000

        fun voiceDir(context: Context): File = File(context.filesDir, "voice").also { it.mkdirs() }

        fun samplesDir(context: Context): File = File(voiceDir(context), "samples").also { it.mkdirs() }

        /** Four threads: the big cores of a phone; more only fight for the memory bus. */
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
