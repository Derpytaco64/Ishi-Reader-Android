package com.ishireader.app.ui.reader

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ishireader.app.reader.HighlightColor
import kotlin.math.roundToInt

/**
 * Android port of the website's SelectionPopover color-swatch row (see
 * highlightColors.ts/SelectionPopover.tsx) -- a small floating card of the 5 fixed highlight
 * colors, anchored to [anchor] (the selection's or tapped decoration's on-screen rect, already
 * converted to this ComposeView's local coordinates by the caller) instead of a centered dialog.
 * Used for both picking a color for a brand-new highlight and re-coloring/deleting an existing one
 * (`onDelete` non-null only in the latter case, mirroring SelectionPopover's `existing` branch).
 *
 * [selectedColor] draws a ring around the current color -- the website relies on CSS :hover/
 * :focus-visible for that affordance, which doesn't exist on touch, so a persistent selected-state
 * ring replaces it here.
 */
@Composable
fun HighlightColorPopover(
    anchor: RectF?,
    selectedColor: HighlightColor?,
    onColorSelected: (HighlightColor) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Transparent scrim so a tap anywhere else dismisses the popover, matching the website's
        // document-level pointerdown listener -- doesn't dim the page, this isn't a modal.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
        )

        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val swatchCount = HighlightColor.entries.size + if (onDelete != null) 1 else 0
        val cardWidthPx = with(density) { (swatchCount * 38 + 20).dp.toPx() }
        val cardHeightPx = with(density) { 52.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }

        val minLeft = margin
        val maxLeft = (maxWidthPx - cardWidthPx - margin).coerceAtLeast(minLeft)
        val left = (anchor?.let { (it.left + it.right) / 2f - cardWidthPx / 2f } ?: (maxWidthPx / 2f - cardWidthPx / 2f))
            .coerceIn(minLeft, maxLeft)

        val minTop = margin
        val maxTop = (maxHeightPx - cardHeightPx - margin).coerceAtLeast(minTop)
        val top = (anchor?.let { rect ->
            val fitsAbove = rect.top - margin >= cardHeightPx
            if (fitsAbove) rect.top - cardHeightPx - margin else rect.bottom + margin
        } ?: (maxHeightPx / 2f - cardHeightPx / 2f)).coerceIn(minTop, maxTop)

        Surface(
            modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(color.hex)))
                            .then(
                                if (color == selectedColor) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onColorSelected(color) }
                    )
                }

                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete highlight",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
