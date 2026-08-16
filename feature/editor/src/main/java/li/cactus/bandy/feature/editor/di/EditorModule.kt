package li.cactus.bandy.feature.editor.di

import li.cactus.bandy.feature.editor.presentation.EditorViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val editorModule = module {
    viewModel { params ->
        EditorViewModel(
            recordingId = params.get(),
            getRecordingUseCase = get(),
            getAveragedSpectrumUseCase = get(),
            applyBandPassFilterUseCase = get(),
            previewPlaybackUseCase = get(),
        )
    }
}
