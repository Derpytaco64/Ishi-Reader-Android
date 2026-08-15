package com.ishireader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * While dragging, that's replaced with a HUD card (icon + percent + fill bar) instead of the old
 * bare text label, so the current level reads at a glance rather than requiring parsing "Dim 20%"
 * vs "65%" as two different scales.
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
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = brightnessIcon(dragValue),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = brightnessPercentLabel(dragValue),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = brightnessModeLabel(dragValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Small fill track spanning the full -1f..1f range (bottom = darkest extra-dim,
                // top = brightest) -- a quick-glance analog to the number above it.
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(4.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fillFraction = ((dragValue + 1f) / 2f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fillFraction)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

private fun brightnessIcon(value: Float): ImageVector =
    if (value >= 0f) Icons.Filled.WbSunny else Icons.Filled.Brightness3

/** [value] > 0 is a normal screen-brightness fraction, < 0 is past the hardware floor, into the
 *  black-scrim "extra dim" zone -- see ReaderActivity.applyBrightness's own doc comment. Split
 *  into a separate icon/mode label rather than folding "Dim" into the number itself, so the two
 *  zones read as a continuous scale instead of two differently-formatted numbers. */
private fun brightnessPercentLabel(value: Float): String = "${(abs(value) * 100).roundToInt()}%"

private fun brightnessModeLabel(value: Float): String = if (value >= 0f) "Brightness" else "Extra Dim"
