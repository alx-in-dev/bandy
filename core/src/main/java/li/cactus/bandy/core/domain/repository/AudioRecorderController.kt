package li.cactus.bandy.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import li.cactus.bandy.core.domain.model.RecordingSession
import li.cactus.bandy.core.domain.model.SpectrumFrame

/**
 * Owns the live AudioRecord capture session. A single instance is shared between the
 * foreground service (which keeps it alive + shows the notification) and the record
 * screen's ViewModel (which observes it) — both are Koin singletons over the same object.
 */
interface AudioRecorderController {
    val session: StateFlow<RecordingSession>
    val liveSpectrum: Flow<SpectrumFrame>

    /** Starts capture into a new temp WAV file, returns the path it is writing to. */
    fun start(sampleRate: Int, fftWindowSize: Int): String
    fun pause()
    fun resume()

    /** Stops capture and returns the final duration in ms of the raw WAV written. */
    suspend fun stop(): Long
}
