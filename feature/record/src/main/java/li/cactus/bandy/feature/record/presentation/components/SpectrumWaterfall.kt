package li.cactus.bandy.feature.record.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Waterfall spectrogram: each entry in [frames] is one FFT row of normalized (0..1) magnitudes
 * per frequency column. Oldest first, newest last — newest is drawn at the bottom and older rows
 * scroll upward. X axis is frequency (0Hz → Nyquist), Y axis is time.
 */
@Composable
internal fun SpectrumWaterfall(
    frames: List<FloatArray>,
    maxRows: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val columns = frames.firstOrNull()?.size ?: return@Canvas
        if (columns == 0) return@Canvas

        val rowHeight = size.height / maxRows
        val colWidth = size.width / columns
        val newestBottom = size.height

        frames.forEachIndexed { indexFromOldest, row ->
            val rowsFromNewest = frames.lastIndex - indexFromOldest
            val top = newestBottom - (rowsFromNewest + 1) * rowHeight
            if (top + rowHeight < 0f) return@forEachIndexed
            for (c in 0 until columns) {
                val intensity = row[c]
                if (intensity <= 0.01f) continue
                drawRect(
                    color = heatColor(intensity),
                    topLeft = Offset(c * colWidth, top),
                    size = Size(colWidth + 0.5f, rowHeight + 0.5f),
                )
            }
        }
    }
}

/** Maps 0..1 intensity to a dark-blue → cyan → yellow → red heat ramp. */
private fun heatColor(t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    return when {
        x < 0.33f -> lerp(Color(0xFF0D1B3E), Color(0xFF1DE9B6), x / 0.33f)
        x < 0.66f -> lerp(Color(0xFF1DE9B6), Color(0xFFFFEB3B), (x - 0.33f) / 0.33f)
        else -> lerp(Color(0xFFFFEB3B), Color(0xFFFF1744), (x - 0.66f) / 0.34f)
    }
}

private fun lerp(a: Color, b: Color, f: Float): Color {
    val g = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * g,
        green = a.green + (b.green - a.green) * g,
        blue = a.blue + (b.blue - a.blue) * g,
        alpha = 1f,
    )
}
