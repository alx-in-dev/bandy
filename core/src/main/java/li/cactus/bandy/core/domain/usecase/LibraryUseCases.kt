package li.cactus.bandy.core.domain.usecase

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.repository.ExportRepository
import li.cactus.bandy.core.domain.repository.RecordingRepository
import li.cactus.bandy.core.domain.repository.StorageRepository

interface ObserveRecordingsUseCase {
    operator fun invoke(query: String?): Flow<List<Recording>>
}

internal class ObserveRecordingsUseCaseImpl(
    private val repository: RecordingRepository,
) : ObserveRecordingsUseCase {
    override fun invoke(query: String?): Flow<List<Recording>> = repository.observeRecordings(query)
}

interface GetRecordingUseCase {
    suspend operator fun invoke(id: Long): Recording?
}

internal class GetRecordingUseCaseImpl(
    private val repository: RecordingRepository,
) : GetRecordingUseCase {
    override suspend fun invoke(id: Long): Recording? = repository.getById(id)
}

interface RenameRecordingUseCase {
    suspend operator fun invoke(id: Long, newTitle: String)
}

internal class RenameRecordingUseCaseImpl(
    private val repository: RecordingRepository,
) : RenameRecordingUseCase {
    override suspend fun invoke(id: Long, newTitle: String) = repository.rename(id, newTitle)
}

interface DeleteRecordingUseCase {
    suspend operator fun invoke(recording: Recording)
}

internal class DeleteRecordingUseCaseImpl(
    private val recordingRepository: RecordingRepository,
    private val storageRepository: StorageRepository,
) : DeleteRecordingUseCase {
    override suspend fun invoke(recording: Recording) {
        recordingRepository.delete(recording.id)
        storageRepository.delete(recording.filePath)
    }
}

interface ShareRecordingUseCase {
    operator fun invoke(recording: Recording): Uri
}

internal class ShareRecordingUseCaseImpl(
    private val exportRepository: ExportRepository,
) : ShareRecordingUseCase {
    override fun invoke(recording: Recording): Uri = exportRepository.shareUriFor(recording.filePath)
}
