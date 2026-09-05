package dev.vaultdrop.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Drag the note left or right to step between days.
 *
 * The note is a text field, which would otherwise claim the drag for selection,
 * so this watches the Initial pass and only consumes once the gesture is clearly
 * horizontal — past 16dp and at least 2x wider than it is tall. Below that
 * threshold the events fall through untouched and selection behaves normally.
 *
 * [onSettle] is raised only for a gesture that became a drag, so its argument is
 * always the offset the note is actually sitting at and never a no-op zero.
 */
fun Modifier.daySwipe(
    enabled: Boolean,
    canGoForward: Boolean,
    canGoBack: Boolean,
    onDrag: (Float) -> Unit,
    onSettle: (Float) -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(canGoForward, canGoBack) {
    val slop = 16.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var claimed = false
        var offset = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // The pointer left the stream without ever coming up: a cancel, or a
            // system gesture taking the touch over. A drag that was claimed still
            // has to settle, or the note is left translated and dimmed until the
            // next one.
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (claimed) onSettle(offset)
                break
            }
            if (!change.pressed) {
                // Only a claimed drag settles. A tap, or a vertical scroll of the
                // note, never moved the offset off zero, and settling it anyway
                // ran the frame clock through a 200ms animation from zero to zero
                // for every touch that landed in the editor.
                if (claimed) {
                    change.consume()
                    onSettle(offset)
                }
                break
            }
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            if (!claimed && abs(dx) >= slop && abs(dx) >= abs(dy) * 2f) claimed = true
            if (claimed) {
                // Rubber-band the direction that has nowhere to go.
                val blocked = if (dx < 0f) !canGoForward else !canGoBack
                offset = if (blocked) dx * SWIPE_BLOCKED_DAMPING else dx
                onDrag(offset)
                change.consume()
            }
        }
    }
}

/** Past this, letting go commits the step. */
const val SWIPE_COMMIT_AT = 180f

/** How hard the edge with nowhere to go resists the drag. */
const val SWIPE_BLOCKED_DAMPING = 0.22f
