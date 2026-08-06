package com.ishireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ishireader.app.reader.AnnotationsUiState
import com.ishireader.app.reader.HighlightColor
import kotlinx.serialization.json.JsonElement
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import java.text.DateFormat
import java.util.Date

enum class AnnotationTab { ALL, HIGHLIGHTS, BOOKMARKS, NOTES }

private enum class AnnotationType { HIGHLIGHT, BOOKMARK, NOTE }

private data class AnnotationRow(
    val id: String,
    val type: AnnotationType,
    val locator: Locator?,
    val createdAt: Double,
    val updatedAt: Double? = null,
    val chapterTitle: String?,
    val colorHex: String? = null,
    val noteText: String? = null,
    val quote: String? = null
)

/**
 * Ports the website's in-reader annotations panel (StatefulAnnotationsContainer/AnnotationsContent)
 * -- an all/highlights/bookmarks/notes filter, book-order sort (not creation time), tap-to-jump,
 * a "bookmark this page" toolbar action, and inline note editing. No search, no chapter grouping,
 * no export -- the website itself doesn't offer those in this panel either (export is a
 * library-only, notes-only feature there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsPanelSheet(
    state: AnnotationsUiState,
    totalPositions: Int?,
    onJump: (Locator) -> Unit,
    onBookmarkThisPage: () -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onEditNote: (id: String, text: String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(AnnotationTab.ALL) }
    var descending by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Annotations", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onBookmarkThisPage) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Bookmark this page")
                    }
                    IconButton(onClick = { descending = !descending }) {
                        Icon(
                            if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                            contentDescription = "Sort order"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnnotationTab.entries.forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val rows = buildRows(state, tab, descending)
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No annotations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.height(400.dp).padding(bottom = 16.dp)) {
                    items(rows, key = { it.id }) { row ->
                        AnnotationRowItem(
                            row = row,
                            totalPositions = totalPositions,
                            onJump = { row.locator?.let(onJump) },
                            onDelete = {
                                when (row.type) {
                                    AnnotationType.HIGHLIGHT -> onDeleteHighlight(row.id)
                                    AnnotationType.BOOKMARK -> onDeleteBookmark(row.id)
                                    AnnotationType.NOTE -> onDeleteNote(row.id)
                                }
                            },
                            onEditNote = { text -> onEditNote(row.id, text) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationRowItem(
    row: AnnotationRow,
    totalPositions: Int?,
    onJump: () -> Unit,
    onDelete: () -> Unit,
    onEditNote: (String) -> Unit
) {
    var showNoteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onJump).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.colorHex != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(row.colorHex)))
            )
            Box(Modifier.padding(start = 8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            // Mirrors AnnotationsContent.tsx: a text-less bookmark/highlight falls back to the
            // chapter title as its own excerpt line, so the meta line below omits it in that case
            // to avoid showing the same chapter name twice in one row.
            val usesChapterAsExcerpt = row.type != AnnotationType.NOTE && row.quote == null && row.chapterTitle != null
            val body = when (row.type) {
                AnnotationType.NOTE -> row.noteText.orEmpty()
                else -> row.quote ?: row.chapterTitle ?: row.locator?.href?.toString().orEmpty()
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (row.type == AnnotationType.NOTE && row.quote != null) {
                Text(
                    "“${row.quote}”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val metaParts = listOfNotNull(
                if (!usesChapterAsExcerpt) row.chapterTitle else null,
                locationLabel(row.locator, totalPositions),
                if (row.type == AnnotationType.NOTE) {
                    "Last edited " + formatTimestamp(row.updatedAt ?: row.createdAt)
                } else {
                    formatTimestamp(row.createdAt)
                }
            )
            if (metaParts.isNotEmpty()) {
                Text(
                    metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (row.type == AnnotationType.NOTE) {
            IconButton(onClick = { showNoteDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit note")
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }

    if (showNoteDialog) {
        NoteEditorDialog(
            initialText = row.noteText.orEmpty(),
            isNew = false,
            onSave = { text -> onEditNote(text); showNoteDialog = false },
            onDelete = { onDelete(); showNoteDialog = false },
            onDismiss = { showNoteDialog = false }
        )
    }
}

private fun buildRows(state: AnnotationsUiState, tab: AnnotationTab, descending: Boolean): List<AnnotationRow> {
    val rows = mutableListOf<AnnotationRow>()
    if (tab == AnnotationTab.ALL || tab == AnnotationTab.HIGHLIGHTS) {
        state.highlights.forEach { h ->
            val locator = h.locator?.let(::parseLocator)
            rows += AnnotationRow(
                id = h.id,
                type = AnnotationType.HIGHLIGHT,
                locator = locator,
                createdAt = h.createdAt,
                chapterTitle = h.chapterTitle,
                colorHex = HighlightColor.fromId(h.color).hex,
                quote = locator?.text?.highlight
            )
        }
    }
    if (tab == AnnotationTab.ALL || tab == AnnotationTab.BOOKMARKS) {
        state.bookmarks.forEach { b ->
            val locator = b.locator?.let(::parseLocator)
            rows += AnnotationRow(
                id = b.id,
                type = AnnotationType.BOOKMARK,
                locator = locator,
                createdAt = b.createdAt,
                chapterTitle = b.chapterTitle,
                quote = locator?.text?.highlight
            )
        }
    }
    if (tab == AnnotationTab.ALL || tab == AnnotationTab.NOTES) {
        state.notes.forEach { n ->
            val locator = n.locator?.let(::parseLocator)
            rows += AnnotationRow(
                id = n.id,
                type = AnnotationType.NOTE,
                locator = locator,
                createdAt = n.createdAt,
                updatedAt = n.updatedAt,
                chapterTitle = n.chapterTitle,
                noteText = n.text,
                quote = locator?.text?.highlight
            )
        }
    }

    val sorted = rows.sortedWith(
        compareBy(
            { it.locator?.locations?.position ?: 0 },
            { it.locator?.locations?.totalProgression ?: 0.0 },
            { it.locator?.locations?.progression ?: 0.0 }
        )
    )
    return if (descending) sorted.reversed() else sorted
}

private fun parseLocator(json: JsonElement): Locator? =
    runCatching { Locator.fromJSON(JSONObject(json.toString())) }.getOrNull()

/** Mirrors the website's getLocationLabel.ts: page-of-total when a page count is known for this
 *  locator, otherwise the same one-decimal percent format used elsewhere in the app. */
private fun locationLabel(locator: Locator?, totalPositions: Int?): String? {
    if (locator == null) return null
    val page = locator.locations.position
    if (page != null && totalPositions != null) return "$page of $totalPositions"

    val totalProgression = locator.locations.totalProgression ?: return null
    val percent = kotlin.math.round(totalProgression.coerceIn(0.0, 1.0) * 1000) / 10
    return "%.1f%%".format(percent)
}

/** Mirrors the website's formatTimestamp.ts (Intl.DateTimeFormat with medium date + short time). */
private fun formatTimestamp(epochMillis: Double): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis.toLong()))
