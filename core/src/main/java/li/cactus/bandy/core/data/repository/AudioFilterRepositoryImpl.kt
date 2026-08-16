package li.cactus.bandy.core.data.repository

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.cactus.bandy.core.data.audio.OfflineBandPassFilter
import li.cactus.bandy.core.data.audio.WavFileReader
import li.cactus.bandy.core.data.audio.WavFileWriter
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.repository.AudioFilterRepository
import li.cactus.bandy.core.domain.repository.StorageRepository

internal class AudioFilterRepositoryImpl(
    private val storageRepository: StorageRepository,
) : AudioFilterRepository {

    private val filter = OfflineBandPassFilter()

    override suspend fun applyBandPass(sourceFilePath: String, settings: FilterSettings): String =
        withContext(Dispatchers.Default) {
            val reader = WavFileReader(File(sourceFilePath))
            val samples = reader.readAllSamples()
            val filtered = filter.process(samples, reader.sampleRate, settings)

            val outFile = storageRepository.newRecordingFile("wav")
            val writer = WavFileWriter(outFile, reader.sampleRate)
            writer.open()
            writer.writeSamples(filtered)
            writer.finish()

            outFile.path
        }
}
