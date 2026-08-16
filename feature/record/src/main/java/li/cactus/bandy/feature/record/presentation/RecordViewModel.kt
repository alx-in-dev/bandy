package li.cactus.bandy.feature.record.presentation

import androidx.lifecycle.viewModelScope
import kotlin.math.ln
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import li.cactus.bandy.core.domain.model.SpectrumFrame
import li.cactus.bandy.core.domain.usecase.ObserveAudioSettingsUseCase
import li.cactus.bandy.core.domain.usecase.ObserveLiveSpectrumUseCase
import li.cactus.bandy.core.domain.usecase.ObserveRecordingSessionUseCase
import li.cactus.bandy.core.domain.usecase.RecordingControlUseCase
import li.cactus.bandy.core.domain.usecase.UpdateAudioSettingsUseCase
import li.cactus.bandy.core.mvi.MVIBaseViewModel

internal class RecordViewModel(
    private val recordingControl: RecordingControlUseCase,
    observeSession: ObserveRecordingSessionUseCase,
    observeLiveSpectrum: ObserveLiveSpectrumUseCase,
    observeAudioSettings: ObserveAudioSettingsUseCase,
    private val updateAudioSettings: UpdateAudioSettingsUseCase,
) : MVIBaseViewModel<RecordScreenState, RecordScreenAction, RecordScreenEvent>(
    initialState = RecordScreenState(),
) {

    private val ring = ArrayDeque<FloatArray>(MAX_FRAMES)
    private var spectrumPeak = 1f

    private val _spectrumFrames = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrumFrames: StateFlow<List<FloatArray>> = _spectrumFrames.asStateFlow()

    init {
        viewModelScope.launch {
            observeSession().collect { session -> setState { copy(session = session) } }
        }
        viewModelScope.launch {
            observeAudioSettings().collect { settings -> setState { copy(settings = settings) } }
        }
        viewModelScope.launch {
            observeLiveSpectrum().collect { frame -> pushSpectrum(frame) }
        }
    }

    override fun obtainEvent(viewEvent: RecordScreenEvent) {
        when (viewEvent) {
            RecordScreenEvent.OnStartClick -> onStart()
            RecordScreenEvent.OnPauseClick -> recordingControl.pause()
            RecordScreenEvent.OnResumeClick -> recordingControl.resume()
            RecordScreenEvent.OnStopClick -> onStop()
            RecordScreenEvent.OnPermissionDenied -> setState { copy(permissionDenied = true) }
            RecordScreenEvent.OnOpenSettings -> setState { copy(isSettingsSheetOpen = true) }
            RecordScreenEvent.OnCloseSettings -> setState { copy(isSettingsSheetOpen = false) }
            is RecordScreenEvent.OnSampleRateSelected -> viewModelScope.launch {
                updateAudioSettings.setSampleRate(viewEvent.option)
            }
            is RecordScreenEvent.OnFftWindowSelected -> viewModelScope.launch {
                updateAudioSettings.setFftWindowSize(viewEvent.size)
            }
        }
    }

    private fun onStart() {
        setState { copy(permissionDenied = false) }
        clearSpectrum()
        viewModelScope.launch { recordingControl.start() }
    }

    private fun onStop() {
        viewModelScope.launch {
            val recording = recordingControl.stop()
            clearSpectrum()
            sendAction(RecordScreenAction.NavigateToEditor(recording.id))
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
        if (ring.size >= MAX_FRAMES) ring.removeFirst()
        ring.addLast(normalized)
        _spectrumFrames.value = ring.toList()
    }

    private fun clearSpectrum() {
        ring.clear()
        spectrumPeak = 1f
        _spectrumFrames.value = emptyList()
    }

    private fun bucketToColumns(magnitudes: FloatArray): FloatArray {
        if (magnitudes.isEmpty()) return FloatArray(COLUMNS)
        val out = FloatArray(COLUMNS)
        val bins = magnitudes.size
        for (c in 0 until COLUMNS) {
            val start = (c.toLong() * bins / COLUMNS).toInt()
            val end = ((c + 1).toLong() * bins / COLUMNS).toInt().coerceAtLeast(start + 1)
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

    private companion object {
        const val MAX_FRAMES = 150
        const val COLUMNS = 128
        const val PEAK_DECAY = 0.999f
    }
}
