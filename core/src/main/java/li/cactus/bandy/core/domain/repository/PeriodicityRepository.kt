package li.cactus.bandy.core.domain.repository

import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.PeriodicityAnalysis

interface PeriodicityRepository {
    /** Band-limits [filePath] to [band] and looks for a dominant repeating pattern in it. */
    suspend fun analyze(filePath: String, band: FrequencyBand, order: Int): PeriodicityAnalysis

    /**
     * Full-length WAV, band-limited to [band], with harmonics of [periodMs]'s fundamental
     * emphasized and non-periodic energy suppressed. Returns the output path.
     */
    suspend fun applyCombFilter(filePath: String, band: FrequencyBand, order: Int, periodMs: Float, harmonics: Int): String

    /**
     * Short single-cycle WAV built by averaging a [periodMs]-long window around each of
     * [impulseTimestampsMs] (band-limited to [band] first) — non-periodic noise cancels out,
     * leaving roughly just the repeating signal. Meant to be looped for close listening, not
     * saved to the library. Returns the temp file path.
     */
    suspend fun buildSynchronousAverage(
        filePath: String,
        band: FrequencyBand,
        order: Int,
        periodMs: Float,
        impulseTimestampsMs: List<Float>,
    ): String
}
