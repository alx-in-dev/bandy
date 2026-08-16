package li.cactus.bandy.core.domain.model

data class Recording(
    val id: Long = 0L,
    val title: String,
    val filePath: String,
    val format: AudioFileFormat,
    val createdAtMs: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val appliedBands: List<FrequencyBand>,
    val sourceRecordingId: Long? = null,
)

enum class AudioFileFormat {
    WAV,
    AAC,
}
