package li.cactus.bandy.core.domain.usecase

import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.repository.LivePreviewPlayer

interface PreviewPlaybackUseCase {
    /** [settings] null plays the raw source ("before"); non-null plays through the live biquad filter ("after"). */
    fun play(filePath: String, settings: FilterSettings?)
    fun stop()
    val isPlaying: Boolean
}

internal class PreviewPlaybackUseCaseImpl(
    private val player: LivePreviewPlayer,
) : PreviewPlaybackUseCase {
    override fun play(filePath: String, settings: FilterSettings?) = player.play(filePath, settings)
    override fun stop() = player.stop()
    override val isPlaying: Boolean get() = player.isPlaying
}
