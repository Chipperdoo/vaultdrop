package dev.vaultdrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.vaultdrop.UiState
import dev.vaultdrop.core.IndexEntry
import dev.vaultdrop.core.SearchHit

/**
 * Board 1a, search. Two speeds: names filter as you type off the index, and the
 * Search key reads the vault for the text inside every note.
 *
 * Neither one runs here or on the main thread — the screen only ever shows what
 * the view model hands it.
 */
@Composable
fun SearchScreen(
    state: UiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onOpenResult: (IndexEntry) -> Unit,
    onBack: () -> Unit,
    onReadFull: (IndexEntry, (String) -> Unit) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val opened = state.openedNote

    Column(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        if (opened == null) {
            SearchBar(
                query = state.searchQuery,
                placeholder = "Search ${state.indexSize} notes",
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClear = onClear,
                onClose = onClose,
            )
            Text(
                state.searchStats,
                style = T.searchStats,
                color = c.outline,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
            )
            if (state.searchQuery.isNotBlank() && state.searchResults.isEmpty()) {
                Text(
                    if (state.searchScanning) {
                        "Reading the vault…"
                    } else {
                        "No note is named that. Press Enter to search inside every note instead."
                    },
                    style = T.empty,
                    color = c.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                ) {
                    items(state.searchResults, key = { it.entry.path }) { hit ->
                        ResultRow(hit, state.searchQuery) { onOpenResult(hit.entry) }
                    }
                }
            }
        } else {
            OpenNoteView(opened, onBack, onReadFull)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(T.barHeight).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(T.touch).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Chevron(c.primary, pointsLeft = true, size = 13.dp) }
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = T.searchInput.copy(color = c.onSurface),
            cursorBrush = SolidColor(c.primary),
            // The Search key is what asks for full text; typing only filters names.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(placeholder, style = T.searchInput, color = c.outline)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(T.touch).clip(CircleShape).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) { CloseGlyph(c.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ResultRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val entry = hit.entry
    // The window is a hundred-odd characters and the query is already inside it,
    // so marking the hit is cheap — but not cheap enough to redo every frame.
    val quoted = hit.snippet?.let { window ->
        remember(window, query, c.primary, c.onPrimary) { mark(window, query, c.primary, c.onPrimary) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (quoted == null) 56.dp else 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.name,
                style = T.resultTitle,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Chevron(c.outline.copy(alpha = 0.7f), pointsLeft = false, size = 8.dp)
        }
        // A name match has no note text behind it to quote, and reading one just
        // to fill the gap would put the vault back on the results list.
        if (quoted != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                quoted,
                style = T.resultSnippet,
                color = c.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.path,
            style = T.resultPath,
            color = c.outline.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The scanner's window, with the hit inside it picked out. */
private fun mark(
    window: String,
    query: String,
    hitBackground: androidx.compose.ui.graphics.Color,
    hitInk: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val q = query.trim()
    // Whitespace inside the window was collapsed on the way here, so a query that
    // spanned a line break no longer reads back exactly. Show it unmarked.
    val at = if (q.isEmpty()) -1 else window.indexOf(q, ignoreCase = true)
    if (at == -1) return AnnotatedString(window)

    return buildAnnotatedString {
        append(window.substring(0, at))
        withStyle(SpanStyle(background = hitBackground, color = hitInk)) {
            append(window.substring(at, at + q.length))
        }
        append(window.substring(at + q.length))
    }
}

@Composable
private fun ColumnScope.OpenNoteView(
    entry: IndexEntry,
    onBack: () -> Unit,
    onReadFull: (IndexEntry, (String) -> Unit) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var body by remember(entry.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.path) { onReadFull(entry) { body = it } }

    Row(
        Modifier.fillMaxWidth().height(T.barHeight).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(T.touch).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Chevron(c.primary, pointsLeft = true, size = 13.dp) }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = T.openTitle, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(entry.path, style = T.openPath, color = c.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Text(
        body ?: "reading ${entry.name}…",
        style = T.note,
        color = c.onSurface.copy(alpha = if (body == null) 0.5f else 0.92f),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    )
}
