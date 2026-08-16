package li.cactus.bandy.core.domain.repository

import kotlinx.coroutines.flow.Flow
import li.cactus.bandy.core.domain.model.Recording

interface RecordingRepository {
    fun observeRecordings(query: String?): Flow<List<Recording>>
    suspend fun getById(id: Long): Recording?
    suspend fun insert(recording: Recording): Long
    suspend fun rename(id: Long, newTitle: String)
    suspend fun delete(id: Long)
}
