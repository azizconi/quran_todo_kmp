package tj.app.quran_todo.voiceproto

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * The phoneme model on the phone (task F7, prototype): Hisnlabs/tajweed-v2, a CTC model that writes a recitation down in the Quran
 * phonetic script, one sign per sound (Apache-2.0, int8 ONNX, 23 MB). It is fast enough to run again every second of a recitation.
 * The file lies in `files/voice/` next to the whisper models; [download] fetches it. One session stays loaded; [hear] blocks (call it
 * off the main thread).
 *
 * This module does not know the log-mel transform (that is in `composeApp`): [hear] takes the finished mel. The mel filters and the
 * phonetic script of the Quran are assets of this module, see [openMelFilters] and [openPhonetics].
 */
class PhonemeEngine(private val context: Context) {
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null

    fun modelFile(): File = File(WhisperEngine.voiceDir(context), MODEL_FILE)

    fun isAvailable(): Boolean = modelFile().let { it.isFile && it.length() > 0 }

    /** The Whisper mel filter bank: float32 little-endian, 80 rows x 201 columns, row-major. */
    fun openMelFilters(): InputStream = context.assets.open("voice/mel-80.bin")

    /** The phonetic script of the Quran: one line `surah<TAB>ayah<TAB>words` per ayah (`!` instead of words: no script). */
    fun openPhonetics(): InputStream = context.assets.open("voice/quran-phonetics.tsv")

    /**
     * Downloads the model into `files/voice/` and checks its SHA-256; [onProgress] gets 0..1 (or -1 when the size is unknown).
     * Blocking. A file with another hash is deleted and an [IOException] is thrown.
     */
    fun download(onProgress: (Float) -> Unit) {
        val part = File(modelFile().parentFile, "$MODEL_FILE.part")
        try {
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            try {
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val block = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(block)
                            if (read < 0) break
                            output.write(block, 0, read)
                            digest.update(block, 0, read)
                            done += read
                            onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != MODEL_SHA256) throw IOException("SHA-256 mismatch: $hash")
            } finally {
                connection.disconnect()
            }
            close()
            val target = modelFile()
            target.delete()
            if (!part.renameTo(target)) throw IOException("cannot move the model into place")
            onProgress(1f)
        } finally {
            part.delete()
        }
    }

    /**
     * The sounds heard in [mel], [frames] frames of the 80 mel rows (row-major, 80 x [frames]; at most 3000 frames = 30 s). The signs
     * come as the model writes them, not collapsed. A longer mel is cut into pieces of 28 s.
     */
    @Synchronized
    fun hear(mel: FloatArray, frames: Int): String {
        require(mel.size >= MELS * frames) { "mel is ${mel.size} values, expected ${MELS * frames}" }
        if (frames <= 0) return ""
        if (frames <= MAX_FRAMES) return run(mel, frames)
        val out = StringBuilder()
        var from = 0
        while (from < frames) {
            val count = minOf(PIECE_FRAMES, frames - from)
            val piece = FloatArray(MELS * count)
            for (row in 0 until MELS) mel.copyInto(piece, row * count, row * frames + from, row * frames + from + count)
            out.append(run(piece, count))
            from += count
        }
        return out.toString()
    }

    @Synchronized
    fun close() {
        session?.close()
        session = null
    }

    private fun ensureLoaded(): Pair<OrtEnvironment, OrtSession> {
        val env = environment ?: OrtEnvironment.getEnvironment().also { environment = it }
        val loaded = session ?: run {
            check(isAvailable()) { "no model in ${modelFile().parent}" }
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(WhisperEngine.defaultThreads())
            env.createSession(modelFile().absolutePath, options).also { session = it }
        }
        return env to loaded
    }

    private fun run(mel: FloatArray, frames: Int): String {
        val (env, loaded) = ensureLoaded()
        val direct = ByteBuffer.allocateDirect(MELS * frames * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        direct.put(mel, 0, MELS * frames).rewind()
        OnnxTensor.createTensor(env, direct, longArrayOf(1, MELS.toLong(), frames.toLong())).use { melTensor ->
            OnnxTensor.createTensor(env, longArrayOf(0L)).use { riwaya ->
                loaded.run(mapOf("mel" to melTensor, "riwaya" to riwaya)).use { result ->
                    val logprobs = result.get("logprobs").get() as OnnxTensor
                    val steps = logprobs.info.shape[1].toInt()
                    val classes = logprobs.info.shape[2].toInt()
                    val values = logprobs.floatBuffer
                    val out = StringBuilder()
                    var previous = 0
                    for (t in 0 until steps) {
                        var best = 0
                        var bestValue = values.get(t * classes)
                        for (c in 1 until classes) {
                            val value = values.get(t * classes + c)
                            if (value > bestValue) {
                                bestValue = value
                                best = c
                            }
                        }
                        if (best != 0 && best != previous && best <= PHONEMES.size) out.append(PHONEMES[best - 1])
                        previous = best
                    }
                    return out.toString()
                }
            }
        }
    }

    companion object {
        const val MODEL_NAME = "tajweed-v2-int8"
        const val MODEL_FILE = "tajweed-v2-int8.onnx"
        const val MODEL_URL = "https://huggingface.co/Hisnlabs/tajweed-v2/resolve/main/onnx/model-int8.onnx"
        const val MODEL_SHA256 = "79cca1a2fc7ef81e325a3071a5f26cfec69203ef2c63177549065cdc1022c373"
        const val MODEL_MEGABYTES = 23

        /** The model takes 30 s at most (3000 mel frames). */
        const val MAX_FRAMES = 3000

        /** Longer audio is cut into pieces of 28 s before the model. */
        const val PIECE_FRAMES = 2800
        const val PIECE_SECONDS = 28

        private const val MELS = 80

        /**
         * The 42 signs of the model in the order of its config.json (output class i is `PHONEMES[i - 1]`, class 0 is the CTC blank). Built
         * from code points so that no editor can mangle them.
         */
        val PHONEMES: List<Char> = intArrayOf(
            0x0621, 0x0628, 0x062A, 0x062B, 0x062C, 0x062D, 0x062E, 0x062F, 0x0630, 0x0631, 0x0632, 0x0633, 0x0634, 0x0635,
            0x0636, 0x0637, 0x0638, 0x0639, 0x063A, 0x0641, 0x0642, 0x0643, 0x0644, 0x0645, 0x0646, 0x0647, 0x0648, 0x064A,
            0x0627,
            0x06E6, 0x06E5, 0x064E, 0x064F, 0x0650, 0x06EA, 0x0640, 0x0672, 0x0687, 0x06BA, 0x06FE, 0x06DC, 0x0619,
        ).map { it.toChar() }
    }
}
