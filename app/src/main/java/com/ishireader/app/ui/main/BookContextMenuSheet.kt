package com.ishireader.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CustomShelf

/** Single shared instance, opened by long-pressing any book cover across Home/Library/Series/
 *  Shelves -- mirrors StatefulBookContextMenu.tsx's menu (right-click there, long-press here since
 *  there's no touch equivalent of a cursor-anchored popover) with the same four actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookContextMenuSheet(
    book: Book,
    shelves: List<CustomShelf>,
    canRemoveFromContinueReading: Boolean,
    showDeleteDownload: Boolean,
    onDismiss: () -> Unit,
    onGoToSeries: () -> Unit,
    onExportNotes: () -> Unit,
    onToggleShelf: (CustomShelf) -> Unit,
    onCreateShelf: () -> Unit,
    onRemoveFromContinueReading: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Divider()

            if (book.series != null) {
                MenuRow("Go to Series") { onGoToSeries(); onDismiss() }
            }
            MenuRow("Export Notes") { onExportNotes(); onDismiss() }
            if (showDeleteDownload) {
                MenuRow("Delete Downloaded File") { onDeleteDownload(); onDismiss() }
            }

            Divider()
            Text(
                text = "Shelves",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            shelves.forEach { shelf ->
                val inShelf = shelf.books.any { it.url == book.url }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleShelf(shelf) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(shelf.icon)
                    Text(
                        text = shelf.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    if (inShelf) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            MenuRow("+ Create new shelf") { onCreateShelf(); onDismiss() }

            if (canRemoveFromContinueReading) {
                Divider()
                MenuRow("Remove from Continue Reading") { onRemoveFromContinueReading(); onDismiss() }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label)
    }
}
