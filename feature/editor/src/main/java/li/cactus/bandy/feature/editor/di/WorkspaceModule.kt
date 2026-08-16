package li.cactus.bandy.feature.editor.di

import li.cactus.bandy.feature.editor.presentation.WorkspaceViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val editorModule = module {
    viewModel { params ->
        WorkspaceViewModel(
            initialRecordingId = params.get(),
            recordingControl = get(),
            observeSession = get(),
            observeLiveSpectrum = get(),
            observeAudioSettings = get(),
            updateAudioSettings = get(),
            getRecordingUseCase = get(),
            getAveragedSpectrumUseCase = get(),
            applyBandPassFilterUseCase = get(),
            previewPlaybackUseCase = get(),
            analyzePeriodicityUseCase = get(),
            applyCombFilterUseCase = get(),
            buildSynchronousAverageUseCase = get(),
        )
    }
}
