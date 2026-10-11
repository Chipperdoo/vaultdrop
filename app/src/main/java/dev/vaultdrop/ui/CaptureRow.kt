package dev.vaultdrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The row above the keyboard that holds the capture buttons. It gives way to
 * the `[[` strip while a link is being typed, so the two never stack.
 *
 * This file only lays the buttons out. Each feature adds its own buttons at the
 * call site in [EditorScreen], so voice and photos can land in either order
 * without touching each other's code.
 */
@Composable
fun CaptureRow(content: @Composable RowScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceContainer)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * One button in [CaptureRow]: the same 44dp circle as every other tap target in
 * the chrome. The glyph is drawn, so [label] is what a screen reader announces.
 */
@Composable
fun CaptureButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(T.touch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
