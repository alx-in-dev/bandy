package li.cactus.bandy.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.cactus.bandy.core.domain.model.AudioFileFormat
import li.cactus.bandy.core.domain.model.Recording

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreenContent(
    state: LibraryScreenState,
    onEvent: (LibraryScreenEvent) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Recording?>(null) }
    var deleteTarget by remember { mutableStateOf<Recording?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "Библиотека") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(LibraryScreenEvent.NewRecordingClicked) },
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Новая запись")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query,
                onQueryChange = { onEvent(LibraryScreenEvent.QueryChanged(it)) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.isEmpty -> EmptyState(
                        hasQuery = state.query.isNotBlank(),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> RecordingList(
                        recordings = state.recordings,
                        playingRecordingId = state.playingRecordingId,
                        onEvent = onEvent,
                        onRenameRequest = { renameTarget = it },
                        onDeleteRequest = { deleteTarget = it },
                    )
                }
            }
        }
    }

    renameTarget?.let { recording ->
        RenameDialog(
            recording = recording,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                onEvent(LibraryScreenEvent.RenameConfirmed(recording.id, newTitle))
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { recording ->
        DeleteDialog(
            recording = recording,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onEvent(LibraryScreenEvent.DeleteConfirmed(recording))
                deleteTarget = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text(text = "Поиск по названию") },
    )
}

@Composable
private fun RecordingList(
    recordings: List<Recording>,
    playingRecordingId: Long?,
    onEvent: (LibraryScreenEvent) -> Unit,
    onRenameRequest: (Recording) -> Unit,
    onDeleteRequest: (Recording) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = recordings, key = { it.id }) { recording ->
            RecordingCard(
                recording = recording,
                isPlaying = recording.id == playingRecordingId,
                onClick = { onEvent(LibraryScreenEvent.RecordingClicked(recording)) },
                onPlayToggle = { onEvent(LibraryScreenEvent.PlayToggled(recording)) },
                onShare = { onEvent(LibraryScreenEvent.ShareClicked(recording)) },
                onRename = { onRenameRequest(recording) },
                onDelete = { onDeleteRequest(recording) },
            )
        }
    }
}

@Composable
private fun EmptyState(
    hasQuery: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (hasQuery) "Ничего не найдено" else "Пока нет записей",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(16.dp),
    )
}

@Composable
internal fun FormatBadge(format: AudioFileFormat) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = format.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun BandsBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = "$count полос",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
