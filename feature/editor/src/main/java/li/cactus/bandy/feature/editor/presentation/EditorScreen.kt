package li.cactus.bandy.feature.editor.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.DisposableEffect
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.mvi.CollectActions
import li.cactus.bandy.core.navigation.NavigationState

@Composable
fun EditorScreen(recordingId: Long, navigationState: NavigationState) {
    val viewModel = koinViewModel<EditorViewModel>(parameters = { parametersOf(recordingId) })
    val state by viewModel.getViewState().collectAsStateWithLifecycle()

    CollectActions(viewModel) { action ->
        when (action) {
            is EditorScreenAction.NavigateToLibrary -> navigationState.navigateToLibrary()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.obtainEvent(EditorScreenEvent.StopPreview) }
    }

    EditorScreenContent(
        state = state,
        onEvent = viewModel::obtainEvent,
        onBack = navigationState::navigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreenContent(
    state: EditorScreenState,
    onEvent: (EditorScreenEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.recording?.title ?: "Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            state.errorLoading || state.recording == null || state.spectrum == null ->
                ErrorState(Modifier.fillMaxSize().padding(padding))
            else -> EditorBody(
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
private fun EditorBody(
    state: EditorScreenState,
    onEvent: (EditorScreenEvent) -> Unit,
    modifier: Modifier,
) {
    val spectrum = state.spectrum ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Scale toggle
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Шкала X:", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = state.scale == FreqScale.LOG,
                    onClick = { onEvent(EditorScreenEvent.ScaleChanged(FreqScale.LOG)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Лог") }
                SegmentedButton(
                    selected = state.scale == FreqScale.LINEAR,
                    onClick = { onEvent(EditorScreenEvent.ScaleChanged(FreqScale.LINEAR)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Линейн") }
            }
        }

        SpectrumChart(
            spectrum = spectrum,
            bands = state.bands,
            selectedIndex = state.selectedBandIndex,
            scale = state.scale,
            onBandChanged = { index, low, high ->
                onEvent(EditorScreenEvent.BandChanged(index, low, high))
            },
            onBandMoved = { index, low ->
                onEvent(EditorScreenEvent.BandMoved(index, low))
            },
            onBandSelected = { index ->
                onEvent(EditorScreenEvent.BandSelected(index))
            },
        )
        if (state.bands.isNotEmpty()) {
            Text(
                "Перетащите край полосы, чтобы изменить границу, или её середину — чтобы сдвинуть целиком",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Presets + add band
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onEvent(EditorScreenEvent.ApplyVoicePreset) }) { Text("Голос") }
            FilledTonalButton(onClick = { onEvent(EditorScreenEvent.ApplyBassPreset) }) { Text("Бас") }
            OutlinedButton(onClick = { onEvent(EditorScreenEvent.ResetBands) }) { Text("Сброс") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Полосы (${state.bands.size})", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onEvent(EditorScreenEvent.AddBand) }) {
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
                    onSelect = { onEvent(EditorScreenEvent.BandSelected(index)) },
                    onRemove = { onEvent(EditorScreenEvent.RemoveBand(index)) },
                )
            }
        }

        HorizontalDivider()

        // Butterworth order
        Text("Порядок Butterworth: ${state.butterworthOrder}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.butterworthOrder.toFloat(),
            onValueChange = { onEvent(EditorScreenEvent.ButterworthOrderChanged(it.toInt())) },
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
                    onEvent(EditorScreenEvent.PreviewModeChanged(target))
                },
                modifier = Modifier.weight(1f),
            )
            PlayToggleButton(
                label = "После",
                playing = state.previewMode == PreviewMode.AFTER,
                onClick = {
                    val target = if (state.previewMode == PreviewMode.AFTER) PreviewMode.OFF else PreviewMode.AFTER
                    onEvent(EditorScreenEvent.PreviewModeChanged(target))
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
                onCheckedChange = { onEvent(EditorScreenEvent.LoopPreviewChanged(it)) },
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
                onCheckedChange = { onEvent(EditorScreenEvent.AlsoExportAacChanged(it)) },
                enabled = !state.isSaving,
            )
        }

        Button(
            onClick = { onEvent(EditorScreenEvent.Save) },
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
    state: EditorScreenState,
    onEvent: (EditorScreenEvent) -> Unit,
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
            onClick = { onEvent(EditorScreenEvent.AnalyzePeriodicity) },
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
            onValueChange = { onEvent(EditorScreenEvent.PeriodicityHarmonicsChanged(it.toInt())) },
            valueRange = 1f..12f,
            steps = 10,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PlayToggleButton(
                label = if (state.isPlayingSyncAverage) "Стоп" else "Усреднённый цикл",
                playing = state.isPlayingSyncAverage,
                onClick = {
                    if (state.isPlayingSyncAverage) {
                        onEvent(EditorScreenEvent.StopPreview)
                    } else {
                        onEvent(EditorScreenEvent.PreviewSynchronousAverage)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { onEvent(EditorScreenEvent.ApplyCombFilter) },
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
