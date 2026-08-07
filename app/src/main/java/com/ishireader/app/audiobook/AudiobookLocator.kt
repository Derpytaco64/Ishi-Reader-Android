package com.ishireader.app.audiobook

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Hand-builds/parses the same Locator JSON shape the website's Readium JS AudioNavigator saves to
 * /api/userdata/position -- there's no Kotlin Locator class to reuse here (audio isn't part of
 * this app's Readium Kotlin toolkit setup, see AudiobookRepository's doc comment), so this mirrors
 * it field-for-field instead: href/type identify the single track, locations.fragments carries
 * the `t=<seconds>` media-fragment convention, and totalProgression is backfilled to equal
 * progression (the website does the same, since AudioNavigator only ever sets progression itself
 * -- see StatefulPlayer's own comment on this).
 */
fun buildPositionLocator(
    trackHref: String,
    trackType: String?,
    chapterTitle: String?,
    positionSeconds: Double,
    durationSeconds: Double
): JsonObject {
    val progression = if (durationSeconds > 0) (positionSeconds / durationSeconds).coerceIn(0.0, 1.0) else 0.0
    return buildJsonObject {
        put("href", trackHref)
        put("type", trackType ?: "audio/mp4")
        if (!chapterTitle.isNullOrBlank()) put("title", chapterTitle)
        putJsonObject("locations") {
            putJsonArray("fragments") { add(JsonPrimitive("t=$positionSeconds")) }
            put("progression", progression)
            put("totalProgression", progression)
        }
    }
}

/** Recovers a resume position (in seconds) from a saved locator -- prefers the exact
 *  `t=<seconds>` fragment over totalProgression*duration, since the fragment survives even if
 *  duration was reported slightly differently across two playback sessions. */
fun resumeSecondsFrom(locatorJson: JsonElement?, durationSeconds: Double): Double? {
    val obj = locatorJson as? JsonObject ?: return null
    val locations = obj["locations"] as? JsonObject ?: return null

    val fragments = locations["fragments"] as? JsonArray
    val fragmentSeconds = fragments?.firstOrNull()
        ?.let { (it as? JsonPrimitive)?.contentOrNull }
        ?.let { frag -> Regex("t=([0-9.]+)").find(frag)?.groupValues?.get(1)?.toDoubleOrNull() }
    if (fragmentSeconds != null) return fragmentSeconds

    val progression = (locations["totalProgression"] as? JsonPrimitive)?.doubleOrNull
        ?: (locations["progression"] as? JsonPrimitive)?.doubleOrNull
    return progression?.takeIf { durationSeconds > 0 }?.let { it * durationSeconds }
}
