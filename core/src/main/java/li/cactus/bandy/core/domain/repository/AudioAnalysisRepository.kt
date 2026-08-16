package li.cactus.bandy.core.domain.repository

import li.cactus.bandy.core.domain.model.AveragedSpectrum

interface AudioAnalysisRepository {
    /** Computes the averaged magnitude spectrum of a WAV file, for band-selection UI. */
    suspend fun getAveragedSpectrum(filePath: String, fftWindowSize: Int): AveragedSpectrum
}
