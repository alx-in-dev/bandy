package li.cactus.bandy.core.data.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.Recording

@Entity(tableName = "recordings")
@TypeConverters(RecordingConverters::class)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val filePath: String,
    val format: AudioFileFormat,
    val createdAtMs: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val appliedBands: List<FrequencyBand>,
    val sourceRecordingId: Long?,
)

fun RecordingEntity.toDomain() = Recording(
    id = id,
    title = title,
    filePath = filePath,
    format = format,
    createdAtMs = createdAtMs,
    durationMs = durationMs,
    sampleRate = sampleRate,
    appliedBands = appliedBands,
    sourceRecordingId = sourceRecordingId,
)

fun Recording.toEntity() = RecordingEntity(
    id = id,
    title = title,
    filePath = filePath,
    format = format,
    createdAtMs = createdAtMs,
    durationMs = durationMs,
    sampleRate = sampleRate,
    appliedBands = appliedBands,
    sourceRecordingId = sourceRecordingId,
)
