package li.cactus.bandy.feature.editor.presentation

import kotlin.math.log10
import kotlin.math.pow

/** Shared frequency <-> pixel mapping so every chart in the editor (averaged spectrum,
 * spectrogram) uses the same log/linear axis math. */
internal const val MIN_LOG_HZ = 20f

internal fun freqToPixel(freq: Float, scale: FreqScale, maxHz: Float, length: Float): Float = when (scale) {
    FreqScale.LINEAR -> (freq / maxHz) * length
    FreqScale.LOG -> {
        val f = freq.coerceAtLeast(MIN_LOG_HZ)
        ((log10(f) - log10(MIN_LOG_HZ)) / (log10(maxHz) - log10(MIN_LOG_HZ))) * length
    }
}

internal fun pixelToFreq(pixel: Float, scale: FreqScale, maxHz: Float, length: Float): Float {
    if (length <= 0f) return 0f
    val t = (pixel / length).coerceIn(0f, 1f)
    return when (scale) {
        FreqScale.LINEAR -> t * maxHz
        FreqScale.LOG -> {
            val logMin = log10(MIN_LOG_HZ)
            val logMax = log10(maxHz)
            10f.pow(t * (logMax - logMin) + logMin)
        }
    }
}
