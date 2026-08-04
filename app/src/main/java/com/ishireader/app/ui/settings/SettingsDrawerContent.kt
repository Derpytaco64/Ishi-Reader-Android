package com.ishireader.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ishireader.app.data.model.AppSettings
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.HomeShelfId
import com.ishireader.app.data.model.ThemeMode
import com.ishireader.app.ui.theme.LocalDefaultAccentColor

private val AccentPresets = listOf(
    "#1976D2", "#7B1FA2", "#388E3C", "#F57C00", "#D32F2F", "#00796B", "#C2185B", "#F9A825"
)

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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccentSwatch(
                color = LocalDefaultAccentColor.current,
                selected = settings.accentColor == null,
                onClick = { onAccentColorChange(null) }
            )
            AccentPresets.forEach { hex ->
                AccentSwatch(
                    color = parseAccentColor(hex) ?: Color.Gray,
                    selected = settings.accentColor == hex,
                    onClick = { onAccentColorChange(hex) }
                )
            }
        }

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
