package dev.vaultdrop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vaultdrop.DictationUi
import dev.vaultdrop.VoiceOption
import dev.vaultdrop.VoicePicker

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

/**
 * Above the keyboard while an Open Transcribe app listens: what it has heard so
 * far, as it hears it, with Stop and Cancel. The text only ever grows, so it is
 * shown as it arrives and kept scrolled to its end.
 */
@Composable
fun DictationStrip(ui: DictationUi, onStop: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(c.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordingDot(live = !ui.finishing)
            Spacer(Modifier.width(10.dp))
            Text(
                if (ui.finishing) "${ui.engine} · finishing…" else "${ui.engine} · listening",
                style = T.stripCount,
                color = c.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StripAction("CANCEL", c.onSurfaceVariant, onCancel)
            if (!ui.finishing) StripAction("STOP", c.primary, onStop)
        }
        val scroll = rememberScrollState()
        LaunchedEffect(scroll) {
            snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
        }
        Text(
            ui.heard.ifEmpty { if (ui.finishing) "…" else "Speak now." },
            style = T.stripQuery,
            color = if (ui.heard.isEmpty()) c.outline else c.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 76.dp)
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

/** Pulses while the mic is open, and holds still once it has closed. */
@Composable
private fun RecordingDot(live: Boolean) {
    val c = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "recording")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "recordingAlpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .alpha(if (live) pulse else 1f)
            .clip(CircleShape)
            .background(if (live) c.error else c.outline),
    )
}

@Composable
private fun StripAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = T.noticeAction,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

/**
 * Which app takes speech. Raised by the mic the first time an Open Transcribe
 * app is installed, again when the one picked has gone or changed hands, and by
 * Voice input… in the menu. It slides up over the note like the day sheet.
 */
@Composable
fun VoiceSheet(picker: VoicePicker, onPick: (VoiceOption) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(c.surfaceContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.onSurface.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            Text("VOICE INPUT", style = T.eyebrow, color = c.primary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Pick the app that turns your speech into text.",
                style = T.cardBody,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            picker.note?.let { note ->
                Spacer(Modifier.height(10.dp))
                Text(
                    note,
                    style = T.cardBody,
                    color = c.onSurface.copy(alpha = 0.82f),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            picker.options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onPick(option) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Filled for the app picked last time, a ring for the rest.
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .then(
                                if (option.current) Modifier.background(c.primary)
                                else Modifier.border(1.dp, c.outline, CircleShape),
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = T.sheetLabel, color = c.onSurface, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            option.detail,
                            style = T.sheetSub,
                            color = c.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Change it later under Voice input… in the menu.",
                style = T.sheetSub,
                color = c.outline,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
