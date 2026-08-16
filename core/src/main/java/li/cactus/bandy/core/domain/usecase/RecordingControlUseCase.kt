package li.cactus.bandy.core.domain.usecase

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.first
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.repository.AudioRecorderController
import li.cactus.bandy.core.domain.repository.RecordingRepository
import li.cactus.bandy.core.domain.repository.SettingsRepository
import li.cactus.bandy.core.service.RecordingForegroundService

interface RecordingControlUseCase {
    suspend fun start()
    fun pause()
    fun resume()
    suspend fun stop(): Recording
}

internal class RecordingControlUseCaseImpl(
    private val context: Context,
    private val controller: AudioRecorderController,
    private val recordingRepository: RecordingRepository,
    private val settingsRepository: SettingsRepository,
) : RecordingControlUseCase {

    private var activeFilePath: String? = null

    override suspend fun start() {
        val settings = settingsRepository.settings.first()
        activeFilePath = controller.start(settings.sampleRate.hz, settings.fftWindowSize.samples)
        RecordingForegroundService.start(context)
    }

    override fun pause() = controller.pause()

    override fun resume() = controller.resume()

    override suspend fun stop(): Recording {
        val durationMs = controller.stop()
        RecordingForegroundService.stop(context)
        val path = activeFilePath ?: error("Recording was not started")
        val sampleRate = settingsRepository.settings.first().sampleRate.hz
        val recording = Recording(
            title = File(path).nameWithoutExtension,
            filePath = path,
            format = AudioFileFormat.WAV,
            createdAtMs = System.currentTimeMillis(),
            durationMs = durationMs,
            sampleRate = sampleRate,
            appliedBands = emptyList(),
        )
        val id = recordingRepository.insert(recording)
        activeFilePath = null
        return recording.copy(id = id)
    }
}
