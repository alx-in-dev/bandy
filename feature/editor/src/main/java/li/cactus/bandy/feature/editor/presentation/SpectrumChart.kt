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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.model.FrequencyBand
import li.cactus.bandy.core.ui.HeatColor

@Composable
internal fun SpectrumChart(
    spectrum: AveragedSpectrum,
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
    val maxHz = (spectrum.sampleRate / 2f).coerceAtLeast(MIN_LOG_HZ + 1f)
    val touchSlop = with(LocalDensity.current) { 24.dp.toPx() }

    val currentBands by rememberUpdatedState(bands)
    val currentScale by rememberUpdatedState(scale)
    val onChanged by rememberUpdatedState(onBandChanged)
    val onMoved by rememberUpdatedState(onBandMoved)
    val onSelected by rememberUpdatedState(onBandSelected)

    var dragTarget by remember { mutableStateOf<DragTarget?>(null) }
    var widthPx by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(spectrum) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val target = hitTestBand(pos.x, currentBands, currentScale, maxHz, widthPx, touchSlop)
                        dragTarget = target
                        onSelected(target?.bandIndex)
                    },
                    onDragEnd = { dragTarget = null },
                    onDragCancel = { dragTarget = null },
                    onDrag = { change, _ ->
                        val target = dragTarget ?: return@detectDragGestures
                        change.consume()
                        val x = change.position.x.coerceIn(0f, widthPx)
                        val freq = pixelToFreq(x, currentScale, maxHz, widthPx)
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
        widthPx = size.width
        drawSpectrum(spectrum, currentScale, maxHz)
        currentBands.forEachIndexed { index, band ->
            val xLow = freqToPixel(band.lowHz.toFloat(), currentScale, maxHz, size.width)
            val xHigh = freqToPixel(band.highHz.toFloat(), currentScale, maxHz, size.width)
            val isSelected = index == selectedIndex
            drawRect(
                color = if (isSelected) selectedBandColor else bandColor,
                topLeft = Offset(xLow, 0f),
                size = androidx.compose.ui.geometry.Size(xHigh - xLow, size.height),
            )
            val edgeColor = if (isSelected) selectedMarkerColor else markerColor
            val edgeWidth = if (isSelected) 5f else 3f
            drawLine(edgeColor, Offset(xLow, 0f), Offset(xLow, size.height), strokeWidth = edgeWidth)
            drawLine(edgeColor, Offset(xHigh, 0f), Offset(xHigh, size.height), strokeWidth = edgeWidth)
        }
    }
}

/** Heat-colored bars per pixel column — same intensity ramp as the live waterfall, so the
 * averaged spectrum reads as "one row" of the recording screen's spectrogram. */
private fun DrawScope.drawSpectrum(
    spectrum: AveragedSpectrum,
    scale: FreqScale,
    maxHz: Float,
) {
    val mags = spectrum.magnitudes
    if (mags.isEmpty()) return
    val maxMag = mags.maxOrNull()?.takeIf { it > 0f } ?: return
    val binHz = spectrum.binHz
    val widthPx = size.width.toInt().coerceAtLeast(1)

    for (x in 0 until widthPx) {
        val freq = pixelToFreq(x + 0.5f, scale, maxHz, size.width)
        if (scale == FreqScale.LOG && freq < MIN_LOG_HZ) continue
        val bin = (freq / binHz).roundToInt().coerceIn(0, mags.lastIndex)
        val normalized = (mags[bin] / maxMag).coerceIn(0f, 1f)
        val barHeight = normalized * size.height
        drawRect(
            color = HeatColor.of(normalized),
            topLeft = Offset(x.toFloat(), size.height - barHeight),
            size = Size(1.5f, barHeight),
        )
    }
}
