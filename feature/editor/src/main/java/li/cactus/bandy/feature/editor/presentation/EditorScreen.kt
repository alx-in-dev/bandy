package li.cactus.bandy.feature.editor.presentation

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
            scale = state.scale,
            onBandChanged = { index, low, high ->
                onEvent(EditorScreenEvent.BandChanged(index, low, high))
            },
        )

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
                BandRow(band = band, onRemove = { onEvent(EditorScreenEvent.RemoveBand(index)) })
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

        // Before / After preview
        Text("Прослушивание", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.previewMode == PreviewMode.OFF,
                onClick = { onEvent(EditorScreenEvent.PreviewModeChanged(PreviewMode.OFF)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) { Text("Стоп") }
            SegmentedButton(
                selected = state.previewMode == PreviewMode.BEFORE,
                onClick = { onEvent(EditorScreenEvent.PreviewModeChanged(PreviewMode.BEFORE)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) { Text("До") }
            SegmentedButton(
                selected = state.previewMode == PreviewMode.AFTER,
                onClick = { onEvent(EditorScreenEvent.PreviewModeChanged(PreviewMode.AFTER)) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) { Text("После") }
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
private fun BandRow(band: FrequencyBand, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${band.lowHz} – ${band.highHz} Гц")
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить полосу")
        }
    }
}
