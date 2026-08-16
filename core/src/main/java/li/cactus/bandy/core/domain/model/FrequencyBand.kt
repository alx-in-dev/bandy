package li.cactus.bandy.core.domain.model

data class FrequencyBand(
    val lowHz: Int,
    val highHz: Int,
) {
    init {
        require(lowHz in 0..highHz) { "lowHz ($lowHz) must be in 0..highHz ($highHz)" }
    }

    companion object Presets {
        val VOICE = FrequencyBand(300, 3400)
        val BASS = FrequencyBand(20, 250)
    }
}

data class FilterSettings(
    val bands: List<FrequencyBand>,
    val butterworthOrder: Int = 4,
) {
    init {
        require(butterworthOrder in 2..8) { "butterworthOrder must be in 2..8" }
    }

    val isFullSpectrum: Boolean get() = bands.isEmpty()
}
