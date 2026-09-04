package dev.vaultdrop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vaultdrop.VerifyLine

/**
 * Board 1a, READING CONFIG. The parse itself takes a few milliseconds; the
 * cadence is here so the user can read what was found in their vault before the
 * editor takes the screen.
 */
@Composable
fun VerifyScreen(
    lines: List<VerifyLine>,
    step: Int,
    shaLine: String,
    ready: Boolean,
    onContinue: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 60.dp, bottom = 28.dp),
    ) {
        Text("READING CONFIG", style = T.eyebrowTight, color = c.primary)
        Spacer(Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            lines.forEachIndexed { i, line ->
                val done = step > i
                val alpha by animateFloatAsState(
                    targetValue = if (done) 1f else 0.28f,
                    animationSpec = tween(durationMillis = 180),
                    label = "verifyLine",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().alpha(alpha),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (done) c.primary else c.outline.copy(alpha = 0.7f)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        line.label,
                        style = T.verifyLabel,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (done) line.value else "",
                        style = T.verifyValue,
                        color = c.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        val buttonAlpha by animateFloatAsState(
            targetValue = if (ready) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "verifyContinue",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.primary)
                .alpha(buttonAlpha)
                .clickable(enabled = ready, onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue", style = T.button, color = c.onPrimary)
        }
        Spacer(Modifier.height(18.dp))

        Text(
            "cached to SharedPreferences\n$shaLine",
            style = T.verifyFoot,
            color = c.outline.copy(alpha = 0.75f),
        )
    }
}
