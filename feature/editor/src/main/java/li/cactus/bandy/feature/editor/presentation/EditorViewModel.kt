package li.cactus.bandy.feature.editor.presentation

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.usecase.ApplyBandPassFilterUseCase
import li.cactus.bandy.core.domain.usecase.GetAveragedSpectrumUseCase
import li.cactus.bandy.core.domain.usecase.GetRecordingUseCase
import li.cactus.bandy.core.domain.usecase.PreviewPlaybackUseCase
import li.cactus.bandy.core.mvi.MVIBaseViewModel

private const val FFT_WINDOW_SIZE = 2048
private const val MIN_BAND_WIDTH_HZ = 20

internal class EditorViewModel(
    private val recordingId: Long,
    private val getRecordingUseCase: GetRecordingUseCase,
    private val getAveragedSpectrumUseCase: GetAveragedSpectrumUseCase,
    private val applyBandPassFilterUseCase: ApplyBandPassFilterUseCase,
    private val previewPlaybackUseCase: PreviewPlaybackUseCase,
) : MVIBaseViewModel<EditorScreenState, EditorScreenAction, EditorScreenEvent>(
    initialState = EditorScreenState(),
) {

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val recording = getRecordingUseCase(recordingId)
            if (recording == null) {
                setState { copy(isLoading = false, errorLoading = true) }
                return@launch
            }
            setState {
                copy(
                    recording = recording,
                    bands = recording.appliedBands.sortedBy { it.lowHz },
                )
            }
            val spectrum = getAveragedSpectrumUseCase(recording.filePath, FFT_WINDOW_SIZE)
            setState { copy(isLoading = false, spectrum = spectrum) }
        }
    }

    override fun obtainEvent(viewEvent: EditorScreenEvent) {
        when (viewEvent) {
            is EditorScreenEvent.ScaleChanged -> setState { copy(scale = viewEvent.scale) }

            is EditorScreenEvent.AddBand -> addBand()

            is EditorScreenEvent.RemoveBand -> {
                setState {
                    copy(
                        bands = bands.filterIndexed { i, _ -> i != viewEvent.index },
                        selectedBandIndex = null,
                    )
                }
                restartPreviewIfActive()
            }

            is EditorScreenEvent.BandChanged -> changeBand(viewEvent.index, viewEvent.lowHz, viewEvent.highHz)

            is EditorScreenEvent.BandMoved -> moveBand(viewEvent.index, viewEvent.lowHz)

            is EditorScreenEvent.BandSelected -> setState { copy(selectedBandIndex = viewEvent.index) }

            is EditorScreenEvent.ApplyVoicePreset -> setBands(listOf(clampToSpectrum(FrequencyBand.Presets.VOICE)))

            is EditorScreenEvent.ApplyBassPreset -> setBands(listOf(clampToSpectrum(FrequencyBand.Presets.BASS)))

            is EditorScreenEvent.ResetBands -> setBands(emptyList())

            is EditorScreenEvent.ButterworthOrderChanged -> {
                setState { copy(butterworthOrder = viewEvent.order.coerceIn(2, 8)) }
                restartPreviewIfActive()
            }

            is EditorScreenEvent.PreviewModeChanged -> setPreviewMode(viewEvent.mode)

            is EditorScreenEvent.LoopPreviewChanged -> {
                setState { copy(loopPreview = viewEvent.enabled) }
                restartPreviewIfActive(alsoRestartBefore = true)
            }

            is EditorScreenEvent.AlsoExportAacChanged -> setState { copy(alsoExportAac = viewEvent.enabled) }

            is EditorScreenEvent.Save -> save()

            is EditorScreenEvent.StopPreview -> stopPreview()
        }
    }

    private fun setBands(bands: List<FrequencyBand>) {
        setState { copy(bands = bands.sortedBy { it.lowHz }) }
        restartPreviewIfActive()
    }

    private fun addBand() {
        val nyquist = currentState.nyquistHz
        if (nyquist <= MIN_BAND_WIDTH_HZ) return
        val gap = findFreeGap(currentState.bands, nyquist) ?: return
        setBands(currentState.bands + gap)
    }

    private fun changeBand(index: Int, lowHz: Int, highHz: Int) {
        val bands = currentState.bands
        if (index !in bands.indices) return
        val nyquist = currentState.nyquistHz
        val others = bands.filterIndexed { i, _ -> i != index }.sortedBy { it.lowHz }

        val lowerBound = others.filter { it.highHz <= highHz }.maxOfOrNull { it.highHz } ?: 0
        val upperBound = others.filter { it.lowHz >= lowHz }.minOfOrNull { it.lowHz } ?: nyquist

        val clampedLow = lowHz.coerceIn(lowerBound, (highHz - MIN_BAND_WIDTH_HZ).coerceAtLeast(lowerBound))
        val clampedHigh = highHz.coerceIn((clampedLow + MIN_BAND_WIDTH_HZ).coerceAtMost(upperBound), upperBound)
        if (clampedHigh - clampedLow < MIN_BAND_WIDTH_HZ) return

        val updated = bands.mapIndexed { i, band ->
            if (i == index) FrequencyBand(clampedLow, clampedHigh) else band
        }
        setState { copy(bands = updated) }
        restartPreviewIfActive()
    }

    /** Translates a band (preserving its width) to a new low edge, clamped against neighbors. */
    private fun moveBand(index: Int, targetLowHz: Int) {
        val bands = currentState.bands
        val band = bands.getOrNull(index) ?: return
        val width = band.highHz - band.lowHz
        val nyquist = currentState.nyquistHz
        val others = bands.filterIndexed { i, _ -> i != index }

        val leftBound = others.filter { it.highHz <= band.lowHz }.maxOfOrNull { it.highHz } ?: 0
        val rightBound = others.filter { it.lowHz >= band.highHz }.minOfOrNull { it.lowHz } ?: nyquist

        val newLow = targetLowHz.coerceIn(leftBound, (rightBound - width).coerceAtLeast(leftBound))
        val newHigh = newLow + width

        val updated = bands.mapIndexed { i, b -> if (i == index) FrequencyBand(newLow, newHigh) else b }
        setState { copy(bands = updated) }
        restartPreviewIfActive()
    }

    private fun setPreviewMode(mode: PreviewMode) {
        val recording = currentState.recording ?: return
        previewPlaybackUseCase.stop()
        val loop = currentState.loopPreview
        when (mode) {
            PreviewMode.OFF -> Unit
            PreviewMode.BEFORE -> previewPlaybackUseCase.play(recording.filePath, null, loop)
            PreviewMode.AFTER -> previewPlaybackUseCase.play(recording.filePath, currentFilterSettings(), loop)
        }
        setState { copy(previewMode = mode) }
    }

    /** Bands/order changes only need to restart an active AFTER preview; [alsoRestartBefore] additionally
     * restarts BEFORE, used when only the loop flag changed and BEFORE playback should pick it up live. */
    private fun restartPreviewIfActive(alsoRestartBefore: Boolean = false) {
        val mode = currentState.previewMode
        if (mode == PreviewMode.AFTER || (alsoRestartBefore && mode == PreviewMode.BEFORE)) {
            setPreviewMode(mode)
        }
    }

    private fun stopPreview() {
        previewPlaybackUseCase.stop()
        setState { copy(previewMode = PreviewMode.OFF) }
    }

    private fun save() {
        val recording = currentState.recording ?: return
        if (currentState.isSaving) return
        previewPlaybackUseCase.stop()
        setState { copy(previewMode = PreviewMode.OFF, isSaving = true) }
        viewModelScope.launch {
            applyBandPassFilterUseCase(recording, currentFilterSettings(), currentState.alsoExportAac)
            setState { copy(isSaving = false) }
            sendAction(EditorScreenAction.NavigateToLibrary)
        }
    }

    private fun currentFilterSettings(): FilterSettings =
        FilterSettings(bands = currentState.bands, butterworthOrder = currentState.butterworthOrder)

    private fun clampToSpectrum(band: FrequencyBand): FrequencyBand {
        val nyquist = currentState.nyquistHz.coerceAtLeast(MIN_BAND_WIDTH_HZ)
        val high = band.highHz.coerceIn(MIN_BAND_WIDTH_HZ, nyquist)
        val low = band.lowHz.coerceIn(0, high - MIN_BAND_WIDTH_HZ)
        return FrequencyBand(low, high)
    }

    private fun findFreeGap(bands: List<FrequencyBand>, nyquist: Int): FrequencyBand? {
        val sorted = bands.sortedBy { it.lowHz }
        var cursor = 0
        val minGap = MIN_BAND_WIDTH_HZ * 2
        for (band in sorted) {
            if (band.lowHz - cursor >= minGap) {
                val low = cursor
                val high = (band.lowHz).coerceAtMost(low + defaultBandWidth(nyquist))
                return FrequencyBand(low, high)
            }
            cursor = maxOf(cursor, band.highHz)
        }
        if (nyquist - cursor >= minGap) {
            val low = cursor
            val high = (low + defaultBandWidth(nyquist)).coerceAtMost(nyquist)
            return FrequencyBand(low, high)
        }
        return null
    }

    private fun defaultBandWidth(nyquist: Int): Int = (nyquist / 5).coerceAtLeast(MIN_BAND_WIDTH_HZ * 2)

    override fun onCleared() {
        previewPlaybackUseCase.stop()
        super.onCleared()
    }
}
