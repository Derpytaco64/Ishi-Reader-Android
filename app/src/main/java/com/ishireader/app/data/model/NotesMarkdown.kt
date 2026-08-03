package com.ishireader.app.data.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mirrors exportNotes.ts's buildNotesMarkdown: one markdown document from a book's notes, oldest
 *  first (their actual writing order), each with its timestamp and the passage it was attached to
 *  (if any). note.text is itself markdown, embedded as-is rather than escaped. */
fun buildNotesMarkdown(title: String, author: String?, notes: List<StoredNote>): String {
    val sorted = notes.sortedBy { it.createdAt }
    val header = "# $title\n" + (author?.takeIf { it.isNotBlank() }?.let { "*by $it*\n" } ?: "")

    if (sorted.isEmpty()) {
        return "$header\n_No notes yet._\n"
    }

    val sections = sorted.mapIndexed { index, note ->
        val quote = (note.locator?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("highlight") as? JsonPrimitive)?.contentOrNull

        buildList {
            add("## Note ${index + 1} — ${formatNoteTimestamp(note.createdAt)}")
            note.chapterTitle?.let { add("*$it*") }
            quote?.let { add("> $it") }
            add(note.text)
        }.joinToString("\n\n")
    }

    return "$header\n${sections.joinToString("\n\n---\n\n")}\n"
}

private fun formatNoteTimestamp(epochMillis: Double): String {
    val formatter = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis.toLong()))
}

/** Keeps the export filename filesystem-safe without mangling the title past recognition. */
fun notesExportFilename(title: String): String {
    val safeTitle = title.trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
        .replace(Regex("\\s+"), " ")
        .trim()
    return "${safeTitle.ifEmpty { "Untitled" }} - Notes.md"
}
