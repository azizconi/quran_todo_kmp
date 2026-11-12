package tj.app.quran_todo.common.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * The log-mel spectrogram that Whisper-family models take (task F7, prototype): what faster-whisper's
 * `FeatureExtractor(feature_size=80)(x, padding=0)` gives. 16 kHz audio, a periodic Hann window of 400 samples, hop 160, the signal
 * reflected by 200 samples at both ends, power spectrum of 201 bins, [filters] (80 x 201, row-major) on top, `log10`, the floor at
 * 8 below the loudest value, then `(x + 4) / 4`. The last frame is dropped, so the result has `samples.size / 160` frames.
 *
 * The caller pads the audio and trims the frames the model wants (see the phoneme engine of the lab); this class is only the transform.
 * The FFT is a Stockham one of mixed radix (400 = 5 x 5 x 2 x 2 x 2 x 2); two real frames go through one complex transform.
 */
class LogMel(private val filters: FloatArray) {
    init {
        require(filters.size == Mels * Bins) { "mel filters must be $Mels x $Bins" }
    }

    // The non-zero range of every filter row (the triangles are narrow): the zeros are skipped, the sum is the same.
    private val rowStart = IntArray(Mels)
    private val rowEnd = IntArray(Mels)

    init {
        for (row in 0 until Mels) {
            var first = Bins
            var last = -1
            for (bin in 0 until Bins) {
                if (filters[row * Bins + bin] != 0f) {
                    if (first == Bins) first = bin
                    last = bin
                }
            }
            rowStart[row] = first
            rowEnd[row] = last + 1
        }
    }

    /** Samples of the Hann window multiplied in before the transform. */
    private val window = DoubleArray(Fft) { 0.5 - 0.5 * cos(2.0 * PI * it / Fft) }

    private class Stage(val radix: Int, val m: Int, val s: Int, val twiddleRe: DoubleArray, val twiddleIm: DoubleArray)

    private val stages: List<Stage> = run {
        var n = Fft
        var s = 1
        Radices.map { p ->
            val m = n / p
            val re = DoubleArray(m * p)
            val im = DoubleArray(m * p)
            for (j in 0 until m) for (r in 0 until p) {
                val angle = -2.0 * PI * j * r / n
                re[j * p + r] = cos(angle)
                im[j * p + r] = sin(angle)
            }
            Stage(p, m, s, re, im).also {
                n = m
                s *= p
            }
        }
    }

    private val rootRe = DoubleArray(5) { cos(-2.0 * PI * it / 5) }
    private val rootIm = DoubleArray(5) { sin(-2.0 * PI * it / 5) }

    /** The number of frames [compute] gives for [sampleCount] samples. */
    fun frameCount(sampleCount: Int): Int = sampleCount / Hop

    /** The mel spectrogram of [samples] (16 kHz): [Mels] rows of [frameCount] values, row-major. */
    fun compute(samples: FloatArray): FloatArray {
        val frames = frameCount(samples.size)
        if (frames == 0) return FloatArray(0)
        // Frames of the padded signal: one more than we keep (the last one is dropped), but the loudest value is taken over the kept ones
        // only, like the reference does after the drop.
        val result = FloatArray(Mels * frames)
        val padded = DoubleArray(samples.size + Fft)
        val last = samples.size - 1
        for (i in padded.indices) {
            var index = i - Fft / 2
            if (index < 0) index = -index
            if (index > last) index = 2 * last - index
            padded[i] = samples[index.coerceIn(0, last)].toDouble()
        }
        var a = DoubleArray(2 * Fft)
        var b = DoubleArray(2 * Fft)
        val powerA = DoubleArray(Bins)
        val powerB = DoubleArray(Bins)
        var peak = -Double.MAX_VALUE
        val log = DoubleArray(Mels * frames)
        var frame = 0
        while (frame < frames) {
            val pair = frame + 1 < frames
            val offsetA = frame * Hop
            val offsetB = (frame + 1) * Hop
            for (n in 0 until Fft) {
                a[2 * n] = padded[offsetA + n] * window[n]
                a[2 * n + 1] = if (pair) padded[offsetB + n] * window[n] else 0.0
            }
            val out = fft(a, b)
            if (out === b) {
                val t = a
                a = b
                b = t
            }
            // Z = X1 + i X2 (both real signals): X1[k] = (Z[k] + conj Z[N-k]) / 2, X2[k] = (Z[k] - conj Z[N-k]) / 2i.
            for (k in 0 until Bins) {
                val nk = (Fft - k) % Fft
                val zr = a[2 * k]
                val zi = a[2 * k + 1]
                val cr = a[2 * nk]
                val ci = -a[2 * nk + 1]
                val x1r = (zr + cr) * 0.5
                val x1i = (zi + ci) * 0.5
                val x2r = (zi - ci) * 0.5
                val x2i = -(zr - cr) * 0.5
                powerA[k] = x1r * x1r + x1i * x1i
                powerB[k] = x2r * x2r + x2i * x2i
            }
            for (row in 0 until Mels) {
                var sumA = 0.0
                var sumB = 0.0
                val base = row * Bins
                for (bin in rowStart[row] until rowEnd[row]) {
                    val f = filters[base + bin].toDouble()
                    sumA += f * powerA[bin]
                    sumB += f * powerB[bin]
                }
                val la = log10(max(sumA, 1e-10))
                log[row * frames + frame] = la
                if (la > peak) peak = la
                if (pair) {
                    val lb = log10(max(sumB, 1e-10))
                    log[row * frames + frame + 1] = lb
                    if (lb > peak) peak = lb
                }
            }
            frame += 2
        }
        val floor = peak - 8.0
        for (i in log.indices) result[i] = ((max(log[i], floor) + 4.0) / 4.0).toFloat()
        return result
    }

    /** Complex FFT of [Fft] points in place of interleaved (re, im) data; returns the array that holds the result ([x] or [y]). */
    private fun fft(x: DoubleArray, y: DoubleArray): DoubleArray {
        var src = x
        var dst = y
        for (stage in stages) {
            val p = stage.radix
            val m = stage.m
            val s = stage.s
            for (j in 0 until m) {
                for (k in 0 until s) {
                    val from = k + s * j
                    val step = s * m
                    val to = k + s * p * j
                    if (p == 2) {
                        val ar = src[2 * from]
                        val ai = src[2 * from + 1]
                        val br = src[2 * (from + step)]
                        val bi = src[2 * (from + step) + 1]
                        dst[2 * to] = ar + br
                        dst[2 * to + 1] = ai + bi
                        val dr = ar - br
                        val di = ai - bi
                        val wr = stage.twiddleRe[j * 2 + 1]
                        val wi = stage.twiddleIm[j * 2 + 1]
                        dst[2 * (to + s)] = dr * wr - di * wi
                        dst[2 * (to + s) + 1] = dr * wi + di * wr
                    } else {
                        for (r in 0 until 5) {
                            var sr = 0.0
                            var si = 0.0
                            for (q in 0 until 5) {
                                val index = 2 * (from + q * step)
                                val rot = (q * r) % 5
                                val cr = rootRe[rot]
                                val ci = rootIm[rot]
                                val vr = src[index]
                                val vi = src[index + 1]
                                sr += vr * cr - vi * ci
                                si += vr * ci + vi * cr
                            }
                            val wr = stage.twiddleRe[j * 5 + r]
                            val wi = stage.twiddleIm[j * 5 + r]
                            dst[2 * (to + r * s)] = sr * wr - si * wi
                            dst[2 * (to + r * s) + 1] = sr * wi + si * wr
                        }
                    }
                }
            }
            val t = src
            src = dst
            dst = t
        }
        return src
    }

    companion object {
        const val Mels = 80
        const val Bins = 201
        const val Fft = 400
        const val Hop = 160
        private val Radices = intArrayOf(5, 5, 2, 2, 2, 2)
    }
}
