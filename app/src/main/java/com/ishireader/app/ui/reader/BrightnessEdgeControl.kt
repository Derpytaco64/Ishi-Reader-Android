package com.ishireader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** A full-height drag = roughly this much of the -1f..1f range, so the whole span (dim to
 *  brightest) is reachable well within one screen-length swipe, matching Moon+ Reader's feel
 *  rather than requiring an edge-to-edge drag for a small change. */
private const val DRAG_SENSITIVITY = 2f

/**
 * Moon+ Reader-style brightness gesture: dragging vertically anywhere in this strip (meant to be
 * pinned to the far-left edge of the reader, full height, see ReaderActivity) adjusts brightness
 * live via [onPreview]; a plain tap (no meaningful vertical movement) falls through to [onTap]
 * instead of being swallowed -- normally wired to the same "go back a page" action
 * ChromeTapInputListener's own left-edge zone uses, so a tap here doesn't silently lose that
 * behavior just because this control also lives in that zone. [onCommit] fires once, with the
 * final value, when the drag ends.
 *
 * Built as a manual down/move/up loop (same style as SettingsDrawerContent's ColorWheelPicker)
 * rather than [androidx.compose.foundation.gestures.detectVerticalDragGestures] -- Compose claims
 * the whole gesture for this node from the initial hit-test regardless of which detector is used,
 * so distinguishing "tap" from "drag" has to happen here explicitly, not by leaving taps
 * unconsumed and hoping they fall through to the navigator underneath.
 *
 * [value] is only read at the start of a drag (via [rememberUpdatedState], so it's always current
 * without restarting the gesture loop) -- a running drag always continues from its own accumulated
 * value rather than re-seeding from [value] mid-gesture, since [onPreview] itself doesn't feed back
 * into [value] until [onCommit] persists it.
 */
@Composable
fun BrightnessEdgeControl(
    value: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentValue = rememberUpdatedState(value)
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downY = down.position.y
                    var lastY = downY
                    var dragging = false
                    var current = currentValue.value
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            val y = change.position.y
                            if (!dragging) {
                                if (abs(y - downY) > slop) {
                                    dragging = true
                                    isDragging = true
                                    lastY = y
                                }
                            } else {
                                val deltaFraction = (lastY - y) / size.height.toFloat()
                                lastY = y
                                current = (current + deltaFraction * DRAG_SENSITIVITY).coerceIn(-1f, 1f)
                                dragValue = current
                                onPreview(current)
                            }
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    isDragging = false
                    if (dragging) {
                        onCommit(current)
                    } else {
                        onTap()
                    }
                }
            }
    ) {
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = brightnessLabel(dragValue),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** [value] > 0 is a normal screen-brightness fraction; [value] < 0 is past the hardware floor,
 *  into the black-scrim "extra dim" zone -- see ReaderSettings.brightness's own doc comment. */
private fun brightnessLabel(value: Float): String =
    if (value >= 0f) "${(value * 100).roundToInt()}%" else "Dim ${(-value * 100).roundToInt()}%"
