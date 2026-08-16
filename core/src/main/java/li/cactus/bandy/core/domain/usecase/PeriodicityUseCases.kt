package li.cactus.bandy.core.domain.usecase

import java.io.File
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.PeriodicityAnalysis
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.repository.PeriodicityRepository
import li.cactus.bandy.core.domain.repository.RecordingRepository

interface AnalyzePeriodicityUseCase {
    suspend operator fun invoke(filePath: String, band: FrequencyBand, order: Int): PeriodicityAnalysis
}

internal class AnalyzePeriodicityUseCaseImpl(
    private val repository: PeriodicityRepository,
) : AnalyzePeriodicityUseCase {
    override suspend fun invoke(filePath: String, band: FrequencyBand, order: Int): PeriodicityAnalysis =
        repository.analyze(filePath, band, order)
}

interface ApplyCombFilterUseCase {
    /** Saves a new library Recording, like [ApplyBandPassFilterUseCase]. */
    suspend operator fun invoke(source: Recording, band: FrequencyBand, order: Int, periodMs: Float, harmonics: Int): Recording
}

internal class ApplyCombFilterUseCaseImpl(
    private val periodicityRepository: PeriodicityRepository,
    private val recordingRepository: RecordingRepository,
) : ApplyCombFilterUseCase {
    override suspend fun invoke(
        source: Recording,
        band: FrequencyBand,
        order: Int,
        periodMs: Float,
        harmonics: Int,
    ): Recording {
        val outputPath = periodicityRepository.applyCombFilter(source.filePath, band, order, periodMs, harmonics)
        val filtered = Recording(
            title = File(outputPath).nameWithoutExtension,
            filePath = outputPath,
            format = AudioFileFormat.WAV,
            createdAtMs = System.currentTimeMillis(),
            durationMs = source.durationMs,
            sampleRate = source.sampleRate,
            appliedBands = listOf(band),
            sourceRecordingId = source.id,
        )
        val id = recordingRepository.insert(filtered)
        return filtered.copy(id = id)
    }
}

interface BuildSynchronousAverageUseCase {
    /** Returns a temp WAV path (single averaged cycle) — not saved to the library, meant for looped preview. */
    suspend operator fun invoke(
        source: Recording,
        band: FrequencyBand,
        order: Int,
        analysis: PeriodicityAnalysis,
    ): String
}

internal class BuildSynchronousAverageUseCaseImpl(
    private val repository: PeriodicityRepository,
) : BuildSynchronousAverageUseCase {
    override suspend fun invoke(source: Recording, band: FrequencyBand, order: Int, analysis: PeriodicityAnalysis): String =
        repository.buildSynchronousAverage(source.filePath, band, order, analysis.periodMs, analysis.impulseTimestampsMs)
}
