package li.cactus.bandy.core.domain.model

/** One FFT analysis window: magnitude per frequency bin, evenly spaced from 0Hz to Nyquist. */
data class SpectrumFrame(
    val timestampMs: Long,
    val magnitudes: FloatArray,
    val sampleRate: Int,
) {
    val binHz: Float get() = (sampleRate / 2f) / magnitudes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpectrumFrame) return false
        return timestampMs == other.timestampMs &&
            magnitudes.contentEquals(other.magnitudes) &&
            sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + magnitudes.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

/** Averaged magnitude spectrum over a whole recording, used for band selection. */
data class AveragedSpectrum(
    val magnitudes: FloatArray,
    val sampleRate: Int,
) {
    val binHz: Float get() = (sampleRate / 2f) / magnitudes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AveragedSpectrum) return false
        return magnitudes.contentEquals(other.magnitudes) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = magnitudes.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}
