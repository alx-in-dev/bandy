package li.cactus.bandy.core.data.audio

import java.io.File
import java.io.RandomAccessFile

private const val HEADER_SIZE = 44
private const val BITS_PER_SAMPLE = 16

/** Streams PCM16 samples into a canonical 44-byte-header WAV file, patching sizes on [finish]. */
class WavFileWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int = 1,
) {
    private lateinit var raf: RandomAccessFile
    private var dataBytesWritten = 0L

    fun open() {
        raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        raf.write(ByteArray(HEADER_SIZE)) // placeholder, patched in finish()
        dataBytesWritten = 0L
    }

    fun writeSamples(samples: ShortArray, length: Int = samples.size) {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            val s = samples[i].toInt()
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        raf.write(bytes)
        dataBytesWritten += bytes.size
    }

    /** Patches the RIFF/data chunk sizes and returns the duration in ms. */
    fun finish(): Long {
        val byteRate = sampleRate * channels * (BITS_PER_SAMPLE / 8)
        val blockAlign = channels * (BITS_PER_SAMPLE / 8)
        val header = ByteArray(HEADER_SIZE)

        fun put4(offset: Int, s: String) = s.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        fun putInt(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun putShort(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        put4(0, "RIFF")
        putInt(4, 36 + dataBytesWritten.toInt())
        put4(8, "WAVE")
        put4(12, "fmt ")
        putInt(16, 16) // fmt chunk size
        putShort(20, 1) // PCM
        putShort(22, channels)
        putInt(24, sampleRate)
        putInt(28, byteRate)
        putShort(32, blockAlign)
        putShort(34, BITS_PER_SAMPLE)
        put4(36, "data")
        putInt(40, dataBytesWritten.toInt())

        raf.seek(0)
        raf.write(header)
        raf.close()

        val totalSamples = dataBytesWritten / 2 / channels
        return (totalSamples * 1000L) / sampleRate
    }
}
