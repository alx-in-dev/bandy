package li.cactus.bandy.core.data.audio

import kotlin.math.abs
import kotlin.math.sqrt
import org.jtransforms.fft.DoubleFFT_1D

/**
 * Thin wrapper around JTransforms' real FFT in packed format:
 * a[0]=Re[0], a[1]=Re[n/2], a[2k]=Re[k], a[2k+1]=Im[k] for k in 1 until n/2.
 */
class FftProcessor(val size: Int) {
    private val fft = DoubleFFT_1D(size.toLong())
    val halfSize: Int = size / 2

    /** In-place forward real FFT; [data].size must equal [size]. */
    fun forward(data: DoubleArray) {
        require(data.size == size)
        fft.realForward(data)
    }

    /** In-place inverse of a forward()-produced packed array, normalized back to the original scale. */
    fun inverse(data: DoubleArray) {
        require(data.size == size)
        fft.realInverse(data, true)
    }

    fun magnitudes(packed: DoubleArray): FloatArray {
        val mags = FloatArray(halfSize + 1)
        mags[0] = abs(packed[0]).toFloat()
        mags[halfSize] = abs(packed[1]).toFloat()
        for (k in 1 until halfSize) {
            val re = packed[2 * k]
            val im = packed[2 * k + 1]
            mags[k] = sqrt(re * re + im * im).toFloat()
        }
        return mags
    }

    /** Multiplies each frequency bin (0..halfSize) by [gainForBin] in place. */
    fun applyGain(packed: DoubleArray, gainForBin: (Int) -> Double) {
        packed[0] *= gainForBin(0)
        packed[1] *= gainForBin(halfSize)
        for (k in 1 until halfSize) {
            val g = gainForBin(k)
            packed[2 * k] *= g
            packed[2 * k + 1] *= g
        }
    }
}
