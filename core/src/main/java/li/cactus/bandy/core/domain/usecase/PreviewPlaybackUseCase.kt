package li.cactus.bandy.core.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.repository.LivePreviewPlayer

interface PreviewPlaybackUseCase {
    /** [settings] null plays the raw source ("before"); non-null plays through the live biquad filter ("after"). */
    fun play(filePath: String, settings: FilterSettings?, loop: Boolean = false)
    fun stop()
    /** Hot-swaps the filter of the current "after" session without restarting playback position. */
    fun updateFilter(settings: FilterSettings)
    val isPlaying: StateFlow<Boolean>
}

internal class PreviewPlaybackUseCaseImpl(
    private val player: LivePreviewPlayer,
) : PreviewPlaybackUseCase {
    override fun play(filePath: String, settings: FilterSettings?, loop: Boolean) =
        player.play(filePath, settings, loop)
    override fun stop() = player.stop()
    override fun updateFilter(settings: FilterSettings) = player.updateFilter(settings)
    override val isPlaying: StateFlow<Boolean> get() = player.isPlaying
}
