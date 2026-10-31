package tj.app.quran_todo.common.voice

import kotlin.math.sqrt

/**
 * Cuts a recording at its pauses (task F7, prototype). The model that writes the speech down was trained on single ayahs: given one long
 * recording it runs the words of two ayahs together and invents words at the start of an ayah. Given the pieces between the pauses it
 * makes far fewer false errors (`scripts/voice_eval.py`: 2.0 -> 0.65 per passage). The same rules as `split_at_silences` of that script.
 *
 * A pause is a stretch of at least [MinSilenceFrames] frames of 20 ms whose energy is below [QuietShare] of the loud part of the
 * recording (the 90th percentile of the frames); it is cut in its middle. A piece shorter than [MinChunkSeconds] is joined to the next one.
 */
object SilenceSplitter {
    const val SampleRate = 16_000
    const val FrameSize = 320
    const val MinSilenceFrames = 12
    const val MinChunkSeconds = 2.0
    const val QuietShare = 0.03

    /** The sample ranges (start inclusive, end exclusive) of the pieces; one piece for a recording without pauses. */
    fun split(samples: FloatArray): List<IntRange> {
        val frames = samples.size / FrameSize
        if (frames == 0) return listOf(0 until samples.size)
        val rms = DoubleArray(frames) { frame ->
            var sum = 0.0
            for (i in frame * FrameSize until (frame + 1) * FrameSize) sum += samples[i].toDouble() * samples[i]
            sqrt(sum / FrameSize)
        }
        val loud = rms.sortedArray()[(frames * 0.9).toInt()]
        val quiet = BooleanArray(frames) { rms[it] < QuietShare * loud }
        val cuts = ArrayList<Int>()
        cuts += 0
        var i = 0
        while (i < frames) {
            if (!quiet[i]) {
                i++
                continue
            }
            var j = i
            while (j < frames && quiet[j]) j++
            if (j - i >= MinSilenceFrames && i > 0 && j < frames) cuts += (i + j) / 2
            i = j
        }
        cuts += frames
        val pieces = ArrayList<IntArray>()
        var start = cuts[0]
        for (k in 1 until cuts.size) {
            if ((cuts[k] - start) * FrameSize / SampleRate.toDouble() >= MinChunkSeconds || k == cuts.size - 1) {
                pieces += intArrayOf(start, cuts[k])
                start = cuts[k]
            }
        }
        if (pieces.size > 1 && (pieces.last()[1] - pieces.last()[0]) * FrameSize / SampleRate.toDouble() < MinChunkSeconds) {
            val last = pieces.removeAt(pieces.size - 1)
            pieces.last()[1] = last[1]
        }
        return pieces.map { (it[0] * FrameSize) until (it[1] * FrameSize) }
    }
}
