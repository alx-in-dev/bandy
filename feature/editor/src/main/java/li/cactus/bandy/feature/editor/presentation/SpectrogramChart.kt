package li.cactus.bandy.feature.editor.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import kotlin.math.roundToInt
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.domain.model.SpectrumFrame
import li.cactus.bandy.core.ui.HeatColor

private const val FREQUENCY_ROWS = 128

/** Time (X) vs frequency (Y), heat-colored amplitude — the same visual language as the live
 * waterfall on the recording phase, but transposed: here time runs left-to-right and frequency
 * runs bottom-to-top, which is the conventional spectrogram layout. Bands can be edited here too
 * — same drag-edge/drag-middle gestures as the averaged-spectrum chart, just along the vertical
 * (frequency) axis instead of horizontal. */
@Composable
internal fun SpectrogramChart(
    frames: List<SpectrumFrame>,
    bands: List<FrequencyBand>,
    selectedIndex: Int?,
    scale: FreqScale,
    onBandChanged: (index: Int, lowHz: Int, highHz: Int) -> Unit,
    onBandMoved: (index: Int, lowHz: Int) -> Unit,
    onBandSelected: (index: Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    bandColor: Color = Color(0x33FF9800),
    selectedBandColor: Color = Color(0x664F9DFF),
    markerColor: Color = Color(0xFFFF9800),
    selectedMarkerColor: Color = Color(0xFF4F9DFF),
) {
    val maxHz = ((frames.firstOrNull()?.sampleRate ?: 0) / 2f).coerceAtLeast(MIN_LOG_HZ + 1f)
    val touchSlop = with(LocalDensity.current) { 24.dp.toPx() }

    val currentBands by rememberUpdatedState(bands)
    val currentScale by rememberUpdatedState(scale)
    val onChanged by rememberUpdatedState(onBandChanged)
    val onMoved by rememberUpdatedState(onBandMoved)
    val onSelected by rememberUpdatedState(onBandSelected)

    var dragTarget by remember { mutableStateOf<DragTarget?>(null) }
    var heightPx by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(frames) {
                detectDragGestures(
                    onDragStart = { pos ->
                        // Y grows downward on screen but the frequency axis grows upward (0Hz at the
                        // bottom), so the "distance from the 0Hz end" used by freqToPixel/pixelToFreq
                        // is the distance from the bottom edge, not from the top.
                        val axisPos = heightPx - pos.y
                        val target = hitTestBand(axisPos, currentBands, currentScale, maxHz, heightPx, touchSlop)
                        dragTarget = target
                        onSelected(target?.bandIndex)
                    },
                    onDragEnd = { dragTarget = null },
                    onDragCancel = { dragTarget = null },
                    onDrag = { change, _ ->
                        val target = dragTarget ?: return@detectDragGestures
                        change.consume()
                        val y = change.position.y.coerceIn(0f, heightPx)
                        val axisPos = heightPx - y
                        val freq = pixelToFreq(axisPos, currentScale, maxHz, heightPx)
                        val band = currentBands.getOrNull(target.bandIndex) ?: return@detectDragGestures
                        when (target.kind) {
                            HandleKind.EDGE_LOW -> onChanged(target.bandIndex, freq.roundToInt(), band.highHz)
                            HandleKind.EDGE_HIGH -> onChanged(target.bandIndex, band.lowHz, freq.roundToInt())
                            HandleKind.MOVE -> {
                                val newLow = target.originLowHz + (freq - target.originFreqHz)
                                onMoved(target.bandIndex, newLow.roundToInt())
                            }
                        }
                    },
                )
            },
    ) {
        heightPx = size.height
        if (frames.isEmpty()) return@Canvas

        var globalMax = 0f
        for (frame in frames) {
            for (m in frame.magnitudes) if (m > globalMax) globalMax = m
        }
        if (globalMax <= 0f) return@Canvas
        val logDenom = ln(1f + globalMax)

        val colWidth = size.width / frames.size
        val rowHeight = size.height / FREQUENCY_ROWS

        frames.forEachIndexed { frameIndex, frame ->
            val binHz = frame.sampleRate / 2f / frame.magnitudes.size
            val x = frameIndex * colWidth
            for (row in 0 until FREQUENCY_ROWS) {
                val yTop = row * rowHeight
                val freq = pixelToFreq(size.height - yTop, currentScale, maxHz, size.height)
                val bin = (freq / binHz).roundToInt().coerceIn(0, frame.magnitudes.lastIndex)
                val magnitude = frame.magnitudes[bin]
                val normalized = (ln(1f + magnitude) / logDenom).coerceIn(0f, 1f)
                if (normalized <= 0.02f) continue
                drawRect(
                    color = HeatColor.of(normalized),
                    topLeft = Offset(x, yTop),
                    size = Size(colWidth + 0.5f, rowHeight + 0.5f),
                )
            }
        }

        currentBands.forEachIndexed { index, band ->
            val yTopEdge = size.height - freqToPixel(band.highHz.toFloat(), currentScale, maxHz, size.height)
            val yBottomEdge = size.height - freqToPixel(band.lowHz.toFloat(), currentScale, maxHz, size.height)
            val isSelected = index == selectedIndex
            drawRect(
                color = if (isSelected) selectedBandColor else bandColor,
                topLeft = Offset(0f, yTopEdge),
                size = Size(size.width, yBottomEdge - yTopEdge),
            )
            val edgeColor = if (isSelected) selectedMarkerColor else markerColor
            val edgeWidth = if (isSelected) 5f else 3f
            drawLine(edgeColor, Offset(0f, yTopEdge), Offset(size.width, yTopEdge), strokeWidth = edgeWidth)
            drawLine(edgeColor, Offset(0f, yBottomEdge), Offset(size.width, yBottomEdge), strokeWidth = edgeWidth)
        }
    }
}
