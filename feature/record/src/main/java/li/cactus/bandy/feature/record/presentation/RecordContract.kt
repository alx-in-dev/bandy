package li.cactus.bandy.feature.record.presentation

import li.cactus.bandy.core.domain.model.AudioSettings
import li.cactus.bandy.core.domain.model.FftWindowSize
import li.cactus.bandy.core.domain.model.RecordingSession
import li.cactus.bandy.core.domain.model.SampleRateOption

data class RecordScreenState(
    val session: RecordingSession = RecordingSession(),
    val settings: AudioSettings = AudioSettings(),
    val isSettingsSheetOpen: Boolean = false,
    val permissionDenied: Boolean = false,
)

sealed interface RecordScreenAction {
    data class NavigateToEditor(val recordingId: Long) : RecordScreenAction
}

sealed interface RecordScreenEvent {
    data object OnStartClick : RecordScreenEvent
    data object OnPauseClick : RecordScreenEvent
    data object OnResumeClick : RecordScreenEvent
    data object OnStopClick : RecordScreenEvent
    data object OnPermissionDenied : RecordScreenEvent
    data object OnOpenSettings : RecordScreenEvent
    data object OnCloseSettings : RecordScreenEvent
    data class OnSampleRateSelected(val option: SampleRateOption) : RecordScreenEvent
    data class OnFftWindowSelected(val size: FftWindowSize) : RecordScreenEvent
}
