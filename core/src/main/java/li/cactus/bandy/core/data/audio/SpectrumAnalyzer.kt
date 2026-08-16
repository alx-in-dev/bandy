package li.cactus.bandy.core.data.audio

import kotlin.math.cos
import kotlin.math.sqrt
import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.model.SpectrumFrame

class SpectrumAnalyzer(private val windowSize: Int) {

    private val fftProcessor = FftProcessor(windowSize)
    private val hannWindow = DoubleArray(windowSize) { i ->
        0.5 - 0.5 * cos(2 * Math.PI * i / (windowSize - 1))
    }

    /** [samples] should have size == windowSize; shorter buffers are zero-padded. */
    fun analyzeWindow(samples: ShortArray, sampleRate: Int, timestampMs: Long): SpectrumFrame {
        val data = DoubleArray(windowSize)
        val limit = minOf(samples.size, windowSize)
        for (i in 0 until limit) {
            data[i] = (samples[i] / 32768.0) * hannWindow[i]
        }
        fftProcessor.forward(data)
        return SpectrumFrame(timestampMs, fftProcessor.magnitudes(data), sampleRate)
    }

    fun rms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            val n = s / 32768.0
            sum += n * n
        }
        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Full-file spectrogram (time -> spectrum), one [SpectrumFrame] per analysis window with a
     * real timestamp. Hop is scaled up for long recordings so the frame count stays around
     * [targetFrames] — this is for on-screen rendering, not analysis precision.
     */
    fun spectrogram(allSamples: ShortArray, sampleRate: Int, targetFrames: Int): List<SpectrumFrame> {
        if (allSamples.size < windowSize) {
            return listOf(analyzeWindow(allSamples, sampleRate, 0L))
        }
        val hop = maxOf(windowSize / 4, allSamples.size / targetFrames)
        val frames = mutableListOf<SpectrumFrame>()
        var offset = 0
        while (offset + windowSize <= allSamples.size) {
            val window = allSamples.copyOfRange(offset, offset + windowSize)
            val timestampMs = (offset.toLong() * 1000) / sampleRate
            frames.add(analyzeWindow(window, sampleRate, timestampMs))
            offset += hop
        }
        return frames
    }

    /** Averages magnitude spectra over overlapping windows (50% hop) across the whole signal. */
    fun averagedSpectrum(allSamples: ShortArray, sampleRate: Int): AveragedSpectrum {
        val half = fftProcessor.halfSize + 1
        if (allSamples.size < windowSize) {
            val frame = analyzeWindow(allSamples, sampleRate, 0L)
            return AveragedSpectrum(frame.magnitudes, sampleRate)
        }
        val acc = DoubleArray(half)
        val hop = windowSize / 2
        var offset = 0
        var count = 0
        while (offset + windowSize <= allSamples.size) {
            val window = allSamples.copyOfRange(offset, offset + windowSize)
            val frame = analyzeWindow(window, sampleRate, 0L)
            for (i in 0 until half) acc[i] += frame.magnitudes[i]
            count++
            offset += hop
        }
        val avg = FloatArray(half) { (acc[it] / count).toFloat() }
        return AveragedSpectrum(avg, sampleRate)
    }
}
