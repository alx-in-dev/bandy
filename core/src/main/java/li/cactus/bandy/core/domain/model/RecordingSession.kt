package li.cactus.bandy.core.domain.model

enum class RecordingPhase {
    IDLE,
    RECORDING,
    PAUSED,
}

data class RecordingSession(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val elapsedMs: Long = 0L,
    val vuLevel: Float = 0f,
    val lowStorageWarning: Boolean = false,
)

enum class SampleRateOption(val hz: Int) {
    SR_44100(44_100),
    SR_48000(48_000),
}

enum class FftWindowSize(val samples: Int) {
    SIZE_1024(1024),
    SIZE_2048(2048),
    SIZE_4096(4096),
}

data class AudioSettings(
    val sampleRate: SampleRateOption = SampleRateOption.SR_44100,
    val fftWindowSize: FftWindowSize = FftWindowSize.SIZE_2048,
)
