package li.cactus.bandy.feature.editor.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import li.cactus.bandy.core.ui.HeatColor

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
                    color = HeatColor.of(intensity),
                    topLeft = Offset(c * colWidth, top),
                    size = Size(colWidth + 0.5f, rowHeight + 0.5f),
                )
            }
        }
    }
}
