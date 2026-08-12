package com.ishireader.app.data.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Reads a saved Locator's `locations.totalProgression` into a 0..100 percent, matching
 * StatefulBookSheet.tsx's ReadProgressDial calculation exactly (rounded to one decimal place).
 * Returns null if there's no progress yet -- callers treat that the same as 0%.
 */
fun percentFromLocator(locator: JsonElement?): Double? {
    val totalProgression = progressionFromLocator(locator) ?: return null
    val percent = round(min(1.0, max(0.0, totalProgression)) * 1000) / 10
    return percent.takeIf { it > 0 }
}

/** The raw 0..1 `locations.totalProgression` a saved Locator carries -- [percentFromLocator]'s
 *  own 0..100/one-decimal rounding, but unrounded, for callers (like the "time left in book"
 *  estimate) that need the fraction itself rather than a display string. */
fun progressionFromLocator(locator: JsonElement?): Double? =
    locator?.jsonObject
        ?.get("locations")?.jsonObject
        ?.get("totalProgression")?.jsonPrimitive?.doubleOrNull

/** One-decimal-place percent string for a 0..1 progression fraction, e.g. "75.7%" -- the single
 *  formatter every percent display in the app should go through, so "67%" and "67.3%" never show
 *  up side by side for what's meant to be the same number. Clamps to 0..100 and treats non-finite
 *  input (e.g. a 0/0 division in a reading-history bucket) as 0%. */
fun formatPercent(fraction: Double): String {
    val safeFraction = fraction.takeIf { it.isFinite() } ?: 0.0
    val percent = round(min(1.0, max(0.0, safeFraction)) * 1000) / 10
    return "%.1f%%".format(percent)
}
