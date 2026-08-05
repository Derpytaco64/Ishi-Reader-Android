package com.ishireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ishireader.app.reader.HighlightColor

/** Shown when tapping an existing highlight decoration -- pick a new color from the fixed
 *  5-swatch palette, or delete. */
@Composable
fun HighlightEditDialog(
    onColorSelected: (HighlightColor) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HighlightColor.entries.forEach { color ->
                    Column(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(color.hex)))
                            .clickable { onColorSelected(color) }
                    ) {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/** Shown both for creating a new note (isNew = true, initialText = "") and for viewing/editing an
 *  existing one -- mirrors the website's NoteOverlay (view mode with Edit/Delete, switching to an
 *  inline textarea), collapsed into one dialog with a single "editing" toggle. */
@Composable
fun NoteEditorDialog(
    initialText: String,
    isNew: Boolean,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf(isNew) }
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add note" else "Note") },
        text = {
            if (editing) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a note...") },
                    minLines = 3
                )
            } else {
                Text(text)
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
            } else {
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        },
        dismissButton = {
            if (!editing && onDelete != null) {
                TextButton(onClick = onDelete) { Text("Delete") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
