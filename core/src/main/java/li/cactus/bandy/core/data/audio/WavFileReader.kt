package li.cactus.bandy.core.data.audio

import java.io.File
import java.io.RandomAccessFile

/** Reads a canonical PCM16 mono/stereo WAV file, locating "fmt " and "data" chunks explicitly. */
class WavFileReader(private val file: File) {

    val sampleRate: Int
    private val channels: Int
    private val dataOffset: Long
    private val dataSize: Long

    init {
        RandomAccessFile(file, "r").use { raf ->
            require(readTag(raf) == "RIFF") { "Not a RIFF file: ${file.path}" }
            raf.skipBytes(4) // riff size
            require(readTag(raf) == "WAVE") { "Not a WAVE file: ${file.path}" }

            var foundSampleRate = 0
            var foundChannels = 1
            var foundDataOffset = -1L
            var foundDataSize = 0L

            while (raf.filePointer < raf.length() - 8) {
                val tag = readTag(raf)
                val size = readLe32(raf)
                when (tag) {
                    "fmt " -> {
                        val fmtStart = raf.filePointer
                        raf.skipBytes(2) // audio format
                        foundChannels = readLe16(raf)
                        foundSampleRate = readLe32(raf)
                        raf.seek(fmtStart + size)
                    }
                    "data" -> {
                        foundDataOffset = raf.filePointer
                        foundDataSize = size.toLong() and 0xFFFFFFFFL
                        raf.seek(foundDataOffset + foundDataSize)
                    }
                    else -> raf.skipBytes(size)
                }
                if (size % 2 == 1 && raf.filePointer < raf.length()) raf.skipBytes(1)
            }

            require(foundDataOffset >= 0) { "No data chunk in ${file.path}" }
            sampleRate = foundSampleRate
            channels = foundChannels
            dataOffset = foundDataOffset
            dataSize = minOf(foundDataSize, raf.length() - foundDataOffset)
        }
    }

    val durationMs: Long
        get() {
            val totalSamples = dataSize / 2 / channels
            return if (sampleRate == 0) 0L else (totalSamples * 1000L) / sampleRate
        }

    fun readAllSamples(): ShortArray {
        val sampleCount = (dataSize / 2).toInt()
        val result = ShortArray(sampleCount)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize.toInt())
            raf.readFully(bytes)
            for (i in 0 until sampleCount) {
                val lo = bytes[2 * i].toInt() and 0xFF
                val hi = bytes[2 * i + 1].toInt()
                result[i] = ((hi shl 8) or lo).toShort()
            }
        }
        return result
    }

    fun readChunked(chunkSize: Int, onChunk: (ShortArray) -> Unit) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(dataOffset)
            val byteChunk = ByteArray(chunkSize * 2)
            var remaining = dataSize
            while (remaining > 0) {
                val toRead = minOf(byteChunk.size.toLong(), remaining).toInt()
                val read = raf.read(byteChunk, 0, toRead)
                if (read <= 0) break
                val sampleCount = read / 2
                val samples = ShortArray(sampleCount)
                for (i in 0 until sampleCount) {
                    val lo = byteChunk[2 * i].toInt() and 0xFF
                    val hi = byteChunk[2 * i + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort()
                }
                onChunk(samples)
                remaining -= read
            }
        }
    }

    private fun readTag(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readLe32(raf: RandomAccessFile): Int {
        val b0 = raf.read(); val b1 = raf.read(); val b2 = raf.read(); val b3 = raf.read()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
    }

    private fun readLe16(raf: RandomAccessFile): Int {
        val b0 = raf.read(); val b1 = raf.read()
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
    }
}
