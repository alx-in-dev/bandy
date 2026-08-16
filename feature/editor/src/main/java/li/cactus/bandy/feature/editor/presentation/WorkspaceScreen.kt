package li.cactus.bandy.feature.editor.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.RecordingPhase
import li.cactus.bandy.core.mvi.CollectActions
import li.cactus.bandy.core.navigation.NavigationState
import li.cactus.bandy.feature.editor.presentation.components.SettingsSheet
import li.cactus.bandy.feature.editor.presentation.components.SpectrumWaterfall
import li.cactus.bandy.feature.editor.presentation.components.VuMeter
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val WATERFALL_MAX_ROWS = 150

private val requiredPermissions: Array<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

/** [recordingId] null starts a fresh recording; non-null opens directly in editing mode for that recording. */
@Composable
fun WorkspaceScreen(recordingId: Long?, navigationState: NavigationState) {
    val viewModel = koinViewModel<WorkspaceViewModel>(parameters = { parametersOf(recordingId ?: -1L) })
    val state by viewModel.getViewState().collectAsStateWithLifecycle()
    val spectrumFrames by viewModel.spectrumFrames.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val recordGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (recordGranted) {
            viewModel.obtainEvent(WorkspaceScreenEvent.OnStartClick)
        } else {
            viewModel.obtainEvent(WorkspaceScreenEvent.OnPermissionDenied)
        }
    }

    val requestStart: () -> Unit = {
        val recordGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (recordGranted) {
            viewModel.obtainEvent(WorkspaceScreenEvent.OnStartClick)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    CollectActions(viewModel) { action ->
        when (action) {
            is WorkspaceScreenAction.NavigateToLibrary -> navigationState.navigateToLibrary()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.obtainEvent(WorkspaceScreenEvent.StopPreview) }
    }

    WorkspaceScreenContent(
        state = state,
        spectrumFrames = spectrumFrames,
        onStartRecording = requestStart,
        onEvent = viewModel::obtainEvent,
        onBack = navigationState::navigateBack,
    )

    if (state.isSettingsSheetOpen) {
        SettingsSheet(
            settings = state.audioSettings,
            onSampleRateSelected = { viewModel.obtainEvent(WorkspaceScreenEvent.OnSampleRateSelected(it)) },
            onFftWindowSelected = { viewModel.obtainEvent(WorkspaceScreenEvent.OnFftWindowSelected(it)) },
            onDismiss = { viewModel.obtainEvent(WorkspaceScreenEvent.OnCloseSettings) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceScreenContent(
    state: WorkspaceScreenState,
    spectrumFrames: List<FloatArray>,
    onStartRecording: () -> Unit,
    onEvent: (WorkspaceScreenEvent) -> Unit,
    onBack: () -> Unit,
) {
    when (state.phase) {
        WorkspacePhase.RECORDING -> RecordingPhaseScaffold(
            state = state,
            spectrumFrames = spectrumFrames,
            onStartRecording = onStartRecording,
            onEvent = onEvent,
            onBack = onBack,
        )
        WorkspacePhase.EDITING -> EditingPhaseScaffold(
            state = state,
            onEvent = onEvent,
            onBack = onBack,
        )
    }
}

// ==================== Recording phase ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingPhaseScaffold(
    state: WorkspaceScreenState,
    spectrumFrames: List<FloatArray>,
    onStartRecording: () -> Unit,
    onEvent: (WorkspaceScreenEvent) -> Unit,
    onBack: () -> Unit,
) {
    val phase = state.session.phase
    val isIdle = phase == RecordingPhase.IDLE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запись") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isIdle) {
                        IconButton(onClick = { onEvent(WorkspaceScreenEvent.OnOpenSettings) }) {
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

            RecordingControls(
                phase = phase,
                onStartRecording = onStartRecording,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun RecordingControls(
    phase: RecordingPhase,
    onStartRecording: () -> Unit,
    onEvent: (WorkspaceScreenEvent) -> Unit,
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
                    onClick = { onEvent(WorkspaceScreenEvent.OnPauseClick) },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = "Пауза")
                }
                RecordingStopButton(onEvent)
            }
            RecordingPhase.PAUSED -> {
                FilledIconButton(
                    onClick = { onEvent(WorkspaceScreenEvent.OnResumeClick) },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Продолжить")
                }
                RecordingStopButton(onEvent)
            }
        }
    }
}

@Composable
private fun RecordingStopButton(onEvent: (WorkspaceScreenEvent) -> Unit) {
    FilledIconButton(
        onClick = { onEvent(WorkspaceScreenEvent.OnStopClick) },
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

// ==================== Editing phase ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditingPhaseScaffold(
    state: WorkspaceScreenState,
    onEvent: (WorkspaceScreenEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.recording?.title ?: "Редактор") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            state.errorLoading || state.recording == null || state.spectrum == null ->
                ErrorState(Modifier.fillMaxSize().padding(padding))
            else -> EditingBody(
                state = state,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Не удалось загрузить запись", textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditingBody(
    state: WorkspaceScreenState,
    onEvent: (WorkspaceScreenEvent) -> Unit,
    modifier: Modifier,
) {
    val spectrum = state.spectrum ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Chart style
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("График:", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = state.chartStyle == ChartStyle.AVERAGED,
                    onClick = { onEvent(WorkspaceScreenEvent.ChartStyleChanged(ChartStyle.AVERAGED)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Спектр") }
                SegmentedButton(
                    selected = state.chartStyle == ChartStyle.SPECTROGRAM,
                    onClick = { onEvent(WorkspaceScreenEvent.ChartStyleChanged(ChartStyle.SPECTROGRAM)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Спектрограмма") }
            }
        }

        // Scale toggle
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Шкала частоты:", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = state.scale == FreqScale.LOG,
                    onClick = { onEvent(WorkspaceScreenEvent.ScaleChanged(FreqScale.LOG)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Лог") }
                SegmentedButton(
                    selected = state.scale == FreqScale.LINEAR,
                    onClick = { onEvent(WorkspaceScreenEvent.ScaleChanged(FreqScale.LINEAR)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Линейн") }
            }
        }

        when (state.chartStyle) {
            ChartStyle.AVERAGED -> {
                SpectrumChart(
                    spectrum = spectrum,
                    bands = state.bands,
                    selectedIndex = state.selectedBandIndex,
                    scale = state.scale,
                    onBandChanged = { index, low, high ->
                        onEvent(WorkspaceScreenEvent.BandChanged(index, low, high))
                    },
                    onBandMoved = { index, low ->
                        onEvent(WorkspaceScreenEvent.BandMoved(index, low))
                    },
                    onBandSelected = { index ->
                        onEvent(WorkspaceScreenEvent.BandSelected(index))
                    },
                )
                if (state.bands.isNotEmpty()) {
                    Text(
                        "Перетащите край полосы, чтобы изменить границу, или её середину — чтобы сдвинуть целиком",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ChartStyle.SPECTROGRAM -> {
                val spectrogram = state.spectrogram
                if (state.isLoadingSpectrogram || spectrogram == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    SpectrogramChart(
                        frames = spectrogram,
                        bands = state.bands,
                        selectedIndex = state.selectedBandIndex,
                        scale = state.scale,
                        onBandChanged = { index, low, high ->
                            onEvent(WorkspaceScreenEvent.BandChanged(index, low, high))
                        },
                        onBandMoved = { index, low ->
                            onEvent(WorkspaceScreenEvent.BandMoved(index, low))
                        },
                        onBandSelected = { index ->
                            onEvent(WorkspaceScreenEvent.BandSelected(index))
                        },
                    )
                    Text(
                        "Время слева направо, частота снизу вверх — перетащите край полосы, чтобы изменить границу, или её середину — чтобы сдвинуть целиком",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val selectedBand = state.selectedBandIndex?.let { state.bands.getOrNull(it) }
        if (selectedBand != null) {
            val width = selectedBand.highHz - selectedBand.lowHz
            val maxLow = (state.nyquistHz - width).coerceAtLeast(0)
            Text(
                "Сдвиг выбранной полосы: ${selectedBand.lowHz}–${selectedBand.highHz} Гц",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = selectedBand.lowHz.toFloat().coerceIn(0f, maxLow.toFloat()),
                onValueChange = {
                    onEvent(WorkspaceScreenEvent.BandMoved(state.selectedBandIndex, it.toInt()))
                },
                valueRange = 0f..maxLow.toFloat().coerceAtLeast(1f),
            )
        }

        // Presets + add band
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onEvent(WorkspaceScreenEvent.ApplyVoicePreset) }) { Text("Голос") }
            FilledTonalButton(onClick = { onEvent(WorkspaceScreenEvent.ApplyBassPreset) }) { Text("Бас") }
            OutlinedButton(onClick = { onEvent(WorkspaceScreenEvent.ResetBands) }) { Text("Сброс") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Полосы (${state.bands.size})", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onEvent(WorkspaceScreenEvent.AddBand) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Добавить")
            }
        }

        if (state.bands.isEmpty()) {
            Text("Нет полос — полный спектр (без фильтрации)", style = MaterialTheme.typography.bodyMedium)
        } else {
            state.bands.forEachIndexed { index, band ->
                BandRow(
                    band = band,
                    selected = index == state.selectedBandIndex,
                    onSelect = { onEvent(WorkspaceScreenEvent.BandSelected(index)) },
                    onRemove = { onEvent(WorkspaceScreenEvent.RemoveBand(index)) },
                )
            }
        }

        HorizontalDivider()

        // Butterworth order
        Text("Порядок Butterworth: ${state.butterworthOrder}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.butterworthOrder.toFloat(),
            onValueChange = { onEvent(WorkspaceScreenEvent.ButterworthOrderChanged(it.toInt())) },
            valueRange = 2f..8f,
            steps = 5,
        )

        HorizontalDivider()

        // Periodicity: isolate a repeating knock/tick from background noise within the selected band
        PeriodicitySection(state = state, onEvent = onEvent)

        HorizontalDivider()

        // Before / After preview
        Text("Прослушивание", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayToggleButton(
                label = "До",
                playing = state.previewMode == PreviewMode.BEFORE,
                onClick = {
                    val target = if (state.previewMode == PreviewMode.BEFORE) PreviewMode.OFF else PreviewMode.BEFORE
                    onEvent(WorkspaceScreenEvent.PreviewModeChanged(target))
                },
                modifier = Modifier.weight(1f),
            )
            PlayToggleButton(
                label = "После",
                playing = state.previewMode == PreviewMode.AFTER,
                onClick = {
                    val target = if (state.previewMode == PreviewMode.AFTER) PreviewMode.OFF else PreviewMode.AFTER
                    onEvent(WorkspaceScreenEvent.PreviewModeChanged(target))
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Зациклить воспроизведение")
            Switch(
                checked = state.loopPreview,
                onCheckedChange = { onEvent(WorkspaceScreenEvent.LoopPreviewChanged(it)) },
            )
        }

        HorizontalDivider()

        // Save + AAC export
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Также экспортировать в AAC")
            Switch(
                checked = state.alsoExportAac,
                onCheckedChange = { onEvent(WorkspaceScreenEvent.AlsoExportAacChanged(it)) },
                enabled = !state.isSaving,
            )
        }

        Button(
            onClick = { onEvent(WorkspaceScreenEvent.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Сохранение…")
            } else {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun PlayToggleButton(
    label: String,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playing) {
        Button(onClick = onClick, modifier = modifier) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

@Composable
private fun PeriodicitySection(
    state: WorkspaceScreenState,
    onEvent: (WorkspaceScreenEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Периодичность (выделить повторяющийся стук)", style = MaterialTheme.typography.labelLarge)

        val band = state.periodicityBand
        if (band == null) {
            Text(
                "Выберите ровно одну полосу выше (клик по полосе), чтобы искать в ней повторяющийся стук",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        OutlinedButton(
            onClick = { onEvent(WorkspaceScreenEvent.AnalyzePeriodicity) },
            enabled = !state.isAnalyzingPeriodicity,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isAnalyzingPeriodicity) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Анализ…")
            } else {
                Text("Анализировать полосу ${band.lowHz}–${band.highHz} Гц")
            }
        }

        val analysis = state.periodicityAnalysis ?: return
        if (!analysis.isPeriodic) {
            Text(
                "Явной периодичности не найдено — попробуйте другую полосу или более длинную запись",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        PeriodicityChart(analysis = analysis)
        Text(
            "Период ≈ %.1f мс (%.0f повторов/мин), уверенность %.0f%%, найдено импульсов: %d".format(
                analysis.periodMs,
                analysis.cyclesPerMinute,
                analysis.confidence * 100f,
                analysis.impulseTimestampsMs.size,
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Гармоник в гребенчатом фильтре: ${state.periodicityHarmonics}", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = state.periodicityHarmonics.toFloat(),
            onValueChange = { onEvent(WorkspaceScreenEvent.PeriodicityHarmonicsChanged(it.toInt())) },
            valueRange = 1f..12f,
            steps = 10,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PlayToggleButton(
                label = if (state.isPlayingSyncAverage) "Стоп" else "Усреднённый цикл",
                playing = state.isPlayingSyncAverage,
                onClick = {
                    if (state.isPlayingSyncAverage) {
                        onEvent(WorkspaceScreenEvent.StopPreview)
                    } else {
                        onEvent(WorkspaceScreenEvent.PreviewSynchronousAverage)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { onEvent(WorkspaceScreenEvent.ApplyCombFilter) },
                enabled = !state.isApplyingPeriodicity,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isApplyingPeriodicity) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Применить гребенчатый фильтр")
                }
            }
        }
    }
}

@Composable
private fun BandRow(
    band: FrequencyBand,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${band.lowHz} – ${band.highHz} Гц")
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить полосу")
        }
    }
}
