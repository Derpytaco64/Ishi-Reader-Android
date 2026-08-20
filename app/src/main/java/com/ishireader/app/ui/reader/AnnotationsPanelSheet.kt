package com.ishireader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
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
import androidx.compose.ui.unit.dp
import com.ishireader.app.reader.AnnotationsUiState
import com.ishireader.app.reader.DynamicPageCountState
import org.readium.r2.shared.publication.Locator

/**
 * Ports the website's in-reader annotations panel (StatefulAnnotationsContainer/AnnotationsContent)
 * -- an all/highlights/bookmarks/notes filter, book-order sort (not creation time), tap-to-jump,
 * a "bookmark this page" toolbar action, and inline note editing. No search, no chapter grouping,
 * no export -- the website itself doesn't offer those in this panel either (export is a
 * library-only, notes-only feature there). Row layout/filtering itself lives in AnnotationRows.kt,
 * shared with the book detail screen's inline annotations section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsPanelSheet(
    state: AnnotationsUiState,
    totalPositions: Int?,
    dynamicPageCount: DynamicPageCountState?,
    onJump: (Locator) -> Unit,
    onBookmarkThisPage: () -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onEditNote: (id: String, text: String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDismiss: () -> Unit,
    // Highlighting needs selectable text, which a comic page (rendered as a single image) doesn't
    // have -- so comics only ever get bookmarks and page-attached notes, and the Highlights filter
    // chip is meaningless clutter there.
    isComic: Boolean = false
) {
    var tab by remember { mutableStateOf(AnnotationTab.ALL) }
    var descending by remember { mutableStateOf(false) }
    val availableTabs = if (isComic) {
        AnnotationTab.entries.filter { it != AnnotationTab.HIGHLIGHTS }
    } else {
        AnnotationTab.entries
    }

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
                availableTabs.forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val rows = buildRows(state, tab, descending, isComic)
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
                            dynamicPageCount = dynamicPageCount,
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
