package com.ishireader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness3
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** A full-height drag = roughly this much of the -1f..1f range, so the whole span (dim to
 *  brightest) is reachable well within one screen-length swipe, matching Moon+ Reader's feel
 *  rather than requiring an edge-to-edge drag for a small change. Lowered from 2f to 1.5f (25%
 *  less sensitive) at user request -- the original felt twitchy for small adjustments. */
private const val DRAG_SENSITIVITY = 1.5f

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
 *
 * A faint always-on track marks the strip when idle -- there was previously no visual affordance
 * at all for this zone, so users had no way to discover it short of accidentally dragging there.
 * While dragging, that's replaced with a tall fill bar spanning the control's own full height
 * (rather than a small fixed-size indicator) with a notch marking the 0% boundary: positive
 * (brighter) values fill upward from the notch, negative (extra-dim) values fill downward from
 * it -- a single needle pivoting at "system brightness" rather than one long fill that happens to
 * pass through an arbitrary midpoint. The percent readout sits directly underneath, in an
 * unbounded-width row so it can never wrap inside the narrow 40dp strip.
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
        if (!isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            )
        }

        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            // wrapContentWidth(unbounded = true) lets the percent pill below the bar render at
            // its natural width instead of being squeezed (and wrapped) into this Box's own 40dp
            // -- Box would otherwise measure this content with that same 40dp max-width cap.
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .wrapContentWidth(unbounded = true)
                .padding(start = 6.dp, top = 24.dp, bottom = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The fill bar: takes the control's full (current) height minus what the percent
                // pill below needs, rather than a small fixed-size indicator.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
                        // Upper half: 0f..1f normal brightness -- fills upward, flush against the
                        // notch below it.
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val upFraction = dragValue.coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(upFraction)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        // Notch marking the 0% boundary between the two zones.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        )
                        // Lower half: -1f..0f extra-dim -- fills downward from the notch, the
                        // opposite direction from the upper half.
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val downFraction = (-dragValue).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(downFraction)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                // Percent readout, pinned directly underneath the bar -- single line, never wraps
                // (see the unbounded-width modifier on the AnimatedVisibility above).
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = brightnessIcon(dragValue),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = brightnessPercentLabel(dragValue),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

private fun brightnessIcon(value: Float): ImageVector =
    if (value >= 0f) Icons.Filled.WbSunny else Icons.Filled.Brightness3

/** [value] > 0 is a normal screen-brightness fraction, < 0 is past the hardware floor, into the
 *  black-scrim "extra dim" zone -- see ReaderActivity.applyBrightness's own doc comment. The icon
 *  above this label (see [brightnessIcon]) is what distinguishes the two zones now; the number
 *  itself is always an unsigned magnitude. */
private fun brightnessPercentLabel(value: Float): String = "${(abs(value) * 100).roundToInt()}%"
