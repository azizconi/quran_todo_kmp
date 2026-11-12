package tj.app.quran_todo.common.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [LogMel] against `mel_case.bin`, the input that faster-whisper's feature extractor makes for `mel_case.wav` (3 s, 16 kHz). */
class LogMelTest {
    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("voice/$name")!!.use { it.readBytes() }

    private fun floats(bytes: ByteArray): FloatArray {
        val out = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }

    private val filters: FloatArray by lazy {
        // The filters are the asset of the voiceproto module; the test reads the copy that lies next to it in the repository.
        val file = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "voiceproto/src/androidMain/assets/voice/mel-80.bin") }.first { it.isFile }
        floats(file.readBytes())
    }

    /** What the engine does around [LogMel]: 0.4 s of zeros at both ends, then an even number of frames, 3000 at the most. */
    private fun melOf(samples: FloatArray, mel: LogMel): Pair<FloatArray, Int> {
        val pad = 6400
        val padded = FloatArray(samples.size + 2 * pad)
        samples.copyInto(padded, pad)
        val all = mel.compute(padded)
        val total = mel.frameCount(padded.size)
        val frames = minOf(total / 2 * 2, 3000)
        val out = FloatArray(LogMel.Mels * frames)
        for (row in 0 until LogMel.Mels) all.copyInto(out, row * frames, row * total, row * total + frames)
        return out to frames
    }

    @Test
    fun matchesTheReference() {
        val wav = resource("mel_case.wav")
        val buffer = ByteBuffer.wrap(wav, 44, wav.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray((wav.size - 44) / 2) { buffer.getShort(44 + it * 2) / 32768f }
        val expected = floats(resource("mel_case.bin"))
        val (mel, frames) = melOf(samples, LogMel(filters))
        assertEquals(380, frames)
        assertEquals(expected.size, mel.size)
        var worst = 0f
        for (i in expected.indices) worst = maxOf(worst, abs(expected[i] - mel[i]))
        println("LogMel max abs diff: $worst")
        assertTrue(worst < 1e-3f, "max abs diff $worst")
    }

    @Test
    fun timing() {
        val mel = LogMel(filters)
        val noise = FloatArray(28 * 16_000) { Random(it).nextFloat() * 0.2f - 0.1f }
        repeat(5) { mel.compute(noise) }
        val runs = 20
        val started = System.nanoTime()
        repeat(runs) { mel.compute(noise) }
        println("LogMel 28 s of audio: %.1f ms per run (JVM)".format((System.nanoTime() - started) / 1e6 / runs))
    }
}
