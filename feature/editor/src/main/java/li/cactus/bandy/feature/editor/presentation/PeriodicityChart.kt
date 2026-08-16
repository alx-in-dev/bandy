package li.cactus.bandy.feature.editor.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import li.cactus.bandy.core.domain.model.PeriodicityAnalysis

private const val MAX_ENVELOPE_POINTS = 1200

/** Envelope over time with a vertical tick at each detected repeating impulse. */
@Composable
internal fun PeriodicityChart(
    analysis: PeriodicityAnalysis,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = Color(0xFF4CAF50),
    fillColor: Color = Color(0x334CAF50),
    markerColor: Color = Color(0xFFFF5252),
) {
    val envelope = analysis.envelope
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (envelope.isEmpty()) return@Canvas
        val totalMs = (envelope.size * analysis.envelopeIntervalMs).coerceAtLeast(1f)
        val maxVal = envelope.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val stride = (envelope.size / MAX_ENVELOPE_POINTS).coerceAtLeast(1)
        val lastIndex = (envelope.size - 1).coerceAtLeast(1)

        val line = Path()
        val fill = Path()
        var started = false
        var i = 0
        while (i < envelope.size) {
            val x = (i / lastIndex.toFloat()) * size.width
            val y = size.height - (envelope[i] / maxVal) * size.height
            if (!started) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
                started = true
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
            i += stride
        }
        if (started) {
            fill.lineTo(size.width, size.height)
            fill.close()
            drawPath(fill, fillColor)
            drawPath(line, lineColor, style = Stroke(width = 2f))
        }

        analysis.impulseTimestampsMs.forEach { tMs ->
            val x = (tMs / totalMs) * size.width
            drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
        }
    }
}
