package li.cactus.bandy.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import li.cactus.bandy.core.domain.model.SpectrumFrame
import li.cactus.bandy.core.domain.repository.AudioRecorderController

interface ObserveLiveSpectrumUseCase {
    operator fun invoke(): Flow<SpectrumFrame>
}

internal class ObserveLiveSpectrumUseCaseImpl(
    private val controller: AudioRecorderController,
) : ObserveLiveSpectrumUseCase {
    override fun invoke(): Flow<SpectrumFrame> = controller.liveSpectrum
}
