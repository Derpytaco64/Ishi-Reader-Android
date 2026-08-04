package com.ishireader.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun IshiReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    // A user-picked accent just retints primary/secondary on top of the base scheme rather than
    // deriving a full tonal palette from the seed color (no seed-generation API ships in plain
    // material3 without pulling in the Material color-utilities library for what's otherwise a
    // simple preset-swatch picker).
    val colorScheme = if (accentColor != null) {
        baseColorScheme.copy(primary = accentColor, secondary = accentColor)
    } else {
        baseColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
