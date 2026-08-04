package com.ishireader.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.AppSettings
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.HomeShelfId
import com.ishireader.app.data.model.ThemeMode
import com.ishireader.app.ui.theme.LocalDefaultAccentColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// CLAUDE-ADDED: The 12-stop hue ring the wheel's Canvas paints as a sweepGradient -- 13 stops so
// the gradient closes exactly back to red (0/360deg) with no seam.
private val HueRingColors = (0..12).map { Color.hsv(it * 30f, 1f, 1f) }

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

@Composable
fun SettingsDrawerContent(
    settings: AppSettings,
    onThemeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (String?) -> Unit,
    onCoverSizeChange: (CoverSize) -> Unit,
    onShelfVisibleChange: (HomeShelfId, Boolean) -> Unit,
    onMoveShelf: (HomeShelfId, Int) -> Unit
) {
    ModalDrawerSheet {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        SectionLabel("Theme")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.theme == mode,
                    onClick = { onThemeChange(mode) },
                    label = { Text(mode.key.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SectionLabel("Accent color")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccentSwatch(
                color = LocalDefaultAccentColor.current,
                selected = settings.accentColor == null,
                onClick = { onAccentColorChange(null) }
            )
            Text(text = "Default", style = MaterialTheme.typography.bodyMedium)
        }
        ColorWheelPicker(
            color = parseAccentColor(settings.accentColor) ?: LocalDefaultAccentColor.current,
            onColorChange = { onAccentColorChange(it.toHex()) },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SectionLabel("Cover size")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoverSize.entries.forEach { size ->
                FilterChip(
                    selected = settings.coverSize == size,
                    onClick = { onCoverSizeChange(size) },
                    label = { Text(size.key.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SectionLabel("Home shelves")
        settings.shelfOrder.forEachIndexed { index, id ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = settings.isShelfVisible(id),
                    onCheckedChange = { onShelfVisibleChange(id, it) }
                )
                Text(text = id.displayName, modifier = Modifier.padding(start = 4.dp).weight(1f))
                IconButton(onClick = { onMoveShelf(id, -1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMoveShelf(id, 1) }, enabled = index < settings.shelfOrder.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
        }
    }
}

/** Hue/saturation wheel (angle = hue, distance from center = saturation) plus a brightness slider
 *  underneath -- picked in place of fixed presets so any accent is reachable, not just eight swatches.
 *  Hue/sat/value are seeded once from [color] rather than re-synced on every recomposition: since
 *  every drag round-trips through [onColorChange] back into [color], keying off it directly would
 *  either snap the marker back on each event or (for a fully desaturated color, whose hue is
 *  undefined) fight the user's own hue choice mid-drag. */
@Composable
fun ColorWheelPicker(color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val initialHsv = remember { colorToHsv(color) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    fun updateFromOffset(offset: Offset, radius: Float, center: Offset) {
        val delta = offset - center
        val distance = min(delta.getDistance(), radius)
        val angleDegrees = (Math.toDegrees(atan2(delta.y, delta.x).toDouble()) + 360.0) % 360.0
        hue = angleDegrees.toFloat()
        saturation = (distance / radius).coerceIn(0f, 1f)
        onColorChange(hsvToColor(hue, saturation, value))
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        updateFromOffset(down.position, radius, center)
                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull { it.id == down.id }
                            if (drag != null && drag.positionChanged()) {
                                updateFromOffset(drag.position, radius, center)
                                drag.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(brush = Brush.sweepGradient(HueRingColors, center), radius = radius, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            val markerAngle = Math.toRadians(hue.toDouble())
            val markerDistance = saturation * radius
            val marker = Offset(
                center.x + (markerDistance * cos(markerAngle)).toFloat(),
                center.y + (markerDistance * sin(markerAngle)).toFloat()
            )
            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = marker, style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = marker, style = Stroke(width = 1.dp.toPx()))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Brightness", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    onColorChange(hsvToColor(hue, saturation, value))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
