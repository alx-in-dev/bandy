package li.cactus.bandy.core.data.audio

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import li.cactus.bandy.core.domain.model.FrequencyBand

/** Single RBJ biquad band-pass (constant skirt gain) section. */
private class BiquadSection {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun configure(centerHz: Double, q: Double, sampleRate: Double) {
        val w0 = 2 * Math.PI * centerHz / sampleRate
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        b0 = alpha / a0
        b1 = 0.0
        b2 = -alpha / a0
        a1 = (-2 * cos(w0)) / a0
        a2 = (1 - alpha) / a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}

/**
 * Low-latency IIR band-pass bank for live before/after preview: one cascade of biquad sections
 * per selected band (order/2 sections each), outputs summed for multi-band union.
 */
class BiquadBandPass(sampleRate: Int, bands: List<FrequencyBand>, order: Int) {

    private val bandCascades: List<List<BiquadSection>> = bands.map { band ->
        val center = sqrt(band.lowHz.coerceAtLeast(1).toDouble() * band.highHz.toDouble())
        val bandwidth = (band.highHz - band.lowHz).toDouble().coerceAtLeast(1.0)
        val q = (center / bandwidth).coerceIn(0.3, 20.0)
        val sections = (order / 2).coerceAtLeast(1)
        List(sections) { BiquadSection().apply { configure(center, q, sampleRate.toDouble()) } }
    }

    fun processSample(x: Double): Double {
        if (bandCascades.isEmpty()) return x
        var sum = 0.0
        for (cascade in bandCascades) {
            var v = x
            for (section in cascade) v = section.process(v)
            sum += v
        }
        return sum.coerceIn(-1.0, 1.0)
    }

    fun processBuffer(input: ShortArray): ShortArray {
        val output = ShortArray(input.size)
        for (i in input.indices) {
            val y = processSample(input[i] / 32768.0)
            output[i] = (y * 32767).toInt().toShort()
        }
        return output
    }
}
