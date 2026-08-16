package li.cactus.bandy.core.domain.usecase

import li.cactus.bandy.core.domain.model.SpectrumFrame
import li.cactus.bandy.core.domain.repository.AudioAnalysisRepository

interface GetSpectrogramUseCase {
    suspend operator fun invoke(filePath: String, fftWindowSize: Int): List<SpectrumFrame>
}

internal class GetSpectrogramUseCaseImpl(
    private val repository: AudioAnalysisRepository,
) : GetSpectrogramUseCase {
    override suspend fun invoke(filePath: String, fftWindowSize: Int): List<SpectrumFrame> =
        repository.getSpectrogram(filePath, fftWindowSize)
}
