package dev.vaultdrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The chrome's small marks are drawn rather than typeset. The design sets them
 * in IBM Plex Mono, and glyphs like U+2315 are absent from the platform faces —
 * drawing them keeps the shape identical on every device.
 */

@Composable
fun TriangleDown(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val path = Path().apply {
            moveTo(0f, this@Canvas.size.height * 0.25f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height * 0.25f)
            lineTo(this@Canvas.size.width / 2f, this@Canvas.size.height * 0.8f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun TriangleRight(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.25f, 0f)
            lineTo(this@Canvas.size.width * 0.25f, this@Canvas.size.height)
            lineTo(this@Canvas.size.width * 0.85f, this@Canvas.size.height / 2f)
            close()
        }
        drawPath(path, color)
    }
}

/** Single guillemet, as used for the day steppers and the back affordance. */
@Composable
fun Chevron(color: Color, pointsLeft: Boolean, size: Dp = 11.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val path = Path().apply {
            if (pointsLeft) {
                moveTo(w * 0.68f, 0f); lineTo(w * 0.28f, h / 2f); lineTo(w * 0.68f, h)
            } else {
                moveTo(w * 0.32f, 0f); lineTo(w * 0.72f, h / 2f); lineTo(w * 0.32f, h)
            }
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
fun SearchGlyph(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.32f
        val c = Offset(this.size.width * 0.42f, this.size.height * 0.42f)
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = r, center = c, style = stroke)
        drawLine(
            color,
            start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            end = Offset(this.size.width * 0.88f, this.size.height * 0.88f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** A ring with an "i" in it: something worth knowing that is not a problem. */
@Composable
fun InfoGlyph(color: Color, size: Dp = 15.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = 1.3.dp.toPx()
        drawCircle(color, radius = w / 2f - stroke / 2f, style = Stroke(width = stroke))
        drawLine(color, Offset(w / 2f, w * 0.45f), Offset(w / 2f, w * 0.72f), stroke, StrokeCap.Round)
        drawCircle(color, radius = stroke * 0.8f, center = Offset(w / 2f, w * 0.29f))
    }
}

@Composable
fun CloseGlyph(color: Color, size: Dp = 13.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(this.size.width, this.size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(this.size.width, 0f), Offset(0f, this.size.height), stroke, StrokeCap.Round)
    }
}
