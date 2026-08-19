package com.ishireader.app.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.AppSettings
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.HomeShelfId
import com.ishireader.app.data.model.ThemeMode
import com.ishireader.app.data.repository.BookDownloadRepository
import com.ishireader.app.ui.theme.LocalDefaultAccentColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onMoveShelf: (HomeShelfId, Int) -> Unit,
    showDownloadedOnly: Boolean,
    onShowDownloadedOnlyChange: (Boolean) -> Unit,
    bookDownloadRepository: BookDownloadRepository,
    downloadsVersion: Int,
    onDeleteAllDownloads: () -> Unit
) {
    val context = LocalContext.current
    // CLAUDE-ADDED: Read off PackageManager instead of hardcoding the string here (or enabling the
    // BuildConfig feature just for this one field, which IshiReaderApp already avoids elsewhere --
    // see its own onCreate comment) so this label can't drift out of sync with build.gradle.kts'
    // versionName the way the hardcoded "V1.2.3"/"V1.2.4" string previously did.
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
    ModalDrawerSheet {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "V$versionName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Derpytaco64/Ishi-Reader-Android")
                    )
                    context.startActivity(intent)
                }
            )
        }

        // CLAUDE-ADDED: Local-only (per-device) filter, deliberately first in the drawer -- unlike
        // every other setting here, it isn't synced to the server (the website has no concept of
        // "downloaded" since it's always live). See AppPreferences.showDownloadedOnly.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Only show downloaded books",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Switch(checked = showDownloadedOnly, onCheckedChange = onShowDownloadedOnlyChange)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        DownloadedFilesSection(bookDownloadRepository, downloadsVersion, onDeleteAllDownloads)
    }
}

/** Local-storage info + a destructive bulk-delete, both device-specific like the toggle above --
 *  the website has no equivalent since it never stores book files locally. File count/size are
 *  recomputed with a single directory listing on every [downloadsVersion] bump (same invalidation
 *  signal BookCoverCard's dimming uses) rather than polled, since that's the only thing that can
 *  change them. */
@Composable
private fun DownloadedFilesSection(
    bookDownloadRepository: BookDownloadRepository,
    downloadsVersion: Int,
    onDeleteAllDownloads: () -> Unit
) {
    var fileCount by remember { mutableStateOf(0) }
    var totalBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(downloadsVersion) {
        val files = withContext(Dispatchers.IO) {
            bookDownloadRepository.booksDirectory
                .listFiles { file -> !file.name.endsWith(".part") }
                .orEmpty()
        }
        fileCount = files.size
        totalBytes = files.sumOf { it.length() }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    SectionLabel("Downloaded files")
    Text(
        text = bookDownloadRepository.booksDirectory.absolutePath,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Text(
        text = "$fileCount file${if (fileCount == 1) "" else "s"} (${formatFileSize(totalBytes)})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
    )
    TextButton(
        onClick = { showDeleteConfirm = true },
        enabled = fileCount > 0,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Text("Delete All Downloaded Files")
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all downloaded files?") },
            text = { Text("Every locally downloaded book file on this device will be removed. Your library is unaffected -- books can be re-downloaded any time.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAllDownloads()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
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
fun ColorWheelPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 200.dp,
    onDraggingChange: (Boolean) -> Unit = {}
) {
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
                .size(wheelSize)
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onDraggingChange(true)
                        updateFromOffset(down.position, radius, center)
                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull { it.id == down.id }
                            if (drag != null && drag.positionChanged()) {
                                updateFromOffset(drag.position, radius, center)
                                drag.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        onDraggingChange(false)
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
                    onDraggingChange(true)
                },
                onValueChangeFinished = { onDraggingChange(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
