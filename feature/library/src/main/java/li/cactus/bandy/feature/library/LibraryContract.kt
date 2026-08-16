package li.cactus.bandy.feature.library

import android.net.Uri
import li.cactus.bandy.core.domain.model.Recording

data class LibraryScreenState(
    val query: String = "",
    val recordings: List<Recording> = emptyList(),
    val isLoading: Boolean = true,
    val playingRecordingId: Long? = null,
) {
    val isEmpty: Boolean get() = !isLoading && recordings.isEmpty()
}

sealed interface LibraryScreenEvent {
    data class QueryChanged(val query: String) : LibraryScreenEvent
    data class RecordingClicked(val recording: Recording) : LibraryScreenEvent
    data object NewRecordingClicked : LibraryScreenEvent
    data class RenameConfirmed(val id: Long, val newTitle: String) : LibraryScreenEvent
    data class DeleteConfirmed(val recording: Recording) : LibraryScreenEvent
    data class ShareClicked(val recording: Recording) : LibraryScreenEvent
    data class PlayToggled(val recording: Recording) : LibraryScreenEvent
}

sealed interface LibraryScreenAction {
    data class NavigateToEditor(val recordingId: Long) : LibraryScreenAction
    data object NavigateToRecord : LibraryScreenAction
    data class ShareRecording(val uri: Uri, val mimeType: String) : LibraryScreenAction
}
