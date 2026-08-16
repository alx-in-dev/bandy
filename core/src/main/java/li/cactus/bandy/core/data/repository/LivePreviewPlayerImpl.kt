package li.cactus.bandy.core.data.repository

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.cactus.bandy.core.data.audio.BiquadBandPass
import li.cactus.bandy.core.data.audio.WavFileReader
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.repository.LivePreviewPlayer

private const val PREVIEW_CHUNK_SAMPLES = 1024

/**
 * Streams a WAV file through AudioTrack, optionally through a live biquad band-pass bank, for
 * before/after preview. A Koin single — shared by every screen that can play audio (editor
 * preview, library quick-play) so starting playback in one place stops it everywhere else.
 */
internal class LivePreviewPlayerImpl : LivePreviewPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Read on every chunk of the active session; [updateFilter] swaps it so edits are heard
     * immediately without restarting playback position. Null when playing unfiltered ("before"). */
    @Volatile private var activeFilter: BiquadBandPass? = null
    @Volatile private var canFilter: Boolean = false
    private var activeSampleRate: Int = 0

    override fun play(sourceFilePath: String, settings: FilterSettings?, loop: Boolean) {
        stop()

        val reader = WavFileReader(File(sourceFilePath))
        val sampleRate = reader.sampleRate
        activeSampleRate = sampleRate
        canFilter = settings != null
        activeFilter = settings?.takeUnless { it.isFullSpectrum }?.let {
            BiquadBandPass(sampleRate, it.bands, it.butterworthOrder)
        }

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

        _isPlaying.value = true
        track.play()

        job = scope.launch {
            try {
                do {
                    reader.readChunked(PREVIEW_CHUNK_SAMPLES) { chunk ->
                        if (isActive) {
                            val processed = activeFilter?.processBuffer(chunk) ?: chunk
                            track.write(processed, 0, processed.size)
                        }
                    }
                } while (loop && isActive)
            } finally {
                track.stop()
                track.release()
                _isPlaying.value = false
                activeFilter = null
                canFilter = false
            }
        }
    }

    override fun updateFilter(settings: FilterSettings) {
        if (!canFilter || !_isPlaying.value) return
        activeFilter = settings.takeUnless { it.isFullSpectrum }?.let {
            BiquadBandPass(activeSampleRate, it.bands, it.butterworthOrder)
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _isPlaying.value = false
    }
}
