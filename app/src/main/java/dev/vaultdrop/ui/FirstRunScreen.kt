package dev.vaultdrop.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Board 1a, first frame. Says what the app reads, what it writes, and what it
 * will never touch, then hands off to the system picker.
 */
@Composable
fun FirstRunScreen(
    errorTitle: String?,
    errorDetail: String?,
    onChooseVault: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("VAULTDROP", style = T.eyebrow, color = c.primary)
            Spacer(Modifier.height(26.dp))
            Text("Point this at your vault.", style = T.headline, color = c.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(
                "It reads the Daily Notes settings you already have, and writes plain " +
                    ".md files into the folder you already use. Nothing syncs. Nothing renders.",
                style = T.lede,
                color = c.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }

        Column {
            if (errorTitle != null) {
                NotAVaultCard(errorTitle, errorDetail.orEmpty())
                Spacer(Modifier.height(18.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.onSurface.copy(alpha = 0.09f)),
            )
            Spacer(Modifier.height(18.dp))
            CapabilityRow("reads", ".obsidian/daily-notes.json", accent = true)
            CapabilityRow("writes", "<folder>/<today>.md", accent = true)
            CapabilityRow("never", "anything else in the vault", accent = false)
            Spacer(Modifier.height(26.dp))

            // The picker hands back whichever folder is open when Use this
            // folder is tapped, so the easy mistakes are one level off either
            // way: the folder holding the vault, or the daily notes folder.
            // Short, because the screen does not scroll; the "Not a vault"
            // card spells out both mistakes if one is made.
            Text(
                "In the picker, go into your vault's own folder, the one with the vault's name, " +
                    "then tap Use this folder.",
                style = T.lede,
                color = c.onSurface,
            )
            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.primary)
                    .clickable(onClick = onChooseVault),
                contentAlignment = Alignment.Center,
            ) {
                Text("Choose vault folder", style = T.button, color = c.onPrimary)
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String, accent: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = T.capability,
            color = if (accent) c.primary else c.outline,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = T.capability, color = c.outline)
    }
}

/** The validation failure: the one place the error role appears. */
@Composable
fun NotAVaultCard(title: String, detail: String) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.errorContainer)
            .padding(14.dp),
    ) {
        Text(title, style = T.cardTitle, color = c.onErrorContainer)
        Spacer(Modifier.height(5.dp))
        Text(detail, style = T.cardBody, color = c.onErrorContainer.copy(alpha = 0.82f))
    }
}
