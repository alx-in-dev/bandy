package li.cactus.bandy.core.data.repository

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.cactus.bandy.core.data.audio.SpectrumAnalyzer
import li.cactus.bandy.core.data.audio.WavFileReader
import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.repository.AudioAnalysisRepository

internal class AudioAnalysisRepositoryImpl : AudioAnalysisRepository {
    override suspend fun getAveragedSpectrum(filePath: String, fftWindowSize: Int): AveragedSpectrum =
        withContext(Dispatchers.Default) {
            val reader = WavFileReader(File(filePath))
            val samples = reader.readAllSamples()
            SpectrumAnalyzer(fftWindowSize).averagedSpectrum(samples, reader.sampleRate)
        }
}
