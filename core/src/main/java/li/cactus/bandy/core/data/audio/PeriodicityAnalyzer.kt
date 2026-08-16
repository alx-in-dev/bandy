package li.cactus.bandy.core.data.audio

import kotlin.math.roundToInt
import kotlin.math.sqrt
import li.cactus.bandy.core.domain.model.PeriodicityAnalysis

private const val ENVELOPE_RATE_HZ = 1000f
private const val MIN_PERIOD_MS = 5f
private const val MAX_PERIOD_MS = 1500f
private const val PEAK_THRESHOLD_STDDEV = 1.0
private const val PEAK_MIN_GAP_FACTOR = 0.6

/**
 * Finds a dominant repetition period in a band-limited signal via FFT-based autocorrelation of
 * its envelope, then locates individual repeating impulses (for on-screen markers) and can build
 * a synchronous-averaged single-cycle template (repeating noise cancels, the periodic signal —
 * e.g. an engine knock — accumulates).
 */
internal class PeriodicityAnalyzer {

    fun analyze(bandLimitedSamples: ShortArray, sampleRate: Int): PeriodicityAnalysis {
        val blockSamples = (sampleRate / ENVELOPE_RATE_HZ).roundToInt().coerceAtLeast(1)
        val envelope = extractEnvelope(bandLimitedSamples, blockSamples)
        val envelopeRateHz = sampleRate / blockSamples.toFloat()

        val minLag = (MIN_PERIOD_MS / 1000f * envelopeRateHz).roundToInt().coerceAtLeast(1)
        val maxLag = (MAX_PERIOD_MS / 1000f * envelopeRateHz).roundToInt().coerceAtMost(envelope.size - 1)

        if (envelope.size < 8 || maxLag <= minLag) {
            return PeriodicityAnalysis(0f, 0f, emptyList(), envelope, 1000f / envelopeRateHz)
        }

        val autocorr = fftAutocorrelation(envelope)
        val r0 = autocorr[0].takeIf { it > 1e-9 }
            ?: return PeriodicityAnalysis(0f, 0f, emptyList(), envelope, 1000f / envelopeRateHz)

        var bestLag = minLag
        var bestVal = autocorr[minLag]
        for (lag in minLag..maxLag) {
            if (autocorr[lag] > bestVal) {
                bestVal = autocorr[lag]
                bestLag = lag
            }
        }
        val periodMs = bestLag / envelopeRateHz * 1000f
        val confidence = (bestVal / r0).toFloat().coerceIn(0f, 1f)
        val impulses = findImpulsePeaks(envelope, bestLag, envelopeRateHz)

        return PeriodicityAnalysis(periodMs, confidence, impulses, envelope, 1000f / envelopeRateHz)
    }

    /** Averages a [periodMs]-long window of [bandLimitedSamples] centered on each impulse; non-periodic content cancels out. */
    fun buildSynchronousAverage(
        bandLimitedSamples: ShortArray,
        sampleRate: Int,
        periodMs: Float,
        impulseTimestampsMs: List<Float>,
    ): ShortArray {
        val periodSamples = (periodMs / 1000f * sampleRate).roundToInt().coerceAtLeast(2)
        val half = periodSamples / 2
        val sum = DoubleArray(periodSamples)
        var count = 0

        for (tMs in impulseTimestampsMs) {
            val center = (tMs / 1000f * sampleRate).roundToInt()
            val start = center - half
            val end = start + periodSamples
            if (start < 0 || end > bandLimitedSamples.size) continue
            for (i in 0 until periodSamples) sum[i] += bandLimitedSamples[start + i] / 32768.0
            count++
        }
        if (count == 0) return ShortArray(0)

        return ShortArray(periodSamples) { i ->
            val v = (sum[i] / count).coerceIn(-1.0, 1.0)
            (v * 32767).toInt().toShort()
        }
    }

    private fun extractEnvelope(samples: ShortArray, blockSamples: Int): FloatArray {
        val count = samples.size / blockSamples
        if (count <= 0) return FloatArray(0)
        return FloatArray(count) { b ->
            var sumSq = 0.0
            val start = b * blockSamples
            for (i in 0 until blockSamples) {
                val s = samples[start + i] / 32768.0
                sumSq += s * s
            }
            sqrt(sumSq / blockSamples).toFloat()
        }
    }

    /** Linear autocorrelation via Wiener-Khinchin: FFT -> power spectrum -> inverse FFT. */
    private fun fftAutocorrelation(envelope: FloatArray): DoubleArray {
        var n = 1
        while (n < envelope.size * 2) n = n shl 1

        val fft = FftProcessor(n)
        val data = DoubleArray(n)
        val mean = envelope.average()
        for (i in envelope.indices) data[i] = envelope[i] - mean

        fft.forward(data)
        val halfSize = fft.halfSize
        val power = DoubleArray(n)
        power[0] = data[0] * data[0]
        power[1] = data[1] * data[1]
        for (k in 1 until halfSize) {
            val re = data[2 * k]
            val im = data[2 * k + 1]
            power[2 * k] = re * re + im * im
        }
        fft.inverse(power)
        return power
    }

    private fun findImpulsePeaks(envelope: FloatArray, minSpacingSamples: Int, envelopeRateHz: Float): List<Float> {
        if (envelope.size < 3) return emptyList()
        val mean = envelope.average()
        val variance = envelope.sumOf { (it - mean) * (it - mean) } / envelope.size
        val threshold = mean + sqrt(variance) * PEAK_THRESHOLD_STDDEV
        val minGap = (minSpacingSamples * PEAK_MIN_GAP_FACTOR).roundToInt().coerceAtLeast(1)

        val peaks = mutableListOf<Int>()
        for (i in 1 until envelope.size - 1) {
            if (envelope[i] < threshold) continue
            if (envelope[i] < envelope[i - 1] || envelope[i] < envelope[i + 1]) continue

            val last = peaks.lastOrNull()
            when {
                last == null || i - last >= minGap -> peaks.add(i)
                envelope[i] > envelope[last] -> peaks[peaks.lastIndex] = i
            }
        }
        return peaks.map { it / envelopeRateHz * 1000f }
    }
}
