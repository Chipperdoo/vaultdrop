package dev.vaultdrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The capture row's photo marks, drawn for the same reason as the rest in
 * Glyphs.kt, and in the same 1.5dp line as the search glass.
 */

/** A camera body with the viewfinder's hump on top, and the lens. */
@Composable
fun CameraGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(w * 0.06f, h * 0.38f)
            quadraticTo(w * 0.06f, h * 0.30f, w * 0.14f, h * 0.30f)
            lineTo(w * 0.30f, h * 0.30f)
            lineTo(w * 0.37f, h * 0.17f)
            lineTo(w * 0.63f, h * 0.17f)
            lineTo(w * 0.70f, h * 0.30f)
            lineTo(w * 0.86f, h * 0.30f)
            quadraticTo(w * 0.94f, h * 0.30f, w * 0.94f, h * 0.38f)
            lineTo(w * 0.94f, h * 0.76f)
            quadraticTo(w * 0.94f, h * 0.84f, w * 0.86f, h * 0.84f)
            lineTo(w * 0.14f, h * 0.84f)
            quadraticTo(w * 0.06f, h * 0.84f, w * 0.06f, h * 0.76f)
            close()
        }
        drawPath(body, color, style = stroke)
        drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.56f), style = stroke)
    }
}

/** A framed picture: a sun over two hills. */
@Composable
fun GalleryGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.08f, h * 0.14f),
            size = Size(w * 0.84f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.1f),
            style = stroke,
        )
        drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.34f, h * 0.38f), style = stroke)
        val hills = Path().apply {
            moveTo(w * 0.08f, h * 0.76f)
            lineTo(w * 0.38f, h * 0.50f)
            lineTo(w * 0.58f, h * 0.66f)
            lineTo(w * 0.70f, h * 0.56f)
            lineTo(w * 0.92f, h * 0.74f)
        }
        drawPath(hills, color, style = stroke)
    }
}
