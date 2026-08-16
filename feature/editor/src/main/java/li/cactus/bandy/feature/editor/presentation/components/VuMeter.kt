package li.cactus.bandy.feature.editor.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Horizontal signal-level bar driven by [level] in 0..1. */
@Composable
internal fun VuMeter(
    level: Float,
    modifier: Modifier = Modifier,
    trackColor: Color,
) {
    val clamped = level.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (clamped <= 0f) return@Canvas

        val fillColor = when {
            clamped > 0.9f -> Color(0xFFE53935)
            clamped > 0.7f -> Color(0xFFFFB300)
            else -> Color(0xFF43A047)
        }
        drawRoundRect(
            color = fillColor,
            topLeft = Offset.Zero,
            size = Size(size.width * clamped, size.height),
            cornerRadius = radius,
        )
    }
}
