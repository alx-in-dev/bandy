package li.cactus.bandy.core.data.audio

import kotlin.math.pow
import kotlin.math.sqrt
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.model.FrequencyBand

/** Offline band-pass: Butterworth-shaped per-bin gain (union of [FilterSettings.bands]) applied via [StftGainProcessor]. */
class OfflineBandPassFilter(blockSize: Int = 4096) {

    private val engine = StftGainProcessor(blockSize)

    fun process(input: ShortArray, sampleRate: Int, settings: FilterSettings): ShortArray {
        if (settings.isFullSpectrum) return input.copyOf()
        val binHz = engine.binHz(sampleRate)
        val gains = DoubleArray(engine.halfSize + 1) { bin ->
            val freq = bin * binHz
            settings.bands.maxOf { band -> butterworthBandGain(freq, band, settings.butterworthOrder) }
                .coerceIn(0.0, 1.0)
        }
        return engine.apply(input, gains)
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
