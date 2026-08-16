package li.cactus.bandy.feature.record.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.cactus.bandy.core.domain.model.RecordingPhase
import li.cactus.bandy.core.mvi.CollectActions
import li.cactus.bandy.core.navigation.NavigationState
import li.cactus.bandy.feature.record.presentation.components.SettingsSheet
import li.cactus.bandy.feature.record.presentation.components.SpectrumWaterfall
import li.cactus.bandy.feature.record.presentation.components.VuMeter
import org.koin.androidx.compose.koinViewModel

private const val WATERFALL_MAX_ROWS = 150

private val requiredPermissions: Array<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

@Composable
fun RecordScreen(navigationState: NavigationState) {
    val viewModel = koinViewModel<RecordViewModel>()
    val state by viewModel.getViewState().collectAsStateWithLifecycle()
    val spectrumFrames by viewModel.spectrumFrames.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val recordGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (recordGranted) {
            viewModel.obtainEvent(RecordScreenEvent.OnStartClick)
        } else {
            viewModel.obtainEvent(RecordScreenEvent.OnPermissionDenied)
        }
    }

    val requestStart: () -> Unit = {
        val recordGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (recordGranted) {
            viewModel.obtainEvent(RecordScreenEvent.OnStartClick)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    CollectActions(viewModel) { action ->
        when (action) {
            is RecordScreenAction.NavigateToEditor ->
                navigationState.navigateToEditor(action.recordingId)
        }
    }

    RecordScreenContent(
        state = state,
        spectrumFrames = spectrumFrames,
        onStartRecording = requestStart,
        onEvent = viewModel::obtainEvent,
    )

    if (state.isSettingsSheetOpen) {
        SettingsSheet(
            settings = state.settings,
            onSampleRateSelected = { viewModel.obtainEvent(RecordScreenEvent.OnSampleRateSelected(it)) },
            onFftWindowSelected = { viewModel.obtainEvent(RecordScreenEvent.OnFftWindowSelected(it)) },
            onDismiss = { viewModel.obtainEvent(RecordScreenEvent.OnCloseSettings) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordScreenContent(
    state: RecordScreenState,
    spectrumFrames: List<FloatArray>,
    onStartRecording: () -> Unit,
    onEvent: (RecordScreenEvent) -> Unit,
) {
    val phase = state.session.phase
    val isIdle = phase == RecordingPhase.IDLE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запись") },
                actions = {
                    if (isIdle) {
                        IconButton(onClick = { onEvent(RecordScreenEvent.OnOpenSettings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.session.lowStorageWarning) {
                LowStorageBanner()
            }

            Text(
                text = formatElapsed(state.session.elapsedMs),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            VuMeter(
                level = state.session.vuLevel,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            SpectrumWaterfall(
                frames = spectrumFrames,
                maxRows = WATERFALL_MAX_ROWS,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = Color(0xFF0D1B3E),
                        shape = RoundedCornerShape(12.dp),
                    ),
            )

            if (state.permissionDenied) {
                PermissionDenied(onRequest = onStartRecording)
            }

            Controls(
                phase = phase,
                onStartRecording = onStartRecording,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun Controls(
    phase: RecordingPhase,
    onStartRecording: () -> Unit,
    onEvent: (RecordScreenEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (phase) {
            RecordingPhase.IDLE -> {
                FilledIconButton(
                    onClick = onStartRecording,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        Icons.Filled.FiberManualRecord,
                        contentDescription = "Записать",
                        tint = Color(0xFFE53935),
                    )
                }
            }
            RecordingPhase.RECORDING -> {
                FilledIconButton(
                    onClick = { onEvent(RecordScreenEvent.OnPauseClick) },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = "Пауза")
                }
                StopButton(onEvent)
            }
            RecordingPhase.PAUSED -> {
                FilledIconButton(
                    onClick = { onEvent(RecordScreenEvent.OnResumeClick) },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Продолжить")
                }
                StopButton(onEvent)
            }
        }
    }
}

@Composable
private fun StopButton(onEvent: (RecordScreenEvent) -> Unit) {
    FilledIconButton(
        onClick = { onEvent(RecordScreenEvent.OnStopClick) },
        modifier = Modifier.size(64.dp),
    ) {
        Icon(Icons.Filled.Stop, contentDescription = "Остановить")
    }
}

@Composable
private fun LowStorageBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = "Мало места на устройстве",
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun PermissionDenied(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Для записи нужен доступ к микрофону",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = onRequest,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Разрешить")
        }
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
