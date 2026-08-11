package com.ishireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ishireader.app.reader.AnnotationsUiState
import com.ishireader.app.reader.DynamicPageCountState
import com.ishireader.app.reader.HighlightColor
import com.ishireader.app.reader.dynamicPageForLocator
import kotlinx.serialization.json.JsonElement
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import java.text.DateFormat
import java.util.Date

/**
 * Row-building/rendering shared by the in-reader AnnotationsPanelSheet and the book detail
 * screen's inline annotations section -- both need the exact same all/highlights/bookmarks/notes
 * filter, book-order sort, and per-row layout the website's AnnotationsContent.tsx defines, so
 * this is the one place that logic lives rather than being duplicated per screen.
 */
enum class AnnotationTab { ALL, HIGHLIGHTS, BOOKMARKS, NOTES }

internal enum class AnnotationType { HIGHLIGHT, BOOKMARK, NOTE }

internal data class AnnotationRow(
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

internal fun buildRows(state: AnnotationsUiState, tab: AnnotationTab, descending: Boolean): List<AnnotationRow> {
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

internal fun parseLocator(json: JsonElement): Locator? =
    runCatching { Locator.fromJSON(JSONObject(json.toString())) }.getOrNull()

/** Mirrors the website's getLocationLabel.ts: prefers the real, layout-aware dynamic page (see
 *  DynamicPageCountTracker) over the coarse positions()-derived one -- same "chapters vary in
 *  length" reasoning findExactPageForLocator documents -- then falls back to the coarse
 *  page-of-total, then to the same one-decimal percent format used elsewhere in the app. */
internal fun locationLabel(locator: Locator?, totalPositions: Int?, dynamicPageCount: DynamicPageCountState? = null): String? {
    if (locator == null) return null

    val dynamicTotal = dynamicPageCount?.totalPages
    if (dynamicPageCount != null && dynamicTotal != null) {
        dynamicPageForLocator(dynamicPageCount, locator)?.let { return "$it of $dynamicTotal" }
    }

    val page = locator.locations.position
    if (page != null && totalPositions != null) return "$page of $totalPositions"

    val totalProgression = locator.locations.totalProgression ?: return null
    val percent = kotlin.math.round(totalProgression.coerceIn(0.0, 1.0) * 1000) / 10
    return "%.1f%%".format(percent)
}

/** Mirrors the website's formatTimestamp.ts (Intl.DateTimeFormat with medium date + short time). */
internal fun formatTimestamp(epochMillis: Double): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis.toLong()))

@Composable
internal fun AnnotationRowItem(
    row: AnnotationRow,
    totalPositions: Int?,
    onJump: () -> Unit,
    onDelete: () -> Unit,
    onEditNote: (String) -> Unit,
    dynamicPageCount: DynamicPageCountState? = null
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
                locationLabel(row.locator, totalPositions, dynamicPageCount),
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
