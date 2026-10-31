package tj.app.quran_todo.voiceproto

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records 16 kHz mono PCM (16 bit) into memory: what whisper wants, not the AAC of the "Your voice" recorder. [start] needs the
 * RECORD_AUDIO permission (the app has it). One passage is a few minutes at the most, so a growing byte buffer is enough.
 */
class PcmRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val bytes = ByteArrayOutputStream()

    @Volatile
    private var running = false

    /** The level of the last block, 0..1: for a meter. */
    @Volatile
    var level: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minimum = AudioRecord.getMinBufferSize(WhisperEngine.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            WhisperEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minimum * 4,
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            return false
        }
        bytes.reset()
        record = audio
        running = true
        audio.startRecording()
        thread = Thread({
            val block = ByteArray(minimum)
            while (running) {
                val read = audio.read(block, 0, block.size)
                if (read > 0) {
                    synchronized(bytes) { bytes.write(block, 0, read) }
                    level = peak(block, read)
                }
            }
        }, "pcm-recorder").also { it.start() }
        return true
    }

    /** Stops and returns what was recorded as 16 bit samples. */
    fun stop(): ShortArray {
        running = false
        thread?.join()
        thread = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        level = 0f
        val data = synchronized(bytes) { bytes.toByteArray() }
        val shorts = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun peak(block: ByteArray, length: Int): Float {
        var max = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (block[i].toInt() and 0xff) or (block[i + 1].toInt() shl 8)
            max = maxOf(max, kotlin.math.abs(sample.toShort().toInt()))
            i += 2
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }
}

/** 16 bit PCM wav files: reading (any wav that is 16 bit; mono is taken as is, stereo is mixed down) and writing. */
object Wav {
    fun toFloat(samples: ShortArray): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }

    fun write(file: File, samples: ShortArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(WhisperEngine.SAMPLE_RATE).putInt(WhisperEngine.SAMPLE_RATE * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.capacity())
        file.outputStream().use {
            it.write(header.array())
            it.write(data.array())
        }
    }

    /** Samples of a 16 bit PCM wav as floats; null when the file is not one (or is not 16 kHz). */
    fun read(file: File): FloatArray? {
        val all = file.readBytes()
        if (all.size < 44 || String(all, 0, 4) != "RIFF" || String(all, 8, 4) != "WAVE") return null
        val buffer = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)
        var position = 12
        var channels = 1
        var rate = 0
        var bits = 16
        while (position + 8 <= all.size) {
            val id = String(all, position, 4)
            val size = buffer.getInt(position + 4)
            val body = position + 8
            if (id == "fmt ") {
                channels = buffer.getShort(body + 2).toInt()
                rate = buffer.getInt(body + 4)
                bits = buffer.getShort(body + 14).toInt()
            } else if (id == "data") {
                if (bits != 16 || rate != WhisperEngine.SAMPLE_RATE || channels !in 1..2) return null
                val end = minOf(all.size, body + size)
                val frames = (end - body) / (2 * channels)
                return FloatArray(frames) { frame ->
                    var sum = 0f
                    for (c in 0 until channels) sum += buffer.getShort(body + (frame * channels + c) * 2) / 32768f
                    sum / channels
                }
            }
            position = body + size + (size and 1)
        }
        return null
    }
}
