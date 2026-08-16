package li.cactus.bandy.core.domain.repository

import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.model.SpectrumFrame

interface AudioAnalysisRepository {
    /** Computes the averaged magnitude spectrum of a WAV file, for band-selection UI. */
    suspend fun getAveragedSpectrum(filePath: String, fftWindowSize: Int): AveragedSpectrum

    /** Full-file time -> spectrum breakdown, for the spectrogram chart style. */
    suspend fun getSpectrogram(filePath: String, fftWindowSize: Int, targetFrames: Int = 400): List<SpectrumFrame>
}
