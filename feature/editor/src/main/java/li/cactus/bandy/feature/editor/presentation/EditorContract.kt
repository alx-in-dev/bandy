package li.cactus.bandy.feature.editor.presentation

import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.Recording

enum class FreqScale { LINEAR, LOG }

enum class PreviewMode { OFF, BEFORE, AFTER }

data class EditorScreenState(
    val isLoading: Boolean = true,
    val errorLoading: Boolean = false,
    val recording: Recording? = null,
    val spectrum: AveragedSpectrum? = null,
    val bands: List<FrequencyBand> = emptyList(),
    val selectedBandIndex: Int? = null,
    val scale: FreqScale = FreqScale.LOG,
    val butterworthOrder: Int = 4,
    val previewMode: PreviewMode = PreviewMode.OFF,
    val loopPreview: Boolean = false,
    val alsoExportAac: Boolean = false,
    val isSaving: Boolean = false,
) {
    val nyquistHz: Int get() = (recording?.sampleRate ?: 0) / 2
    val canSave: Boolean get() = recording != null && !isSaving
}

sealed interface EditorScreenEvent {
    data class ScaleChanged(val scale: FreqScale) : EditorScreenEvent
    data object AddBand : EditorScreenEvent
    data class RemoveBand(val index: Int) : EditorScreenEvent
    data class BandChanged(val index: Int, val lowHz: Int, val highHz: Int) : EditorScreenEvent
    data class BandMoved(val index: Int, val lowHz: Int) : EditorScreenEvent
    data class BandSelected(val index: Int?) : EditorScreenEvent
    data object ApplyVoicePreset : EditorScreenEvent
    data object ApplyBassPreset : EditorScreenEvent
    data object ResetBands : EditorScreenEvent
    data class ButterworthOrderChanged(val order: Int) : EditorScreenEvent
    data class PreviewModeChanged(val mode: PreviewMode) : EditorScreenEvent
    data class LoopPreviewChanged(val enabled: Boolean) : EditorScreenEvent
    data class AlsoExportAacChanged(val enabled: Boolean) : EditorScreenEvent
    data object Save : EditorScreenEvent
    data object StopPreview : EditorScreenEvent
}

sealed interface EditorScreenAction {
    data object NavigateToLibrary : EditorScreenAction
}
