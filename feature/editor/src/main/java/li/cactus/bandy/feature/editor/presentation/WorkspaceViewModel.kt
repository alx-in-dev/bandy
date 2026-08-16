package li.cactus.bandy.feature.editor.presentation

import androidx.lifecycle.viewModelScope
import kotlin.math.ln
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import li.cactus.bandy.core.domain.model.FilterSettings
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.model.SpectrumFrame
import li.cactus.bandy.core.domain.usecase.AnalyzePeriodicityUseCase
import li.cactus.bandy.core.domain.usecase.ApplyBandPassFilterUseCase
import li.cactus.bandy.core.domain.usecase.ApplyCombFilterUseCase
import li.cactus.bandy.core.domain.usecase.BuildSynchronousAverageUseCase
import li.cactus.bandy.core.domain.usecase.GetAveragedSpectrumUseCase
import li.cactus.bandy.core.domain.usecase.GetRecordingUseCase
import li.cactus.bandy.core.domain.usecase.GetSpectrogramUseCase
import li.cactus.bandy.core.domain.usecase.ObserveAudioSettingsUseCase
import li.cactus.bandy.core.domain.usecase.ObserveLiveSpectrumUseCase
import li.cactus.bandy.core.domain.usecase.ObserveRecordingSessionUseCase
import li.cactus.bandy.core.domain.usecase.PreviewPlaybackUseCase
import li.cactus.bandy.core.domain.usecase.RecordingControlUseCase
import li.cactus.bandy.core.domain.usecase.UpdateAudioSettingsUseCase
import li.cactus.bandy.core.mvi.MVIBaseViewModel

private const val FFT_WINDOW_SIZE = 2048
private const val MIN_BAND_WIDTH_HZ = 20
private const val MAX_WATERFALL_FRAMES = 150
private const val WATERFALL_COLUMNS = 128
private const val PEAK_DECAY = 0.999f

/** [initialRecordingId] < 0 starts fresh in the recording phase; >= 0 opens directly in the
 * editing phase for that existing recording (re-filter flow from the library). */
internal class WorkspaceViewModel(
    private val initialRecordingId: Long,
    private val recordingControl: RecordingControlUseCase,
    observeSession: ObserveRecordingSessionUseCase,
    observeLiveSpectrum: ObserveLiveSpectrumUseCase,
    observeAudioSettings: ObserveAudioSettingsUseCase,
    private val updateAudioSettings: UpdateAudioSettingsUseCase,
    private val getRecordingUseCase: GetRecordingUseCase,
    private val getAveragedSpectrumUseCase: GetAveragedSpectrumUseCase,
    private val getSpectrogramUseCase: GetSpectrogramUseCase,
    private val applyBandPassFilterUseCase: ApplyBandPassFilterUseCase,
    private val previewPlaybackUseCase: PreviewPlaybackUseCase,
    private val analyzePeriodicityUseCase: AnalyzePeriodicityUseCase,
    private val applyCombFilterUseCase: ApplyCombFilterUseCase,
    private val buildSynchronousAverageUseCase: BuildSynchronousAverageUseCase,
) : MVIBaseViewModel<WorkspaceScreenState, WorkspaceScreenAction, WorkspaceScreenEvent>(
    initialState = WorkspaceScreenState(
        phase = if (initialRecordingId >= 0) WorkspacePhase.EDITING else WorkspacePhase.RECORDING,
    ),
) {

    private val ring = ArrayDeque<FloatArray>(MAX_WATERFALL_FRAMES)
    private var spectrumPeak = 1f

    private val _spectrumFrames = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrumFrames: StateFlow<List<FloatArray>> = _spectrumFrames.asStateFlow()

    init {
        if (initialRecordingId >= 0) {
            loadRecordingForEditing(initialRecordingId)
        } else {
            viewModelScope.launch {
                observeSession().collect { session -> setState { copy(session = session) } }
            }
            viewModelScope.launch {
                observeAudioSettings().collect { settings -> setState { copy(audioSettings = settings) } }
            }
            viewModelScope.launch {
                observeLiveSpectrum().collect { frame -> pushSpectrum(frame) }
            }
        }
        // The player is shared across screens (e.g. library quick-play) — if something else
        // stops or takes over playback, drop our stale "now playing" UI state too.
        viewModelScope.launch {
            previewPlaybackUseCase.isPlaying.collect { playing ->
                if (!playing && (currentState.previewMode != PreviewMode.OFF || currentState.isPlayingSyncAverage)) {
                    setState { copy(previewMode = PreviewMode.OFF, isPlayingSyncAverage = false) }
                }
            }
        }
    }

    override fun obtainEvent(viewEvent: WorkspaceScreenEvent) {
        when (viewEvent) {
            WorkspaceScreenEvent.OnStartClick -> onStart()
            WorkspaceScreenEvent.OnPauseClick -> recordingControl.pause()
            WorkspaceScreenEvent.OnResumeClick -> recordingControl.resume()
            WorkspaceScreenEvent.OnStopClick -> onStop()
            WorkspaceScreenEvent.OnPermissionDenied -> setState { copy(permissionDenied = true) }
            WorkspaceScreenEvent.OnOpenSettings -> setState { copy(isSettingsSheetOpen = true) }
            WorkspaceScreenEvent.OnCloseSettings -> setState { copy(isSettingsSheetOpen = false) }
            is WorkspaceScreenEvent.OnSampleRateSelected -> viewModelScope.launch {
                updateAudioSettings.setSampleRate(viewEvent.option)
            }
            is WorkspaceScreenEvent.OnFftWindowSelected -> viewModelScope.launch {
                updateAudioSettings.setFftWindowSize(viewEvent.size)
            }

            is WorkspaceScreenEvent.ChartStyleChanged -> setChartStyle(viewEvent.style)

            is WorkspaceScreenEvent.ScaleChanged -> setState { copy(scale = viewEvent.scale) }

            is WorkspaceScreenEvent.AddBand -> addBand()

            is WorkspaceScreenEvent.RemoveBand -> {
                setState {
                    copy(
                        bands = bands.filterIndexed { i, _ -> i != viewEvent.index },
                        selectedBandIndex = null,
                        periodicityAnalysis = null,
                    )
                }
                applyLiveFilterUpdate()
            }

            is WorkspaceScreenEvent.BandChanged -> changeBand(viewEvent.index, viewEvent.lowHz, viewEvent.highHz)

            is WorkspaceScreenEvent.BandMoved -> moveBand(viewEvent.index, viewEvent.lowHz)

            is WorkspaceScreenEvent.BandSelected -> setState {
                copy(selectedBandIndex = viewEvent.index, periodicityAnalysis = null)
            }

            is WorkspaceScreenEvent.ApplyVoicePreset -> setBands(listOf(clampToSpectrum(FrequencyBand.Presets.VOICE)))

            is WorkspaceScreenEvent.ApplyBassPreset -> setBands(listOf(clampToSpectrum(FrequencyBand.Presets.BASS)))

            is WorkspaceScreenEvent.ResetBands -> setBands(emptyList())

            is WorkspaceScreenEvent.ButterworthOrderChanged -> {
                setState { copy(butterworthOrder = viewEvent.order.coerceIn(2, 8), periodicityAnalysis = null) }
                applyLiveFilterUpdate()
            }

            is WorkspaceScreenEvent.PreviewModeChanged -> setPreviewMode(viewEvent.mode)

            is WorkspaceScreenEvent.LoopPreviewChanged -> {
                setState { copy(loopPreview = viewEvent.enabled) }
                restartPreviewIfActive(alsoRestartBefore = true)
            }

            is WorkspaceScreenEvent.AlsoExportAacChanged -> setState { copy(alsoExportAac = viewEvent.enabled) }

            is WorkspaceScreenEvent.Save -> save()

            is WorkspaceScreenEvent.StopPreview -> stopPreview()

            is WorkspaceScreenEvent.AnalyzePeriodicity -> analyzePeriodicity()

            is WorkspaceScreenEvent.PeriodicityHarmonicsChanged ->
                setState { copy(periodicityHarmonics = viewEvent.harmonics.coerceIn(1, 12)) }

            is WorkspaceScreenEvent.ApplyCombFilter -> applyCombFilter()

            is WorkspaceScreenEvent.PreviewSynchronousAverage -> previewSynchronousAverage()
        }
    }

    // ---- Recording phase ----

    private fun onStart() {
        setState { copy(permissionDenied = false) }
        clearSpectrum()
        viewModelScope.launch { recordingControl.start() }
    }

    private fun onStop() {
        viewModelScope.launch {
            val recording = recordingControl.stop()
            clearSpectrum()
            enterEditingPhase(recording)
        }
    }

    private fun pushSpectrum(frame: SpectrumFrame) {
        val column = bucketToColumns(frame.magnitudes)
        var frameMax = 0f
        for (v in column) frameMax = max(frameMax, v)
        spectrumPeak = max(spectrumPeak * PEAK_DECAY, frameMax).coerceAtLeast(1e-6f)

        val denom = ln(1f + spectrumPeak)
        val normalized = FloatArray(column.size) { i ->
            (ln(1f + column[i]) / denom).coerceIn(0f, 1f)
        }
        if (ring.size >= MAX_WATERFALL_FRAMES) ring.removeFirst()
        ring.addLast(normalized)
        _spectrumFrames.value = ring.toList()
    }

    private fun clearSpectrum() {
        ring.clear()
        spectrumPeak = 1f
        _spectrumFrames.value = emptyList()
    }

    private fun bucketToColumns(magnitudes: FloatArray): FloatArray {
        if (magnitudes.isEmpty()) return FloatArray(WATERFALL_COLUMNS)
        val out = FloatArray(WATERFALL_COLUMNS)
        val bins = magnitudes.size
        for (c in 0 until WATERFALL_COLUMNS) {
            val start = (c.toLong() * bins / WATERFALL_COLUMNS).toInt()
            val end = ((c + 1).toLong() * bins / WATERFALL_COLUMNS).toInt().coerceAtLeast(start + 1)
            var m = 0f
            var i = start
            while (i < end && i < bins) {
                m = max(m, magnitudes[i])
                i++
            }
            out[c] = m
        }
        return out
    }

    // ---- Editing phase ----

    private fun loadRecordingForEditing(id: Long) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            val recording = getRecordingUseCase(id)
            if (recording == null) {
                setState { copy(isLoading = false, errorLoading = true) }
                return@launch
            }
            enterEditingPhase(recording)
        }
    }

    private fun enterEditingPhase(recording: Recording) {
        setState {
            copy(
                phase = WorkspacePhase.EDITING,
                recording = recording,
                bands = recording.appliedBands.sortedBy { it.lowHz },
                isLoading = true,
            )
        }
        viewModelScope.launch {
            val spectrum = getAveragedSpectrumUseCase(recording.filePath, FFT_WINDOW_SIZE)
            setState { copy(isLoading = false, spectrum = spectrum) }
        }
    }

    private fun setChartStyle(style: ChartStyle) {
        setState { copy(chartStyle = style) }
        val recording = currentState.recording ?: return
        if (style == ChartStyle.SPECTROGRAM && currentState.spectrogram == null && !currentState.isLoadingSpectrogram) {
            setState { copy(isLoadingSpectrogram = true) }
            viewModelScope.launch {
                val spectrogram = getSpectrogramUseCase(recording.filePath, FFT_WINDOW_SIZE)
                setState { copy(isLoadingSpectrogram = false, spectrogram = spectrogram) }
            }
        }
    }

    private fun setBands(bands: List<FrequencyBand>) {
        setState { copy(bands = bands.sortedBy { it.lowHz }, selectedBandIndex = null, periodicityAnalysis = null) }
        applyLiveFilterUpdate()
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
        setState { copy(bands = updated, periodicityAnalysis = null) }
        applyLiveFilterUpdate()
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
        setState { copy(bands = updated, periodicityAnalysis = null) }
        applyLiveFilterUpdate()
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
        setState { copy(previewMode = mode, isPlayingSyncAverage = false) }
    }

    /** Used only for the loop-flag toggle, which can't be hot-swapped mid-stream; [alsoRestartBefore]
     * restarts BEFORE too so it picks up the new loop flag live, not just AFTER. */
    private fun restartPreviewIfActive(alsoRestartBefore: Boolean = false) {
        val mode = currentState.previewMode
        if (mode == PreviewMode.AFTER || (alsoRestartBefore && mode == PreviewMode.BEFORE)) {
            setPreviewMode(mode)
        }
    }

    /** Band/order edits hot-swap the filter of an active AFTER preview in place — no restart, so
     * the change is heard immediately instead of the playback jumping back to the start. */
    private fun applyLiveFilterUpdate() {
        if (currentState.previewMode == PreviewMode.AFTER) {
            previewPlaybackUseCase.updateFilter(currentFilterSettings())
        }
    }

    private fun stopPreview() {
        previewPlaybackUseCase.stop()
        setState { copy(previewMode = PreviewMode.OFF, isPlayingSyncAverage = false) }
    }

    private fun save() {
        val recording = currentState.recording ?: return
        if (currentState.isSaving) return
        previewPlaybackUseCase.stop()
        setState { copy(previewMode = PreviewMode.OFF, isPlayingSyncAverage = false, isSaving = true) }
        viewModelScope.launch {
            applyBandPassFilterUseCase(recording, currentFilterSettings(), currentState.alsoExportAac)
            setState { copy(isSaving = false) }
            sendAction(WorkspaceScreenAction.NavigateToLibrary)
        }
    }

    private fun analyzePeriodicity() {
        val recording = currentState.recording ?: return
        val band = currentState.periodicityBand ?: return
        if (currentState.isAnalyzingPeriodicity) return
        setState { copy(isAnalyzingPeriodicity = true, periodicityAnalysis = null) }
        viewModelScope.launch {
            val analysis = analyzePeriodicityUseCase(recording.filePath, band, currentState.butterworthOrder)
            setState { copy(isAnalyzingPeriodicity = false, periodicityAnalysis = analysis) }
        }
    }

    private fun applyCombFilter() {
        val recording = currentState.recording ?: return
        val band = currentState.periodicityBand ?: return
        val analysis = currentState.periodicityAnalysis?.takeIf { it.isPeriodic } ?: return
        if (currentState.isApplyingPeriodicity) return
        previewPlaybackUseCase.stop()
        setState { copy(previewMode = PreviewMode.OFF, isPlayingSyncAverage = false, isApplyingPeriodicity = true) }
        viewModelScope.launch {
            applyCombFilterUseCase(
                recording,
                band,
                currentState.butterworthOrder,
                analysis.periodMs,
                currentState.periodicityHarmonics,
            )
            setState { copy(isApplyingPeriodicity = false) }
            sendAction(WorkspaceScreenAction.NavigateToLibrary)
        }
    }

    private fun previewSynchronousAverage() {
        val recording = currentState.recording ?: return
        val band = currentState.periodicityBand ?: return
        val analysis = currentState.periodicityAnalysis?.takeIf { it.isPeriodic } ?: return
        viewModelScope.launch {
            val path = buildSynchronousAverageUseCase(recording, band, currentState.butterworthOrder, analysis)
            previewPlaybackUseCase.stop()
            previewPlaybackUseCase.play(path, null, loop = true)
            setState { copy(previewMode = PreviewMode.OFF, isPlayingSyncAverage = true) }
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
