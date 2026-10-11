package dev.vaultdrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mic in the capture row. Drawn, like the rest of the chrome's marks, so it
 * looks the same on every device: a capsule, the cup under it, and a stem.
 */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val line = 1.5.dp.toPx()
        val capsule = Size(w * 0.36f, h * 0.56f)
        drawRoundRect(
            color,
            topLeft = Offset((w - capsule.width) / 2f, h * 0.04f),
            size = capsule,
            cornerRadius = CornerRadius(capsule.width / 2f),
            style = Stroke(width = line),
        )
        drawArc(
            color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(w * 0.64f, h * 0.56f),
            style = Stroke(width = line, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w / 2f, h * 0.78f), Offset(w / 2f, h * 0.96f), line, StrokeCap.Round)
    }
}
