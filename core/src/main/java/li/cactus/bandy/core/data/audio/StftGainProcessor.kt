package li.cactus.bandy.core.data.audio

import kotlin.math.cos

/**
 * Shared STFT + weighted overlap-add engine: forward FFT per block, multiply bins by a caller-supplied
 * per-bin gain curve, inverse FFT, overlap-add reconstruct. Used both for the band-pass filter and the
 * periodicity comb filter — they only differ in how the gain curve is shaped.
 */
internal class StftGainProcessor(private val blockSize: Int = 4096) {

    private val hopSize = blockSize / 2
    private val fft = FftProcessor(blockSize)
    private val window = DoubleArray(blockSize) { i ->
        0.5 - 0.5 * cos(2 * Math.PI * i / (blockSize - 1))
    }

    val halfSize: Int get() = fft.halfSize

    fun binHz(sampleRate: Int): Double = (sampleRate / 2.0) / fft.halfSize

    /** [gains] must have size `halfSize + 1`, one gain per frequency bin from 0Hz to Nyquist. */
    fun apply(input: ShortArray, gains: DoubleArray): ShortArray {
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
}
