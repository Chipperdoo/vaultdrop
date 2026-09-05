package dev.vaultdrop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import dev.vaultdrop.DayRow
import dev.vaultdrop.UiState
import dev.vaultdrop.core.IndexEntry
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Board 1a. The editor is the app: one Material line carrying the date, and the
 * note. No toolbar — everything else is raised by what you type or by the date.
 */
@Composable
fun EditorScreen(
    state: UiState,
    body: TextFieldState,
    onToggleFrontmatter: () -> Unit,
    onOpenDays: () -> Unit,
    onCloseDays: () -> Unit,
    onPickDay: (DayRow) -> Unit,
    onStepDay: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onPickLink: (IndexEntry) -> Unit,
    onDismissNotice: () -> Unit,
    onChangeVault: () -> Unit,
    onReloadConfig: () -> Unit,
    onAskReindex: () -> Unit,
    onConfirmReindex: () -> Unit,
    onCancelReindex: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // The day sheet and the delete/reindex confirmations all slide up over the
    // note, so the IME has to come down with them — dropping focus takes the
    // keyboard with it, and dismissing any one of them hands focus straight back
    // below. The confirmations need this as much as the sheet does: their
    // buttons sit low enough that an open keyboard hides the very choice they
    // are asking for.
    val overlayOpen = state.daysOpen || state.deleteOpen || state.reindexOpen
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) focusManager.clearFocus()
    }

    // Land the caret at the end of whatever day is on screen, ready to append.
    LaunchedEffect(state.dateLabel, overlayOpen) {
        if (!overlayOpen) runCatching { focus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            TopBar(
                dateLabel = state.dateLabel,
                created = state.created,
                canGoPrev = state.canGoPrev,
                canGoNext = state.canGoNext,
                onOpenDays = onOpenDays,
                onStepDay = onStepDay,
                onOpenSearch = onOpenSearch,
                onChangeVault = onChangeVault,
                onReloadConfig = onReloadConfig,
                onAskReindex = onAskReindex,
                onAskDelete = onAskDelete,
            )

            if (state.configMissing) {
                MissingConfigCard(state.config)
                Spacer(Modifier.height(20.dp))
            }

            if (state.frontmatter.isNotBlank()) {
                // Remembered against the fence itself: counting its lines meant
                // a trimmed copy and a list of every line in it, and this used
                // to be recomposed by every keystroke in the note below.
                val fmLines = remember(state.frontmatter) { state.frontmatter.trim().lines().size }
                FrontmatterChip(
                    open = state.fmOpen,
                    lineCount = fmLines,
                    onToggle = onToggleFrontmatter,
                )
                if (state.fmOpen) {
                    Text(
                        state.frontmatter.trim(),
                        style = T.fmBody,
                        color = c.onSurfaceVariant.copy(alpha = 0.92f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surfaceContainer)
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .daySwipe(
                        enabled = !state.daysOpen,
                        canGoForward = state.canGoNext,
                        canGoBack = state.canGoPrev,
                        onDrag = { scope.launch { drag.snapTo(it) } },
                        onSettle = { dx ->
                            scope.launch { drag.animateTo(0f, tween(200)) }
                            when {
                                dx > SWIPE_COMMIT_AT -> onStepDay(-1)
                                dx < -SWIPE_COMMIT_AT -> onStepDay(1)
                            }
                        },
                    )
                    .graphicsLayer {
                        translationX = drag.value * 0.45f
                        alpha = 1f - min(abs(drag.value) / 500f, 0.4f)
                    },
            ) {
                // State-backed: an edit goes straight into the field's own
                // buffer instead of round-tripping a whole new UiState through
                // the view model and invalidating this screen for it.
                BasicTextField(
                    state = body,
                    textStyle = T.note.copy(color = c.onSurface.copy(alpha = 0.92f)),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focus)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                )
            }

            if (state.linkOpen) {
                LinkStrip(
                    query = state.linkQuery,
                    count = state.linkCount,
                    indexing = state.indexing,
                    matches = state.linkMatches,
                    onPick = onPickLink,
                )
            }
        }

        // Which day letting go would land on.
        //
        // Read through derivedStateOf, not directly: reading drag.value in the
        // composition scope recomposed this whole screen on every frame of a
        // drag and of the settle animation after it. The hint text only changes
        // when the gesture crosses the threshold or flips direction, so that is
        // all this recomposes for. The translation below reads drag.value inside
        // graphicsLayer's lambda, which defers it to the draw phase.
        val hint by remember(state.prevDayLabel, state.nextDayLabel) {
            derivedStateOf {
                // Not while it settles back: the day has already flipped, so the
                // pill would spend the animation naming the day after the new one.
                if (drag.isRunning) null
                else swipeHint(drag.value, state.prevDayLabel, state.nextDayLabel)
            }
        }
        hint?.let { (back, hintText) ->
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    // Clear of the frontmatter chip: the bar, the 30dp chip, its gap.
                    .padding(top = 94.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.primary)
                    .padding(horizontal = 19.5.dp, vertical = 10.5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same mark the day steppers use, on the side time is moving.
                // The edge with nowhere to go gets none: it points at no day.
                if (back == true) {
                    Chevron(c.onPrimary, pointsLeft = true, size = 13.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(hintText, style = T.swipeHint, color = c.onPrimary, maxLines = 1)
                if (back == false) {
                    Spacer(Modifier.width(8.dp))
                    Chevron(c.onPrimary, pointsLeft = false, size = 13.dp)
                }
            }
        }

        state.notice?.let { notice ->
            NoticeBar(
                text = notice,
                onDismiss = onDismissNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
            )
        }

        if (state.daysOpen) {
            DaySheet(days = state.days, onPick = onPickDay, onDismiss = onCloseDays)
        }

        if (state.deleteOpen) {
            ConfirmDelete(
                path = state.notePath,
                mode = state.deleteMode,
                explainFallback = state.explainTrashFallback,
                onConfirm = onConfirmDelete,
                onDismiss = onCancelDelete,
            )
        }

        if (state.reindexOpen) {
            ConfirmReindex(onConfirm = onConfirmReindex, onDismiss = onCancelReindex)
        }
    }
}

/**
 * Which way the step travels and what the pill says; null to stay hidden.
 * The direction is itself null when the drag has nowhere to land.
 */
private fun swipeHint(
    dx: Float,
    prevDayLabel: String?,
    nextDayLabel: String?,
): Pair<Boolean?, String>? {
    val back = dx > 0
    val label = if (back) prevDayLabel else nextDayLabel
    // Only past the commit point: the pill says that letting go now will actually
    // land you somewhere. The slide and the fade already say a drag is happening.
    // The edge with nowhere to go is rubber-banded, so scale its threshold to match
    // — measured on the offset it would take most of the screen's width to warn.
    val at = if (label == null) SWIPE_COMMIT_AT * SWIPE_BLOCKED_DAMPING else SWIPE_COMMIT_AT
    if (abs(dx) < at) return null
    // Each edge names the day it would land on, or says why it would not move.
    return if (label != null) back to label
    else null to if (back) "no earlier note" else "today is the last note"
}

@Composable
private fun TopBar(
    dateLabel: String,
    created: Boolean,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onOpenDays: () -> Unit,
    onStepDay: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onChangeVault: () -> Unit,
    onReloadConfig: () -> Unit,
    onAskReindex: () -> Unit,
    onAskDelete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(T.barHeight)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(onClick = onOpenDays),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dateLabel,
                style = T.date,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            TriangleDown(c.outline, 7.dp)
            if (created) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "new",
                    style = T.newTag,
                    color = c.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.primaryContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        BarButton(enabled = canGoPrev, onClick = { onStepDay(-1) }) {
            Chevron(barInk(canGoPrev), pointsLeft = true)
        }
        BarButton(enabled = canGoNext, onClick = { onStepDay(1) }) {
            Chevron(barInk(canGoNext), pointsLeft = false)
        }
        BarButton(enabled = true, onClick = onOpenSearch) {
            SearchGlyph(c.onSurface)
        }
        OverflowButton(
            onChangeVault = onChangeVault,
            onReloadConfig = onReloadConfig,
            onAskReindex = onAskReindex,
            onAskDelete = onAskDelete,
        )
    }
}

@Composable
private fun barInk(enabled: Boolean) =
    if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

@Composable
private fun BarButton(enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(T.touch)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun OverflowButton(
    onChangeVault: () -> Unit,
    onReloadConfig: () -> Unit,
    onAskReindex: () -> Unit,
    onAskDelete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .size(T.touch)
                .clip(CircleShape)
                .clickable { open = true },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            repeat(3) {
                Box(Modifier.size(3.5.dp).clip(CircleShape).background(c.onSurfaceVariant))
            }
            Spacer(Modifier.weight(1f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Reload config", style = T.stripTitle) },
                onClick = { open = false; onReloadConfig() },
            )
            DropdownMenuItem(
                text = { Text("Reindex vault", style = T.stripTitle) },
                onClick = { open = false; onAskReindex() },
            )
            DropdownMenuItem(
                text = { Text("Change vault", style = T.stripTitle) },
                onClick = { open = false; onChangeVault() },
            )
            // Last, and in the error ink: the only item here that moves a note.
            DropdownMenuItem(
                text = { Text("Delete note", style = T.stripTitle, color = c.error) },
                onClick = { open = false; onAskDelete() },
            )
        }
    }
}

/** Frontmatter is collapsed by default; the file itself is never rewritten. */
@Composable
private fun FrontmatterChip(open: Boolean, lineCount: Int, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceContainer)
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (open) TriangleDown(c.outline, 7.dp) else TriangleRight(c.outline, 7.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (open) "hide frontmatter" else "frontmatter · $lineCount lines",
            style = T.chipText,
            color = c.onSurfaceVariant,
        )
    }
}
