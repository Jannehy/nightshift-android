package com.jannehy.nightshift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/**
 * The app's mark – crescent moon with a pair of beamed eighth notes.
 *
 * Same geometry as the launcher icon; `tools/make-icon.py --swift` prints these
 * numbers, so artwork and icon cannot drift apart.
 */
object MarkGeometry {
    val moonCentre = Offset(0.4314f, 0.4963f)
    const val moonRadius = 0.3048f
    val biteCentre = Offset(0.5711f, 0.3587f)
    const val biteRadius = 0.2921f
    val head1 = Offset(0.6108f, 0.4612f)
    val head2 = Offset(0.7919f, 0.4145f)
    const val headRX = 0.0662f
    const val headRY = 0.0526f
    const val headTiltDegrees = -18.33f      // -0.32 rad
    val stem1 = Offset(0.6638f, 0.4612f) to Offset(0.6663f, 0.2587f)
    val stem2 = Offset(0.8449f, 0.4145f) to Offset(0.8474f, 0.2120f)
    val beam = Offset(0.6663f, 0.2719f) to Offset(0.8474f, 0.2251f)
    const val stemWidth = 0.0263f
    const val beamWidth = 0.0526f
}

@Composable
fun NightshiftMark(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val originX = (size.width - side) / 2
        val originY = (size.height - side) / 2
        fun place(point: Offset) =
            Offset(originX + point.x * side, originY + point.y * side)

        // Crescent: the moon disc with the bite taken out of it.
        val moon = Path().apply {
            val c = place(MarkGeometry.moonCentre)
            val r = MarkGeometry.moonRadius * side
            addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
        }
        val bite = Path().apply {
            val c = place(MarkGeometry.biteCentre)
            val r = MarkGeometry.biteRadius * side
            addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
        }
        drawPath(Path().apply { op(moon, bite, PathOperation.Difference) }, color)

        // Note heads: ellipses, tilted.
        listOf(MarkGeometry.head1, MarkGeometry.head2).forEach { head ->
            val c = place(head)
            val rx = MarkGeometry.headRX * side
            val ry = MarkGeometry.headRY * side
            rotate(MarkGeometry.headTiltDegrees, pivot = c) {
                drawOval(color, topLeft = Offset(c.x - rx, c.y - ry),
                    size = Size(rx * 2, ry * 2))
            }
        }

        // Stems and beam: round-capped lines, whose outline is the same capsule
        // the icon rasteriser draws.
        listOf(MarkGeometry.stem1, MarkGeometry.stem2).forEach { (from, to) ->
            drawLine(color, place(from), place(to),
                strokeWidth = MarkGeometry.stemWidth * side, cap = StrokeCap.Round)
        }
        drawLine(color, place(MarkGeometry.beam.first), place(MarkGeometry.beam.second),
            strokeWidth = MarkGeometry.beamWidth * side, cap = StrokeCap.Round)
    }
}
