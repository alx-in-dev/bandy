package li.cactus.bandy.core.data.storage

import androidx.room.TypeConverter
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.FrequencyBand

/** Bands are encoded as "lowHz-highHz,lowHz-highHz" to avoid pulling in a JSON dependency. */
class RecordingConverters {

    @TypeConverter
    fun fromBands(bands: List<FrequencyBand>): String =
        bands.joinToString(separator = ",") { "${it.lowHz}-${it.highHz}" }

    @TypeConverter
    fun toBands(raw: String): List<FrequencyBand> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { pair ->
            val (low, high) = pair.split("-")
            FrequencyBand(low.trim().toInt(), high.trim().toInt())
        }
    }

    @TypeConverter
    fun fromFormat(format: AudioFileFormat): String = format.name

    @TypeConverter
    fun toFormat(raw: String): AudioFileFormat = AudioFileFormat.valueOf(raw)
}
