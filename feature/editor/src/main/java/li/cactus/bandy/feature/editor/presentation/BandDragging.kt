package li.cactus.bandy.feature.editor.presentation

import kotlin.math.abs
import li.cactus.bandy.core.domain.model.FrequencyBand

internal enum class HandleKind { EDGE_LOW, EDGE_HIGH, MOVE }

/** Which part of a band is being dragged, plus the state captured at drag-start for MOVE. */
internal data class DragTarget(
    val bandIndex: Int,
    val kind: HandleKind,
    val originLowHz: Int = 0,
    val originFreqHz: Float = 0f,
)

/**
 * Hit-tests a touch position against band edges/interiors along a single frequency axis —
 * shared by [SpectrumChart] (axis = X, horizontal) and [SpectrogramChart] (axis = Y, vertical,
 * caller passes the position/length already converted to "distance from the 0Hz end").
 * Edge handles win within [slop] of a low/high marker; otherwise a tap/drag inside a band's
 * span selects and moves that whole band.
 */
internal fun hitTestBand(
    axisPos: Float,
    bands: List<FrequencyBand>,
    scale: FreqScale,
    maxHz: Float,
    axisLength: Float,
    slop: Float,
): DragTarget? {
    if (axisLength <= 0f) return null

    var bestEdge: DragTarget? = null
    var bestDist = slop
    bands.forEachIndexed { index, band ->
        val posLow = freqToPixel(band.lowHz.toFloat(), scale, maxHz, axisLength)
        val posHigh = freqToPixel(band.highHz.toFloat(), scale, maxHz, axisLength)
        val dLow = abs(axisPos - posLow)
        val dHigh = abs(axisPos - posHigh)
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

    val freq = pixelToFreq(axisPos, scale, maxHz, axisLength)
    val insideIndex = bands.indexOfFirst { freq >= it.lowHz && freq <= it.highHz }
    if (insideIndex < 0) return null
    val band = bands[insideIndex]
    return DragTarget(insideIndex, HandleKind.MOVE, originLowHz = band.lowHz, originFreqHz = freq)
}
