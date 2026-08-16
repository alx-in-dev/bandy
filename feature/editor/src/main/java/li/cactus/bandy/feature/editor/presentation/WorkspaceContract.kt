package li.cactus.bandy.feature.editor.presentation

import li.cactus.bandy.core.domain.model.AudioSettings
import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.model.FftWindowSize
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.PeriodicityAnalysis
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.model.RecordingSession
import li.cactus.bandy.core.domain.model.SampleRateOption
import li.cactus.bandy.core.domain.model.SpectrumFrame

/** A single screen: record a new take, then edit it in place — no navigation between the two. */
enum class WorkspacePhase { RECORDING, EDITING }

enum class FreqScale { LINEAR, LOG }

enum class PreviewMode { OFF, BEFORE, AFTER }

/** AVERAGED: frequency (X) vs amplitude (Y), one snapshot for the whole recording.
 * SPECTROGRAM: time (X) vs frequency (Y), heat-colored amplitude — same visual language as the
 * live waterfall on the recording phase. */
enum class ChartStyle { AVERAGED, SPECTROGRAM }

data class WorkspaceScreenState(
    val phase: WorkspacePhase = WorkspacePhase.RECORDING,

    // Recording phase
    val session: RecordingSession = RecordingSession(),
    val audioSettings: AudioSettings = AudioSettings(),
    val isSettingsSheetOpen: Boolean = false,
    val permissionDenied: Boolean = false,

    // Editing phase
    val isLoading: Boolean = false,
    val errorLoading: Boolean = false,
    val recording: Recording? = null,
    val spectrum: AveragedSpectrum? = null,
    val chartStyle: ChartStyle = ChartStyle.AVERAGED,
    val spectrogram: List<SpectrumFrame>? = null,
    val isLoadingSpectrogram: Boolean = false,
    val bands: List<FrequencyBand> = emptyList(),
    val selectedBandIndex: Int? = null,
    val scale: FreqScale = FreqScale.LOG,
    val butterworthOrder: Int = 4,
    val previewMode: PreviewMode = PreviewMode.OFF,
    val loopPreview: Boolean = false,
    val alsoExportAac: Boolean = false,
    val isSaving: Boolean = false,
    val periodicityAnalysis: PeriodicityAnalysis? = null,
    val isAnalyzingPeriodicity: Boolean = false,
    val isApplyingPeriodicity: Boolean = false,
    val isPlayingSyncAverage: Boolean = false,
    val periodicityHarmonics: Int = 6,
) {
    val nyquistHz: Int get() = (recording?.sampleRate ?: 0) / 2
    val canSave: Boolean get() = recording != null && !isSaving

    /** Periodicity tools operate on exactly one band: the selected one, or the only one if there's just one. */
    val periodicityBand: FrequencyBand? get() = selectedBandIndex?.let { bands.getOrNull(it) } ?: bands.singleOrNull()
}

sealed interface WorkspaceScreenEvent {
    // Recording phase
    data object OnStartClick : WorkspaceScreenEvent
    data object OnPauseClick : WorkspaceScreenEvent
    data object OnResumeClick : WorkspaceScreenEvent
    data object OnStopClick : WorkspaceScreenEvent
    data object OnPermissionDenied : WorkspaceScreenEvent
    data object OnOpenSettings : WorkspaceScreenEvent
    data object OnCloseSettings : WorkspaceScreenEvent
    data class OnSampleRateSelected(val option: SampleRateOption) : WorkspaceScreenEvent
    data class OnFftWindowSelected(val size: FftWindowSize) : WorkspaceScreenEvent

    // Editing phase
    data class ChartStyleChanged(val style: ChartStyle) : WorkspaceScreenEvent
    data class ScaleChanged(val scale: FreqScale) : WorkspaceScreenEvent
    data object AddBand : WorkspaceScreenEvent
    data class RemoveBand(val index: Int) : WorkspaceScreenEvent
    data class BandChanged(val index: Int, val lowHz: Int, val highHz: Int) : WorkspaceScreenEvent
    data class BandMoved(val index: Int, val lowHz: Int) : WorkspaceScreenEvent
    data class BandSelected(val index: Int?) : WorkspaceScreenEvent
    data object ApplyVoicePreset : WorkspaceScreenEvent
    data object ApplyBassPreset : WorkspaceScreenEvent
    data object ResetBands : WorkspaceScreenEvent
    data class ButterworthOrderChanged(val order: Int) : WorkspaceScreenEvent
    data class PreviewModeChanged(val mode: PreviewMode) : WorkspaceScreenEvent
    data class LoopPreviewChanged(val enabled: Boolean) : WorkspaceScreenEvent
    data class AlsoExportAacChanged(val enabled: Boolean) : WorkspaceScreenEvent
    data object Save : WorkspaceScreenEvent
    data object StopPreview : WorkspaceScreenEvent
    data object AnalyzePeriodicity : WorkspaceScreenEvent
    data class PeriodicityHarmonicsChanged(val harmonics: Int) : WorkspaceScreenEvent
    data object ApplyCombFilter : WorkspaceScreenEvent
    data object PreviewSynchronousAverage : WorkspaceScreenEvent
}

sealed interface WorkspaceScreenAction {
    data object NavigateToLibrary : WorkspaceScreenAction
}
