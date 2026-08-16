package li.cactus.bandy.core.domain.usecase

import java.io.File
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.repository.AudioFilterRepository
import li.cactus.bandy.core.domain.repository.ExportRepository
import li.cactus.bandy.core.domain.repository.RecordingRepository

interface ApplyBandPassFilterUseCase {
    suspend operator fun invoke(
        source: Recording,
        settings: FilterSettings,
        alsoExportAac: Boolean,
    ): Recording
}

internal class ApplyBandPassFilterUseCaseImpl(
    private val filterRepository: AudioFilterRepository,
    private val exportRepository: ExportRepository,
    private val recordingRepository: RecordingRepository,
) : ApplyBandPassFilterUseCase {

    override suspend fun invoke(
        source: Recording,
        settings: FilterSettings,
        alsoExportAac: Boolean,
    ): Recording {
        val filteredWavPath = filterRepository.applyBandPass(source.filePath, settings)
        val outputPath = if (alsoExportAac) {
            exportRepository.encodeToAac(filteredWavPath)
        } else {
            filteredWavPath
        }
        val format = if (alsoExportAac) AudioFileFormat.AAC else AudioFileFormat.WAV
        val filtered = Recording(
            title = File(filteredWavPath).nameWithoutExtension,
            filePath = outputPath,
            format = format,
            createdAtMs = System.currentTimeMillis(),
            durationMs = source.durationMs,
            sampleRate = source.sampleRate,
            appliedBands = settings.bands,
            sourceRecordingId = source.id,
        )
        val id = recordingRepository.insert(filtered)
        return filtered.copy(id = id)
    }
}
