package dev.vaultdrop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import dev.vaultdrop.DayRow
import dev.vaultdrop.UiState
import dev.vaultdrop.VoiceOption
import dev.vaultdrop.core.IndexEntry
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Board 1a. The editor is the app: one Material line carrying the date, and the
 * note. Below it, one slim row of capture buttons that gives way to the `[[`
 * strip. Everything else is raised by what you type or by the date.
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
    onMic: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
    onMicStop: () -> Unit,
    onMicCancel: () -> Unit,
    onMicPick: (VoiceOption) -> Unit,
    onMicPickerClose: () -> Unit,
    onMicSettings: () -> Unit,
    /** Opens the app a notice names, by package. */
    onMicOpenApp: (String) -> Unit,
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
    val overlayOpen = state.daysOpen || state.deleteOpen || state.reindexOpen || state.voicePicker != null
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
                pinned = state.captureInFlight,
                onOpenDays = onOpenDays,
                onStepDay = onStepDay,
                onOpenSearch = onOpenSearch,
                onChangeVault = onChangeVault,
                onReloadConfig = onReloadConfig,
                onAskReindex = onAskReindex,
                onAskDelete = onAskDelete,
                onVoiceInput = onMicSettings,
            )

            if (state.configMissing) {
                MissingConfigCard(state.config)
                Spacer(Modifier.height(20.dp))
            }

            // Frontmatter and the note body share one scroll: the chip is the top of
            // that stack, not a fixed header, so it scrolls away like everything else.
            // The field sizes to its own content (no imposed min height) — giving it
            // a computed floor fought with BasicTextField's own "keep the cursor in
            // view" scrolling and clipped the note's opening lines when a day loads
            // with the caret at the end. A plain tap-to-focus on the column covers
            // the empty space below a short note instead.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .daySwipe(
                        enabled = !state.daysOpen && !state.captureInFlight,
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                focus.requestFocus()
                                body.edit { placeCursorAtEnd() }
                            },
                        ),
                ) {
                    if (state.frontmatter.isNotBlank()) {
                        // Remembered against the fence itself: counting its lines
                        // meant a trimmed copy and a list of every line in it, and
                        // this used to be recomposed by every keystroke in the note
                        // below. The trim is the same one the body below is drawn
                        // from, so it is held rather than taken twice.
                        val fmText = remember(state.frontmatter) { state.frontmatter.trim() }
                        val fmLines = remember(fmText) { fmText.lines().size }
                        FrontmatterChip(
                            open = state.fmOpen,
                            lineCount = fmLines,
                            onToggle = onToggleFrontmatter,
                        )
                        if (state.fmOpen) {
                            Text(
                                fmText,
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

                    // State-backed: an edit goes straight into the field's own
                    // buffer instead of round-tripping a whole new UiState through
                    // the view model and invalidating this screen for it.
                    BasicTextField(
                        state = body,
                        textStyle = T.note.copy(color = c.onSurface.copy(alpha = 0.92f)),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    )
                }
            }

            // Sits above the `[[` strip rather than giving way to it, so Stop and
            // Cancel stay in reach while a link is being typed.
            state.dictation?.let { DictationStrip(it, onStop = onMicStop, onCancel = onMicCancel) }

            if (state.linkOpen) {
                LinkStrip(
                    query = state.linkQuery,
                    count = state.linkCount,
                    indexing = state.indexing,
                    matches = state.linkMatches,
                    onPick = onPickLink,
                )
            } else {
                // Stays up while a capture holds the note, with its buttons off,
                // so the row does not jump in and out under the thumb.
                CaptureRow {
                    val ready = !state.captureInFlight
                    CaptureButton("Voice input", ready, onMic) { MicGlyph(barInk(ready)) }
                    CaptureButton("Take a photo", ready, onTakePhoto) { CameraGlyph(barInk(ready)) }
                    CaptureButton("Choose photos", ready, onChoosePhotos) { GalleryGlyph(barInk(ready)) }
                }
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
                    // Clear of the frontmatter chip: the bar, its top margin, the 30dp chip, its gap.
                    .padding(top = 104.dp)
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
            val app = state.noticeApp?.takeIf { it.notice == notice }
            NoticeBar(
                text = notice,
                onDismiss = onDismissNotice,
                action = app?.let { "OPEN ${it.label.uppercase()}" },
                onAction = { app?.let { onMicOpenApp(it.packageName) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    // Clear of the capture row, so a notice never sits on its buttons.
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp + T.touch),
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

        state.voicePicker?.let { VoiceSheet(it, onPick = onMicPick, onDismiss = onMicPickerClose) }
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
    /** A capture holds the note on screen, so nothing here may leave it. */
    pinned: Boolean,
    onOpenDays: () -> Unit,
    onStepDay: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onChangeVault: () -> Unit,
    onReloadConfig: () -> Unit,
    onAskReindex: () -> Unit,
    onAskDelete: () -> Unit,
    onVoiceInput: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(T.barHeight)
            .background(c.surfaceContainerHigh)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(enabled = !pinned, onClick = onOpenDays),
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

        val prev = canGoPrev && !pinned
        val next = canGoNext && !pinned
        BarButton(enabled = prev, onClick = { onStepDay(-1) }) {
            Chevron(barInk(prev), pointsLeft = true)
        }
        BarButton(enabled = next, onClick = { onStepDay(1) }) {
            Chevron(barInk(next), pointsLeft = false)
        }
        BarButton(enabled = true, onClick = onOpenSearch) {
            SearchGlyph(c.onSurface)
        }
        OverflowButton(
            canDelete = !pinned,
            onChangeVault = onChangeVault,
            onReloadConfig = onReloadConfig,
            onAskReindex = onAskReindex,
            onAskDelete = onAskDelete,
            onVoiceInput = onVoiceInput,
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
    canDelete: Boolean,
    onChangeVault: () -> Unit,
    onReloadConfig: () -> Unit,
    onAskReindex: () -> Unit,
    onAskDelete: () -> Unit,
    onVoiceInput: () -> Unit,
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
                text = { Text("Voice input…", style = T.stripTitle) },
                onClick = { open = false; onVoiceInput() },
            )
            DropdownMenuItem(
                text = { Text("Change vault", style = T.stripTitle) },
                onClick = { open = false; onChangeVault() },
            )
            // Last, and in the error ink: the only item here that moves a note.
            // Disabled, it takes the menu's own disabled ink instead.
            DropdownMenuItem(
                text = {
                    Text("Delete note", style = T.stripTitle, color = if (canDelete) c.error else Color.Unspecified)
                },
                onClick = { open = false; onAskDelete() },
                enabled = canDelete,
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
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)
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
