package li.cactus.bandy.core.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TIMEOUT_US = 10_000L
private const val BIT_RATE = 128_000
private const val CHUNK_SAMPLES = 4096

/** Encodes a PCM16 mono WAV file to AAC/M4A via MediaCodec + MediaMuxer. */
class AacEncoder {

    suspend fun encode(pcmWavFile: File, outputFile: File, sampleRate: Int) = withContext(Dispatchers.IO) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val reader = WavFileReader(pcmWavFile)
        val pendingChunks = ArrayDeque<ShortArray>()
        reader.readChunked(CHUNK_SAMPLES) { pendingChunks.addLast(it) }

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputIndex)
                        if (pendingChunks.isNotEmpty()) {
                            val chunk = pendingChunks.removeFirst()
                            val byteBuffer = ByteBuffer.allocate(chunk.size * 2)
                            for (s in chunk) byteBuffer.putShort(s)
                            byteBuffer.flip()
                            inputBuffer?.clear()
                            inputBuffer?.put(byteBuffer)
                            codec.queueInputBuffer(inputIndex, 0, chunk.size * 2, 0L, 0)
                        } else {
                            inputDone = true
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                            muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }
}
