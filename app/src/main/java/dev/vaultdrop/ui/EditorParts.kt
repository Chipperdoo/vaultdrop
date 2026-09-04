package dev.vaultdrop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vaultdrop.DayRow
import dev.vaultdrop.core.DailyNote
import dev.vaultdrop.core.IndexEntry
import dev.vaultdrop.core.TrashOption
import dev.vaultdrop.core.VaultConfig

/**
 * The `[[` strip. With no toolbar this is the wiki-link affordance, raised by
 * the characters you would have typed anyway — and it is where the Phase 2
 * index state lives, rather than in the chrome.
 */
@Composable
fun LinkStrip(
    query: String,
    count: String,
    indexing: Boolean,
    matches: List<IndexEntry>,
    onPick: (IndexEntry) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(c.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("[[", style = T.stripBrackets, color = c.primary)
            Spacer(Modifier.width(9.dp))
            Text(
                query.ifEmpty { "…" },
                style = T.stripQuery,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(9.dp))
            Text(count, style = T.stripCount, color = c.outline)
        }

        if (indexing) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot()
                Spacer(Modifier.width(11.dp))
                Text("Indexing vault…", style = T.indexing, color = c.onSurfaceVariant)
            }
        } else {
            Column(
                Modifier
                    .heightIn(max = 188.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
            ) {
                matches.forEach { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .clickable { onPick(entry) }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            entry.name,
                            style = T.stripTitle,
                            color = c.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.path,
                            style = T.stripPath,
                            color = c.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val c = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "indexing")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "indexingAlpha",
    )
    Box(Modifier.size(7.dp).alpha(alpha).clip(CircleShape).background(c.primary))
}

/** Phase 2 talking back — never blocking, always dismissible. */
@Composable
fun NoticeBar(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text, style = T.notice, color = c.onSurface.copy(alpha = 0.84f), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            "OK",
            style = T.noticeAction,
            color = c.primary,
            modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 2.dp),
        )
    }
}

/** Tapping the date opens this. Existence is resolved when the sheet opens. */
@Composable
fun DaySheet(days: List<DayRow>, onPick: (DayRow) -> Unit, onDismiss: () -> Unit) {
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
                .padding(top = 8.dp, bottom = 24.dp),
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
            Spacer(Modifier.height(12.dp))
            days.forEach { day ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onPick(day) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Three states, because the sheet is now the only way to make
                    // a note: filled for the day you are on, filled-muted for a day
                    // that has one, a ring for a day tapping would create.
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .then(
                                if (day.isCurrent || day.exists) {
                                    Modifier.background(
                                        if (day.isCurrent) c.primary else c.onSurface.copy(alpha = 0.28f),
                                    )
                                } else {
                                    Modifier.border(1.dp, c.outline, CircleShape)
                                },
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(day.label, style = T.sheetLabel, color = c.onSurface, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            day.sub,
                            style = T.sheetSub,
                            color = c.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deleting is the only thing in here that touches a note the user did not ask to
 * change, so it asks first — and names the file rather than the day, because the
 * label on the top bar is a format, not a path.
 */
@Composable
fun ConfirmDelete(
    path: String,
    mode: TrashOption,
    explainFallback: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("DELETE NOTE", style = T.eyebrow, color = c.error)
            Spacer(Modifier.height(14.dp))
            Text(path, style = T.sheetLabel, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Text(
                when (mode) {
                    TrashOption.None ->
                        "Your vault is set to delete permanently — trashOption: none in " +
                            "app.json. No copy is kept and this cannot be undone from here."
                    else ->
                        "Moves it to ${DailyNote.TRASH_DIR}/ under its own name, where Obsidian's " +
                            "local trash puts it. Nothing here empties it — the copy stays until " +
                            "you remove it yourself."
                },
                style = T.cardBody,
                color = c.onSurfaceVariant,
            )
            // Said once, the first time it matters: the vault asked for something
            // the platform will not do, and the difference should not pass in
            // silence just because the fallback is the safe one.
            if (explainFallback) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your vault asks for the system trash. Android does not give an app " +
                        "working through a folder grant any access to it, so VaultDrop uses " +
                        "${DailyNote.TRASH_DIR}/ instead. This is the only time you will see this.",
                    style = T.cardBody,
                    color = c.onSurface.copy(alpha = 0.82f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                DialogAction("CANCEL", c.onSurfaceVariant, onDismiss)
                Spacer(Modifier.width(4.dp))
                DialogAction("DELETE", c.error, onConfirm)
            }
        }
    }
}

/**
 * Overflow's Reindex vault, confirmed the same way Delete is: a scrim over the
 * note rather than firing straight off a menu tap.
 */
@Composable
fun ConfirmReindex(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("REINDEX VAULT", style = T.eyebrow, color = c.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                "Walks every note in the vault from scratch. VaultDrop already picks up new " +
                    "and edited notes incrementally on its own, so this is only for a full " +
                    "refresh — if you think the index has drifted from what's on disk.",
                style = T.cardBody,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                DialogAction("CANCEL", c.onSurfaceVariant, onDismiss)
                Spacer(Modifier.width(4.dp))
                DialogAction("REINDEX", c.primary, onConfirm)
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        style = T.noticeAction,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/**
 * Board 1c, left. A vault where Daily Notes was never opened has no config file
 * — that is the defaults column of the brief's table, not a failure.
 */
@Composable
fun MissingConfigCard(config: VaultConfig) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text("No Daily Notes config", style = T.cardTitle, color = c.primary)
        Spacer(Modifier.height(9.dp))
        Text(
            ".obsidian/daily-notes.json is missing.\nUsing defaults until it appears.",
            style = T.cardBody,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DefaultRow("format", config.format, muted = false)
            DefaultRow("folder", config.folder.ifEmpty { "vault root" }, muted = config.folder.isEmpty())
            DefaultRow("template", config.template.ifEmpty { "none" }, muted = config.template.isEmpty())
        }
    }
}

@Composable
private fun DefaultRow(label: String, value: String, muted: Boolean) {
    val c = MaterialTheme.colorScheme
    Row {
        Text(label, style = T.cardRow, color = c.outline, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(10.dp))
        Text(value, style = T.cardRow, color = if (muted) c.outline else c.onSurface)
    }
}
