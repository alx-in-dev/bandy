package li.cactus.bandy.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.cactus.bandy.core.domain.model.Recording

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingCard(
    recording: Recording,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayToggle: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                text = recording.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 16.dp),
            )
            Text(
                text = formatCreatedAt(recording.createdAtMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(recording.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                )
                FormatBadge(format = recording.format)
                if (recording.appliedBands.isNotEmpty()) {
                    BandsBadge(count = recording.appliedBands.size)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onPlayToggle) {
                    if (isPlaying) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = "Остановить")
                    } else {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Прослушать")
                    }
                }
                IconButton(onClick = onShare) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Поделиться")
                }
                IconButton(onClick = onRename) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Переименовать")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}

@Composable
internal fun RenameDialog(
    recording: Recording,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(recording.id) { mutableStateOf(recording.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Переименовать") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(text = "Название") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) {
                Text(text = "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Отмена") }
        },
    )
}

@Composable
internal fun DeleteDialog(
    recording: Recording,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Удалить запись?") },
        text = { Text(text = "«${recording.title}» будет удалена без возможности восстановления.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = "Удалить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Отмена") }
        },
    )
}
