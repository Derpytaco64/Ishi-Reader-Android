package com.ishireader.app.ui.reader

import android.graphics.PointF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Long-press context menu for a manga/comic page -- there's no selectable text to anchor
 * [AnnotationSelectionActionModeCallback]'s Highlight/Note/Bookmark/Copy toolbar to (the whole page
 * is one image), so this is the comic-only equivalent: Copy (the page image, to the system
 * clipboard), Bookmark, and Note, both applied to the current page's own locator. Positioned and
 * dismissed the same way [HighlightColorPopover] is -- a floating card anchored to the touch point,
 * with a transparent tap-outside-to-dismiss scrim rather than a full modal.
 */
@Composable
fun ComicPageContextMenu(
    anchor: PointF,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
        )

        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthPx = with(density) { 160.dp.toPx() }
        val cardHeightPx = with(density) { 152.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }

        val minLeft = margin
        val maxLeft = (maxWidthPx - cardWidthPx - margin).coerceAtLeast(minLeft)
        val left = (anchor.x - cardWidthPx / 2f).coerceIn(minLeft, maxLeft)

        val minTop = margin
        val maxTop = (maxHeightPx - cardHeightPx - margin).coerceAtLeast(minTop)
        val fitsAbove = anchor.y - margin >= cardHeightPx
        val top = (if (fitsAbove) anchor.y - cardHeightPx - margin else anchor.y + margin)
            .coerceIn(minTop, maxTop)

        Surface(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(160.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Column {
                ContextMenuRow(Icons.Filled.ContentCopy, "Copy", onClick = { onCopy(); onDismiss() })
                ContextMenuRow(Icons.Filled.BookmarkAdd, "Bookmark", onClick = { onBookmark(); onDismiss() })
                ContextMenuRow(Icons.Filled.EditNote, "Note", onClick = { onNote(); onDismiss() })
            }
        }
    }
}

@Composable
private fun ContextMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
