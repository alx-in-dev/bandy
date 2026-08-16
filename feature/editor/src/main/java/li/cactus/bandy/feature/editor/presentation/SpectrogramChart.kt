package li.cactus.bandy.feature.editor.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
 * runs bottom-to-top, which is the conventional spectrogram layout. Bands are shown as static
 * horizontal stripes for context (not draggable here — use the averaged-spectrum chart to edit). */
@Composable
internal fun SpectrogramChart(
    frames: List<SpectrumFrame>,
    bands: List<FrequencyBand>,
    selectedIndex: Int?,
    scale: FreqScale,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    bandColor: Color = Color(0x33FF9800),
    selectedBandColor: Color = Color(0x664F9DFF),
    markerColor: Color = Color(0xFFFF9800),
    selectedMarkerColor: Color = Color(0xFF4F9DFF),
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (frames.isEmpty()) return@Canvas
        val sampleRate = frames.first().sampleRate
        val maxHz = (sampleRate / 2f).coerceAtLeast(MIN_LOG_HZ + 1f)

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
                val freq = pixelToFreq(size.height - yTop, scale, maxHz, size.height)
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

        bands.forEachIndexed { index, band ->
            val yTopEdge = size.height - freqToPixel(band.highHz.toFloat(), scale, maxHz, size.height)
            val yBottomEdge = size.height - freqToPixel(band.lowHz.toFloat(), scale, maxHz, size.height)
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
