package li.cactus.bandy.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import li.cactus.bandy.core.data.storage.RecordingDao
import li.cactus.bandy.core.data.storage.toDomain
import li.cactus.bandy.core.data.storage.toEntity
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.repository.RecordingRepository

internal class RecordingRepositoryImpl(
    private val dao: RecordingDao,
) : RecordingRepository {

    override fun observeRecordings(query: String?): Flow<List<Recording>> =
        dao.observeAll(query?.takeIf { it.isNotBlank() }).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Recording? = dao.getById(id)?.toDomain()

    override suspend fun insert(recording: Recording): Long = dao.insert(recording.toEntity())

    override suspend fun rename(id: Long, newTitle: String) = dao.rename(id, newTitle)

    override suspend fun delete(id: Long) = dao.delete(id)
}
