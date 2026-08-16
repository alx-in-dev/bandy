package li.cactus.bandy.core.domain.repository

import li.cactus.bandy.core.domain.model.FilterSettings

interface AudioFilterRepository {
    /**
     * Applies an offline FFT band-pass (STFT + overlap-add, Butterworth-shaped per-bin gain)
     * to [sourceFilePath] and writes the result to a new WAV file. Returns the output path.
     */
    suspend fun applyBandPass(sourceFilePath: String, settings: FilterSettings): String
}

interface LivePreviewPlayer {
    /**
     * Streams [sourceFilePath] through AudioTrack, optionally through a live biquad band-pass
     * bank. When [loop] is true, playback restarts from the beginning on reaching the end,
     * until [stop] is called.
     */
    fun play(sourceFilePath: String, settings: FilterSettings?, loop: Boolean = false)
    fun stop()
    val isPlaying: Boolean
}
