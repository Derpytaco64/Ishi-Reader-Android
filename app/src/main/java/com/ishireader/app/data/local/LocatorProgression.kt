package com.ishireader.app.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pulls locations.totalProgression out of a Locator's raw JSON (Readium's own toJSON() shape) --
 *  used to compare local vs. server reading progress during sync without needing a fully typed
 *  Locator on both sides (the sync worker only ever sees the server's raw JsonElement). */
fun totalProgressionOf(locator: JsonElement?): Double? =
    locator?.jsonObject?.get("locations")?.jsonObject?.get("totalProgression")?.jsonPrimitive?.doubleOrNull

fun totalProgressionOf(locatorJson: String): Double? =
    runCatching { totalProgressionOf(Json.parseToJsonElement(locatorJson)) }.getOrNull()
