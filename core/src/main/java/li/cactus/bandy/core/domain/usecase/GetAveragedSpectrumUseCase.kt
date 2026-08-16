package li.cactus.bandy.core.domain.usecase

import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.repository.AudioAnalysisRepository

interface GetAveragedSpectrumUseCase {
    suspend operator fun invoke(filePath: String, fftWindowSize: Int): AveragedSpectrum
}

internal class GetAveragedSpectrumUseCaseImpl(
    private val repository: AudioAnalysisRepository,
) : GetAveragedSpectrumUseCase {
    override suspend fun invoke(filePath: String, fftWindowSize: Int): AveragedSpectrum =
        repository.getAveragedSpectrum(filePath, fftWindowSize)
}
