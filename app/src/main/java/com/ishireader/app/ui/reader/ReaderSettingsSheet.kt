package com.ishireader.app.ui.reader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.PositionDisplayAlignment
import com.ishireader.app.data.model.PositionDisplayMode
import com.ishireader.app.data.model.ReaderFontFamily
import com.ishireader.app.data.model.ReaderLayout
import com.ishireader.app.data.model.ReaderLineHeight
import com.ishireader.app.data.model.ReaderSettings
import com.ishireader.app.data.model.ReaderTextAlign
import com.ishireader.app.data.model.ReaderTheme
import kotlin.math.roundToInt

/**
 * In-book reading preferences -- font/theme/layout/spacing -- see ReaderSettings for the exact
 * (curated) scope. Every control writes straight back through [onSettingsChange]; the caller
 * (ReaderActivity) is responsible for applying the result to the live navigator and persisting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Reader Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider()

            SectionLabel("Appearance")
            SegmentedOptionRow(
                label = "Theme",
                options = listOf(null) + ReaderTheme.entries,
                selected = settings.theme,
                optionLabel = { it.label() },
                onSelect = { onSettingsChange(settings.copy(theme = it)) }
            )
            SegmentedOptionRow(
                label = "Font",
                options = listOf(null) + ReaderFontFamily.entries,
                selected = settings.fontFamily,
                optionLabel = { it.label() },
                onSelect = { onSettingsChange(settings.copy(fontFamily = it)) }
            )
            LabeledSlider(
                label = "Font Size",
                value = settings.fontSize.toFloat(),
                valueRange = 0.7f..4.0f,
                steps = 32,
                valueLabel = "${(settings.fontSize * 100).roundToInt()}%",
                onValueChange = { onSettingsChange(settings.copy(fontSize = it.toDouble())) }
            )

            SectionLabel("Layout")
            SegmentedOptionRow(
                label = "Layout",
                options = ReaderLayout.entries,
                selected = settings.layout,
                optionLabel = { it.label() },
                onSelect = { onSettingsChange(settings.copy(layout = it)) }
            )
            SegmentedOptionRow(
                label = "Text Align",
                options = listOf(null) + ReaderTextAlign.entries,
                selected = settings.textAlign,
                optionLabel = { it.label() },
                onSelect = { onSettingsChange(settings.copy(textAlign = it, publisherStyles = false)) }
            )
            SegmentedOptionRow(
                label = "Line Height",
                options = listOf(null) + ReaderLineHeight.entries,
                selected = settings.lineHeight,
                optionLabel = { it.label() },
                onSelect = { onSettingsChange(settings.copy(lineHeight = it, publisherStyles = false)) }
            )
            LabeledSlider(
                label = "Horizontal Margin",
                value = (settings.pageMargins ?: 1.0).toFloat(),
                valueRange = 0.5f..4.0f,
                steps = 34,
                valueLabel = settings.pageMargins?.let { "%.1fx".format(it) } ?: "Default",
                onValueChange = { onSettingsChange(settings.copy(pageMargins = it.toDouble())) }
            )
            LabeledSlider(
                label = "Vertical Margin",
                value = settings.verticalMargin.toFloat(),
                valueRange = 0f..64f,
                steps = 15,
                valueLabel = if (settings.verticalMargin > 0) "${settings.verticalMargin.roundToInt()}dp" else "None",
                onValueChange = { onSettingsChange(settings.copy(verticalMargin = it.toDouble())) }
            )

            SectionLabel("Display")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Show chapter title", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.showChapterTitle,
                    onCheckedChange = { onSettingsChange(settings.copy(showChapterTitle = it)) }
                )
            }
            SegmentedOptionRow(
                label = "Position Indicator",
                options = PositionDisplayMode.entries,
                selected = settings.positionDisplayMode,
                optionLabel = { it.label() },
                onSelect = { onSettingsChange(settings.copy(positionDisplayMode = it)) }
            )
            if (settings.positionDisplayMode != PositionDisplayMode.NONE) {
                SegmentedOptionRow(
                    label = "Position Alignment",
                    options = PositionDisplayAlignment.entries,
                    selected = settings.positionDisplayAlignment,
                    optionLabel = { it.label() },
                    onSelect = { onSettingsChange(settings.copy(positionDisplayAlignment = it)) }
                )
            }

            SectionLabel("Controls")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Volume buttons turn pages", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.volumeButtonsPageTurn,
                    onCheckedChange = { onSettingsChange(settings.copy(volumeButtonsPageTurn = it)) }
                )
            }

            SectionLabel("Dictionary")
            DictionaryAppPicker(
                selectedComponent = settings.dictionaryAppComponent,
                onSelect = { onSettingsChange(settings.copy(dictionaryAppComponent = it)) }
            )

            SectionLabel("Spacing")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Use publisher styles", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.publisherStyles,
                    onCheckedChange = { checked ->
                        onSettingsChange(
                            if (checked) {
                                // Mirrors the website: switching this on clears every spacing/
                                // align/hyphen override back to "let the publisher decide" rather
                                // than just toggling a flag while stale values linger underneath.
                                settings.copy(
                                    publisherStyles = true,
                                    textAlign = null,
                                    lineHeight = null,
                                    paragraphSpacing = null,
                                    paragraphIndent = null,
                                    wordSpacing = null,
                                    letterSpacing = null,
                                    hyphens = null
                                )
                            } else {
                                settings.copy(publisherStyles = false)
                            }
                        )
                    }
                )
            }
            val spacingEnabled = !settings.publisherStyles
            LabeledSlider(
                label = "Paragraph Spacing",
                value = (settings.paragraphSpacing ?: 0.0).toFloat(),
                valueRange = 0f..3f,
                steps = 11,
                valueLabel = formatSpacing(settings.paragraphSpacing),
                enabled = spacingEnabled,
                onValueChange = { onSettingsChange(settings.copy(paragraphSpacing = it.toDouble(), publisherStyles = false)) }
            )
            LabeledSlider(
                label = "Paragraph Indent",
                value = (settings.paragraphIndent ?: 0.0).toFloat(),
                valueRange = 0f..2f,
                steps = 7,
                valueLabel = formatSpacing(settings.paragraphIndent),
                enabled = spacingEnabled,
                onValueChange = { onSettingsChange(settings.copy(paragraphIndent = it.toDouble(), publisherStyles = false)) }
            )
            LabeledSlider(
                label = "Word Spacing",
                value = (settings.wordSpacing ?: 0.0).toFloat(),
                valueRange = 0f..1f,
                steps = 9,
                valueLabel = formatSpacing(settings.wordSpacing),
                enabled = spacingEnabled,
                onValueChange = { onSettingsChange(settings.copy(wordSpacing = it.toDouble(), publisherStyles = false)) }
            )
            LabeledSlider(
                label = "Letter Spacing",
                value = (settings.letterSpacing ?: 0.0).toFloat(),
                valueRange = 0f..0.5f,
                steps = 9,
                valueLabel = formatSpacing(settings.letterSpacing),
                enabled = spacingEnabled,
                onValueChange = { onSettingsChange(settings.copy(letterSpacing = it.toDouble(), publisherStyles = false)) }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Hyphenate text", modifier = Modifier.weight(1f))
                Switch(
                    // Simplified from the site's tri-state (on/off/publisher) to a plain toggle --
                    // unchecked maps to null (publisher/reader default) rather than an explicit
                    // "off", which covers the common cases without a third state to explain in a
                    // small mobile control.
                    checked = settings.hyphens == true,
                    onCheckedChange = { onSettingsChange(settings.copy(hyphens = if (it) true else null)) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

/** A horizontally-scrollable row of filter chips, one per option -- used instead of a fixed-width
 *  segmented button row since some of these (font family, theme) have enough options that a
 *  segmented row would clip on a narrow phone. */
@Composable
private fun <T> SegmentedOptionRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) }
                )
            }
        }
    }
}

/** Lets the user pick which installed app receives selected text for lookup -- see
 *  ReaderActivity.launchDictionaryLookup, which fires Android's standard ACTION_PROCESS_TEXT
 *  intent at whatever's picked here. Options are queried live from PackageManager rather than
 *  hardcoded, since which dictionary/translator apps are installed varies per device -- any app
 *  that already offers itself from other apps' text-selection toolbars ("Process text") shows up
 *  here automatically, no per-app integration needed. */
@Composable
private fun DictionaryAppPicker(selectedComponent: String?, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    val options = remember(context) { dictionaryAppOptions(context) }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        selectedComponent == null -> "None"
        else -> options.firstOrNull { it.componentName == selectedComponent }?.label ?: "Unknown app"
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Lookup App", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSelect(option.componentName); expanded = false }
                    )
                }
            }
        }
        if (options.isEmpty()) {
            Text(
                "No apps found that support text lookup. Install a dictionary or translator app " +
                    "that offers itself from other apps' text-selection menus to enable this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DictionaryAppOption(val componentName: String, val label: String)

/** [PackageManager.MATCH_DEFAULT_ONLY] mirrors how the system itself resolves ACTION_PROCESS_TEXT
 *  for selection-toolbar "Process text" entries, so this list matches what a user would see there. */
private fun dictionaryAppOptions(context: Context): List<DictionaryAppOption> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply { type = "text/plain" }
    return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
            DictionaryAppOption(
                componentName = componentName.flattenToString(),
                label = resolveInfo.loadLabel(packageManager).toString()
            )
        }
        .sortedBy { it.label }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled)
    }
}

private fun ReaderTheme?.label(): String = when (this) {
    null -> "Auto"
    ReaderTheme.LIGHT -> "Light"
    ReaderTheme.DARK -> "Dark"
    ReaderTheme.SEPIA -> "Sepia"
    ReaderTheme.PAPER -> "Paper"
    ReaderTheme.CONTRAST1 -> "Contrast 1"
    ReaderTheme.CONTRAST2 -> "Contrast 2"
    ReaderTheme.CONTRAST3 -> "Contrast 3"
}

private fun ReaderFontFamily?.label(): String = when (this) {
    null -> "Publisher"
    ReaderFontFamily.SERIF -> "Serif"
    ReaderFontFamily.SANS_SERIF -> "Sans Serif"
    ReaderFontFamily.MONOSPACE -> "Monospace"
    ReaderFontFamily.OPEN_DYSLEXIC -> "OpenDyslexic"
}

private fun ReaderTextAlign?.label(): String = when (this) {
    null -> "Publisher"
    ReaderTextAlign.START -> "Start"
    ReaderTextAlign.JUSTIFY -> "Justify"
}

private fun ReaderLineHeight?.label(): String = when (this) {
    null -> "Publisher"
    ReaderLineHeight.COMPACT -> "Compact"
    ReaderLineHeight.NORMAL -> "Normal"
    ReaderLineHeight.RELAXED -> "Relaxed"
}

private fun ReaderLayout.label(): String = when (this) {
    ReaderLayout.PAGINATED -> "Paginated"
    ReaderLayout.SCROLLED -> "Scrolled"
}

private fun PositionDisplayMode.label(): String = when (this) {
    PositionDisplayMode.NONE -> "Off"
    PositionDisplayMode.PAGE -> "Page"
    PositionDisplayMode.PERCENT -> "Percent"
    PositionDisplayMode.PAGE_PERCENT -> "Page + Percent"
}

private fun PositionDisplayAlignment.label(): String = when (this) {
    PositionDisplayAlignment.LEFT -> "Left"
    PositionDisplayAlignment.CENTER -> "Center"
    PositionDisplayAlignment.RIGHT -> "Right"
}

private fun formatSpacing(value: Double?): String = value?.let { "%.2f".format(it) } ?: "Publisher"
