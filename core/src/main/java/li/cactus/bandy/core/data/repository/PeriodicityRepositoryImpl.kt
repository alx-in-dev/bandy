package li.cactus.bandy.core.data.repository

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.cactus.bandy.core.data.audio.OfflineBandPassFilter
import li.cactus.bandy.core.data.audio.PeriodicityAnalyzer
import li.cactus.bandy.core.data.audio.PeriodicityCombFilter
import li.cactus.bandy.core.data.audio.WavFileReader
import li.cactus.bandy.core.data.audio.WavFileWriter
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.PeriodicityAnalysis
import li.cactus.bandy.core.domain.repository.PeriodicityRepository
import li.cactus.bandy.core.domain.repository.StorageRepository

internal class PeriodicityRepositoryImpl(
    private val storageRepository: StorageRepository,
) : PeriodicityRepository {

    private val bandPassFilter = OfflineBandPassFilter()
    private val combFilter = PeriodicityCombFilter()
    private val analyzer = PeriodicityAnalyzer()

    override suspend fun analyze(filePath: String, band: FrequencyBand, order: Int): PeriodicityAnalysis =
        withContext(Dispatchers.Default) {
            val reader = WavFileReader(File(filePath))
            val bandLimited = bandPassFilter.process(
                reader.readAllSamples(),
                reader.sampleRate,
                FilterSettings(bands = listOf(band), butterworthOrder = order),
            )
            analyzer.analyze(bandLimited, reader.sampleRate)
        }

    override suspend fun applyCombFilter(
        filePath: String,
        band: FrequencyBand,
        order: Int,
        periodMs: Float,
        harmonics: Int,
    ): String = withContext(Dispatchers.Default) {
        val reader = WavFileReader(File(filePath))
        val filtered = combFilter.process(reader.readAllSamples(), reader.sampleRate, band, order, periodMs, harmonics)
        writeWav(filtered, reader.sampleRate, storageRepository.newRecordingFile("wav"))
    }

    override suspend fun buildSynchronousAverage(
        filePath: String,
        band: FrequencyBand,
        order: Int,
        periodMs: Float,
        impulseTimestampsMs: List<Float>,
    ): String = withContext(Dispatchers.Default) {
        val reader = WavFileReader(File(filePath))
        val bandLimited = bandPassFilter.process(
            reader.readAllSamples(),
            reader.sampleRate,
            FilterSettings(bands = listOf(band), butterworthOrder = order),
        )
        val template = analyzer.buildSynchronousAverage(bandLimited, reader.sampleRate, periodMs, impulseTimestampsMs)
        writeWav(template, reader.sampleRate, storageRepository.newTempFile("wav"))
    }

    private fun writeWav(samples: ShortArray, sampleRate: Int, outFile: File): String {
        val writer = WavFileWriter(outFile, sampleRate)
        writer.open()
        writer.writeSamples(samples)
        writer.finish()
        return outFile.path
    }
}
