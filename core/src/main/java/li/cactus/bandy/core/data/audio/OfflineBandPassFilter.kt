package li.cactus.bandy.core.data.audio

import kotlin.math.cos
import kotlin.math.pow
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.model.FrequencyBand

/**
 * Offline band-pass via STFT + weighted overlap-add: forward FFT per block, multiply each bin
 * by a Butterworth-shaped gain (see FrequencyBand union below), inverse FFT, overlap-add.
 */
class OfflineBandPassFilter(private val blockSize: Int = 4096) {

    private val hopSize = blockSize / 2
    private val fft = FftProcessor(blockSize)
    private val window = DoubleArray(blockSize) { i ->
        0.5 - 0.5 * cos(2 * Math.PI * i / (blockSize - 1))
    }

    fun process(input: ShortArray, sampleRate: Int, settings: FilterSettings): ShortArray {
        if (settings.isFullSpectrum) return input.copyOf()

        val gains = precomputeGains(sampleRate, settings)
        val outLen = input.size + blockSize
        val output = DoubleArray(outLen)
        val windowSum = DoubleArray(outLen)
        val block = DoubleArray(blockSize)

        var offset = 0
        while (offset < input.size) {
            val len = minOf(blockSize, input.size - offset)
            for (i in 0 until len) block[i] = (input[offset + i] / 32768.0) * window[i]
            for (i in len until blockSize) block[i] = 0.0

            fft.forward(block)
            fft.applyGain(block) { bin -> gains[bin] }
            fft.inverse(block)

            for (i in 0 until blockSize) {
                val idx = offset + i
                if (idx < outLen) {
                    output[idx] += block[i] * window[i]
                    windowSum[idx] += window[i] * window[i]
                }
            }
            offset += hopSize
        }

        val result = ShortArray(input.size)
        for (i in input.indices) {
            val norm = windowSum[i].takeIf { it > 1e-6 } ?: 1.0
            val v = (output[i] / norm).coerceIn(-1.0, 1.0)
            result[i] = (v * 32767).toInt().toShort()
        }
        return result
    }

    private fun precomputeGains(sampleRate: Int, settings: FilterSettings): DoubleArray {
        val binHz = (sampleRate / 2.0) / fft.halfSize
        return DoubleArray(fft.halfSize + 1) { bin ->
            val freq = bin * binHz
            settings.bands.maxOf { band -> butterworthBandGain(freq, band, settings.butterworthOrder) }
                .coerceIn(0.0, 1.0)
        }
    }

    private fun butterworthBandGain(freq: Double, band: FrequencyBand, order: Int): Double {
        val lowGain = if (band.lowHz <= 0) {
            1.0
        } else if (freq <= 0.0) {
            0.0
        } else {
            1.0 / kotlin.math.sqrt(1.0 + (band.lowHz.toDouble() / freq).pow(2 * order))
        }
        val highGain = 1.0 / kotlin.math.sqrt(1.0 + (freq / band.highHz.toDouble()).pow(2 * order))
        return lowGain * highGain
    }
}
