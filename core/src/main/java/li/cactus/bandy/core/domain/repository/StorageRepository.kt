package li.cactus.bandy.core.domain.repository

import java.io.File
import kotlinx.coroutines.flow.Flow
import li.cactus.bandy.core.domain.model.AudioSettings
import li.cactus.bandy.core.domain.model.FftWindowSize
import li.cactus.bandy.core.domain.model.SampleRateOption

interface StorageRepository {
    fun recordingsDir(): File
    fun newRecordingFile(extension: String): File
    /** Scratch file outside the recordings dir (e.g. short preview clips) — not part of the library. */
    fun newTempFile(extension: String): File
    fun freeSpaceBytes(): Long
    fun isFreeSpaceLow(): Boolean
    fun delete(filePath: String)
}

interface SettingsRepository {
    val settings: Flow<AudioSettings>
    suspend fun setSampleRate(option: SampleRateOption)
    suspend fun setFftWindowSize(size: FftWindowSize)
}
