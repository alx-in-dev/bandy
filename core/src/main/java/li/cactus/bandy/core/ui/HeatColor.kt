package li.cactus.bandy.core.ui

import androidx.compose.ui.graphics.Color

/** Dark-blue -> cyan -> yellow -> red heat ramp, shared by the live waterfall (record) and the averaged spectrum chart (editor) so both read as the same visual language. */
object HeatColor {

    fun of(intensity: Float): Color {
        val x = intensity.coerceIn(0f, 1f)
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
}
