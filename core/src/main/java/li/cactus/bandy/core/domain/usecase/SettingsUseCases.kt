package li.cactus.bandy.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import li.cactus.bandy.core.domain.model.AudioSettings
import li.cactus.bandy.core.domain.model.FftWindowSize
import li.cactus.bandy.core.domain.model.SampleRateOption
import li.cactus.bandy.core.domain.repository.SettingsRepository

interface ObserveAudioSettingsUseCase {
    operator fun invoke(): Flow<AudioSettings>
}

internal class ObserveAudioSettingsUseCaseImpl(
    private val repository: SettingsRepository,
) : ObserveAudioSettingsUseCase {
    override fun invoke(): Flow<AudioSettings> = repository.settings
}

interface UpdateAudioSettingsUseCase {
    suspend fun setSampleRate(option: SampleRateOption)
    suspend fun setFftWindowSize(size: FftWindowSize)
}

internal class UpdateAudioSettingsUseCaseImpl(
    private val repository: SettingsRepository,
) : UpdateAudioSettingsUseCase {
    override suspend fun setSampleRate(option: SampleRateOption) = repository.setSampleRate(option)
    override suspend fun setFftWindowSize(size: FftWindowSize) = repository.setFftWindowSize(size)
}
