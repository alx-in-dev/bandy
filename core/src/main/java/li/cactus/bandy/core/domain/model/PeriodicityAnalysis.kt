package li.cactus.bandy.core.domain.model

/**
 * Result of autocorrelation-based periodicity detection within a frequency band: a dominant
 * repetition period (e.g. an engine knock recurring once per combustion cycle), the individual
 * repeating impulses found (for on-screen markers), and the envelope they were found in.
 */
data class PeriodicityAnalysis(
    val periodMs: Float,
    /** Normalized autocorrelation strength at [periodMs], 0..1 — how convincingly periodic the signal is. */
    val confidence: Float,
    val impulseTimestampsMs: List<Float>,
    val envelope: FloatArray,
    val envelopeIntervalMs: Float,
) {
    val isPeriodic: Boolean get() = periodMs > 0f && impulseTimestampsMs.size >= 2
    val cyclesPerMinute: Float get() = if (periodMs <= 0f) 0f else 60_000f / periodMs

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeriodicityAnalysis) return false
        return periodMs == other.periodMs &&
            confidence == other.confidence &&
            impulseTimestampsMs == other.impulseTimestampsMs &&
            envelope.contentEquals(other.envelope) &&
            envelopeIntervalMs == other.envelopeIntervalMs
    }

    override fun hashCode(): Int {
        var result = periodMs.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + impulseTimestampsMs.hashCode()
        result = 31 * result + envelope.contentHashCode()
        result = 31 * result + envelopeIntervalMs.hashCode()
        return result
    }
}
