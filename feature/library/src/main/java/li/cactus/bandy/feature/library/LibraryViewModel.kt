package li.cactus.bandy.feature.library

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.Recording
import li.cactus.bandy.core.domain.usecase.DeleteRecordingUseCase
import li.cactus.bandy.core.domain.usecase.ObserveRecordingsUseCase
import li.cactus.bandy.core.domain.usecase.PreviewPlaybackUseCase
import li.cactus.bandy.core.domain.usecase.RenameRecordingUseCase
import li.cactus.bandy.core.domain.usecase.ShareRecordingUseCase
import li.cactus.bandy.core.mvi.MVIBaseViewModel

internal class LibraryViewModel(
    private val observeRecordingsUseCase: ObserveRecordingsUseCase,
    private val renameRecordingUseCase: RenameRecordingUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val shareRecordingUseCase: ShareRecordingUseCase,
    private val previewPlaybackUseCase: PreviewPlaybackUseCase,
) : MVIBaseViewModel<LibraryScreenState, LibraryScreenAction, LibraryScreenEvent>(
    initialState = LibraryScreenState(),
) {

    private val queryFlow = MutableStateFlow("")

    init {
        observeRecordings()
        // The player is shared across screens — if playback stops (finished, or another
        // screen took over), drop our "now playing" row highlight too.
        viewModelScope.launch {
            previewPlaybackUseCase.isPlaying.collect { playing ->
                if (!playing) setState { copy(playingRecordingId = null) }
            }
        }
    }

    override fun obtainEvent(viewEvent: LibraryScreenEvent) {
        when (viewEvent) {
            is LibraryScreenEvent.QueryChanged -> {
                setState { copy(query = viewEvent.query) }
                queryFlow.value = viewEvent.query
            }

            is LibraryScreenEvent.RecordingClicked -> {
                previewPlaybackUseCase.stop()
                sendAction(LibraryScreenAction.NavigateToEditor(viewEvent.recording.id))
            }

            LibraryScreenEvent.NewRecordingClicked -> {
                previewPlaybackUseCase.stop()
                sendAction(LibraryScreenAction.NavigateToRecord)
            }

            is LibraryScreenEvent.RenameConfirmed -> rename(viewEvent.id, viewEvent.newTitle)

            is LibraryScreenEvent.DeleteConfirmed -> delete(viewEvent)

            is LibraryScreenEvent.ShareClicked -> share(viewEvent)

            is LibraryScreenEvent.PlayToggled -> togglePlay(viewEvent.recording)
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeRecordings() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .flatMapLatest { raw -> observeRecordingsUseCase(raw.trim().ifBlank { null }) }
                .catch { setState { copy(isLoading = false) } }
                .collect { recordings ->
                    setState { copy(recordings = recordings, isLoading = false) }
                }
        }
    }

    private fun togglePlay(recording: Recording) {
        if (currentState.playingRecordingId == recording.id) {
            previewPlaybackUseCase.stop()
            setState { copy(playingRecordingId = null) }
        } else {
            previewPlaybackUseCase.play(recording.filePath, null)
            setState { copy(playingRecordingId = recording.id) }
        }
    }

    private fun rename(id: Long, newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch { renameRecordingUseCase(id, title) }
    }

    private fun delete(event: LibraryScreenEvent.DeleteConfirmed) {
        if (currentState.playingRecordingId == event.recording.id) {
            previewPlaybackUseCase.stop()
            setState { copy(playingRecordingId = null) }
        }
        viewModelScope.launch { deleteRecordingUseCase(event.recording) }
    }

    private fun share(event: LibraryScreenEvent.ShareClicked) {
        val uri = shareRecordingUseCase(event.recording)
        val mimeType = when (event.recording.format) {
            AudioFileFormat.AAC -> MIME_AAC
            AudioFileFormat.WAV -> MIME_WAV
        }
        sendAction(LibraryScreenAction.ShareRecording(uri, mimeType))
    }

    override fun onCleared() {
        previewPlaybackUseCase.stop()
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIME_AAC = "audio/mp4"
        const val MIME_WAV = "audio/wav"
    }
}
