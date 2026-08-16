package li.cactus.bandy.core.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import li.cactus.bandy.core.domain.model.RecordingSession
import li.cactus.bandy.core.domain.repository.AudioRecorderController

interface ObserveRecordingSessionUseCase {
    operator fun invoke(): StateFlow<RecordingSession>
}

internal class ObserveRecordingSessionUseCaseImpl(
    private val controller: AudioRecorderController,
) : ObserveRecordingSessionUseCase {
    override fun invoke(): StateFlow<RecordingSession> = controller.session
}
