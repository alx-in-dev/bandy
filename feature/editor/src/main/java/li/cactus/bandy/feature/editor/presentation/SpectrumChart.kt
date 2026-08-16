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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import li.cactus.bandy.core.domain.model.AveragedSpectrum
import li.cactus.bandy.core.domain.model.FrequencyBand

private const val MIN_LOG_HZ = 20f

private enum class HandleKind { EDGE_LOW, EDGE_HIGH, MOVE }

/** Which part of a band is being dragged, plus the state captured at drag-start for MOVE. */
private data class DragTarget(
    val bandIndex: Int,
    val kind: HandleKind,
    val originLowHz: Int = 0,
    val originFreqHz: Float = 0f,
)

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
    lineColor: Color = Color(0xFF4F9DFF),
    fillColor: Color = Color(0x334F9DFF),
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
                        val target = hitTest(pos.x, currentBands, currentScale, maxHz, widthPx, touchSlop)
                        dragTarget = target
                        onSelected(target?.bandIndex)
                    },
                    onDragEnd = { dragTarget = null },
                    onDragCancel = { dragTarget = null },
                    onDrag = { change, _ ->
                        val target = dragTarget ?: return@detectDragGestures
                        change.consume()
                        val x = change.position.x.coerceIn(0f, widthPx)
                        val freq = xToFreq(x, currentScale, maxHz, widthPx)
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
        drawSpectrum(spectrum, currentScale, maxHz, lineColor, fillColor)
        currentBands.forEachIndexed { index, band ->
            val xLow = freqToX(band.lowHz.toFloat(), currentScale, maxHz, size.width)
            val xHigh = freqToX(band.highHz.toFloat(), currentScale, maxHz, size.width)
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

private fun DrawScope.drawSpectrum(
    spectrum: AveragedSpectrum,
    scale: FreqScale,
    maxHz: Float,
    lineColor: Color,
    fillColor: Color,
) {
    val mags = spectrum.magnitudes
    if (mags.isEmpty()) return
    val maxMag = mags.maxOrNull()?.takeIf { it > 0f } ?: return
    val binHz = spectrum.binHz

    val line = Path()
    val fill = Path()
    var started = false
    for (i in mags.indices) {
        val freq = i * binHz
        if (scale == FreqScale.LOG && freq < MIN_LOG_HZ) continue
        val x = freqToX(freq, scale, maxHz, size.width)
        val y = size.height - (mags[i] / maxMag) * size.height
        if (!started) {
            line.moveTo(x, y)
            fill.moveTo(x, size.height)
            fill.lineTo(x, y)
            started = true
        } else {
            line.lineTo(x, y)
            fill.lineTo(x, y)
        }
    }
    if (started) {
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, fillColor)
        drawPath(line, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}

/** Edge handles win within [slop] of a low/high marker; otherwise a tap/drag inside a band's
 * rectangle selects and moves that whole band. */
private fun hitTest(
    x: Float,
    bands: List<FrequencyBand>,
    scale: FreqScale,
    maxHz: Float,
    width: Float,
    slop: Float,
): DragTarget? {
    if (width <= 0f) return null

    var bestEdge: DragTarget? = null
    var bestDist = slop
    bands.forEachIndexed { index, band ->
        val xLow = freqToX(band.lowHz.toFloat(), scale, maxHz, width)
        val xHigh = freqToX(band.highHz.toFloat(), scale, maxHz, width)
        val dLow = abs(x - xLow)
        val dHigh = abs(x - xHigh)
        if (dLow <= bestDist) {
            bestDist = dLow
            bestEdge = DragTarget(index, HandleKind.EDGE_LOW)
        }
        if (dHigh <= bestDist) {
            bestDist = dHigh
            bestEdge = DragTarget(index, HandleKind.EDGE_HIGH)
        }
    }
    if (bestEdge != null) return bestEdge

    val freq = xToFreq(x, scale, maxHz, width)
    val insideIndex = bands.indexOfFirst { freq >= it.lowHz && freq <= it.highHz }
    if (insideIndex < 0) return null
    val band = bands[insideIndex]
    return DragTarget(insideIndex, HandleKind.MOVE, originLowHz = band.lowHz, originFreqHz = freq)
}

private fun freqToX(freq: Float, scale: FreqScale, maxHz: Float, width: Float): Float = when (scale) {
    FreqScale.LINEAR -> (freq / maxHz) * width
    FreqScale.LOG -> {
        val f = freq.coerceAtLeast(MIN_LOG_HZ)
        ((log10(f) - log10(MIN_LOG_HZ)) / (log10(maxHz) - log10(MIN_LOG_HZ))) * width
    }
}

private fun xToFreq(x: Float, scale: FreqScale, maxHz: Float, width: Float): Float {
    if (width <= 0f) return 0f
    val t = (x / width).coerceIn(0f, 1f)
    return when (scale) {
        FreqScale.LINEAR -> t * maxHz
        FreqScale.LOG -> {
            val logMin = log10(MIN_LOG_HZ)
            val logMax = log10(maxHz)
            10f.pow(t * (logMax - logMin) + logMin)
        }
    }
}
