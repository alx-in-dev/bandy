package li.cactus.bandy.core.data.repository

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.cactus.bandy.core.data.audio.BiquadBandPass
import li.cactus.bandy.core.data.audio.WavFileReader
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.repository.LivePreviewPlayer

private const val PREVIEW_CHUNK_SAMPLES = 1024

/** Streams a WAV file through AudioTrack, optionally through a live biquad band-pass bank, for before/after preview. */
internal class LivePreviewPlayerImpl : LivePreviewPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile private var playing = false
    override val isPlaying: Boolean get() = playing

    override fun play(sourceFilePath: String, settings: FilterSettings?, loop: Boolean) {
        stop()

        val reader = WavFileReader(File(sourceFilePath))
        val sampleRate = reader.sampleRate

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        playing = true
        track.play()

        job = scope.launch {
            try {
                do {
                    // Fresh filter instance per pass so biquad state doesn't carry a
                    // discontinuity click across the loop point.
                    val filter = settings?.takeUnless { it.isFullSpectrum }?.let {
                        BiquadBandPass(sampleRate, it.bands, it.butterworthOrder)
                    }
                    reader.readChunked(PREVIEW_CHUNK_SAMPLES) { chunk ->
                        if (isActive) {
                            val processed = filter?.processBuffer(chunk) ?: chunk
                            track.write(processed, 0, processed.size)
                        }
                    }
                } while (loop && isActive)
            } finally {
                track.stop()
                track.release()
                playing = false
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        playing = false
    }
}
