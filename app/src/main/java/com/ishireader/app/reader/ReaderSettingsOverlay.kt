package com.ishireader.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.math.roundToInt

private val MinFontScale = 0.5
private val MaxFontScale = 3.0
private val FontScaleStep = 0.1

/** A "Aa" button floating over the page (mirrors the site's reader settings entry point) that
 *  opens a bottom sheet for the two most commonly changed reading preferences. Sits in its own
 *  ComposeView layered on top of the EpubNavigatorFragment in activity_reader.xml -- empty space
 *  here has no pointer input attached, so page turns/taps underneath still work normally. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderSettingsOverlay(
    fontScale: Double,
    theme: Theme,
    onFontScaleChange: (Double) -> Unit,
    onThemeChange: (Theme) -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            onClick = { sheetOpen = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    text = "Text Size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onFontScaleChange((fontScale - FontScaleStep).coerceIn(MinFontScale, MaxFontScale)) },
                        enabled = fontScale > MinFontScale
                    ) { Text("A-") }
                    Text("${(fontScale * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(
                        onClick = { onFontScaleChange((fontScale + FontScaleStep).coerceIn(MinFontScale, MaxFontScale)) },
                        enabled = fontScale < MaxFontScale
                    ) { Text("A+") }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOption("Light", Theme.LIGHT, theme, onThemeChange, Modifier.weight(1f))
                    ThemeOption("Sepia", Theme.SEPIA, theme, onThemeChange, Modifier.weight(1f))
                    ThemeOption("Dark", Theme.DARK, theme, onThemeChange, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeOption(
    label: String,
    value: Theme,
    selected: Theme,
    onSelect: (Theme) -> Unit,
    modifier: Modifier
) {
    if (value == selected) {
        Button(onClick = { onSelect(value) }, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = { onSelect(value) }, modifier = modifier) { Text(label) }
    }
}
