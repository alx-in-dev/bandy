package li.cactus.bandy.core.data.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import li.cactus.bandy.core.domain.model.FrequencyBand

private const val DEFAULT_FLOOR_GAIN = 0.2
private const val RELATIVE_BUMP_WIDTH = 0.08

/**
 * Emphasizes harmonics of a detected repetition rate within [band] (Butterworth-shaped edges,
 * same as [OfflineBandPassFilter]) while suppressing non-periodic energy — a Gaussian "bump" of
 * gain around each harmonic of [periodMs]'s fundamental, [DEFAULT_FLOOR_GAIN] everywhere else in
 * the band, near-zero outside it. Reduces broadband noise around a signal (e.g. an engine
 * knock) that is periodic but occupies the same frequency range as the background noise.
 */
class PeriodicityCombFilter(blockSize: Int = 4096) {

    private val engine = StftGainProcessor(blockSize)

    fun process(
        input: ShortArray,
        sampleRate: Int,
        band: FrequencyBand,
        order: Int,
        periodMs: Float,
        harmonics: Int,
        floorGain: Double = DEFAULT_FLOOR_GAIN,
    ): ShortArray {
        val fundamentalHz = 1000.0 / periodMs
        val binHz = engine.binHz(sampleRate)
        val bumpWidthHz = (fundamentalHz * RELATIVE_BUMP_WIDTH).coerceAtLeast(binHz * 2)

        val gains = DoubleArray(engine.halfSize + 1) { bin ->
            val freq = bin * binHz
            val bandGain = butterworthBandGain(freq, band, order)
            val comb = combGain(freq, fundamentalHz, harmonics, bumpWidthHz, floorGain)
            (bandGain * comb).coerceIn(0.0, 1.0)
        }
        return engine.apply(input, gains)
    }

    private fun combGain(freq: Double, fundamentalHz: Double, harmonics: Int, bumpWidthHz: Double, floorGain: Double): Double {
        var best = floorGain
        for (h in 1..harmonics) {
            val center = fundamentalHz * h
            val d = abs(freq - center)
            val bump = exp(-(d * d) / (2 * bumpWidthHz * bumpWidthHz))
            val g = floorGain + (1.0 - floorGain) * bump
            if (g > best) best = g
        }
        return best
    }

    private fun butterworthBandGain(freq: Double, band: FrequencyBand, order: Int): Double {
        val lowGain = if (band.lowHz <= 0) {
            1.0
        } else if (freq <= 0.0) {
            0.0
        } else {
            1.0 / sqrt(1.0 + (band.lowHz.toDouble() / freq).pow(2 * order))
        }
        val highGain = 1.0 / sqrt(1.0 + (freq / band.highHz.toDouble()).pow(2 * order))
        return lowGain * highGain
    }
}
