package com.ishireader.app.ui.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ishireader.app.data.model.AppSettings

/** Read-only access to the current [AppSettings] for screens that only need to react to it (cover
 *  size in the grids, Home's shelf order/visibility) without threading a ViewModel reference down
 *  through every composable in between. Mutations still go through [SettingsViewModel], which is
 *  the only thing that provides this local (see MainActivity). */
val LocalAppSettings = compositionLocalOf { AppSettings() }

/** accentColor is stored as "#RRGGBB"; malformed/absent values fall back to null (the theme's
 *  own default primary color) rather than crashing the composition. */
fun parseAccentColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and this.toArgb())
