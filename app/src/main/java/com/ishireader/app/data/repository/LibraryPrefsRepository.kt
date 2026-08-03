package com.ishireader.app.data.repository

import com.ishireader.app.data.model.CustomShelf
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val KEY_CONTINUE_READING_DISMISSED = "continueReadingDismissed"
private const val KEY_CUSTOM_SHELVES = "customShelves"

/**
 * Handles the library-prefs fields the Home screen's Continue Reading shelf and the Shelves tab
 * need (matching useCustomShelves.ts's convention of mirroring client state to this same freeform
 * endpoint). Other fields (theme, accentColor, shelfPrefs/shelfOrder) belong to a later phase
 * (settings).
 */
class LibraryPrefsRepository(private val network: NetworkModule) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getContinueReadingDismissed(): Map<String, Double> = withContext(Dispatchers.IO) {
        try {
            val prefs = network.api.getLibraryPrefs().body()?.libraryPrefs ?: return@withContext emptyMap()
            val dismissed = prefs.jsonObject[KEY_CONTINUE_READING_DISMISSED] ?: return@withContext emptyMap()
            dismissed.jsonObject.mapValues { (_, value) -> value.jsonPrimitive.doubleOrNull ?: 0.0 }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun setContinueReadingDismissed(dismissed: Map<String, Double>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val patch = JsonObject(
                mapOf(KEY_CONTINUE_READING_DISMISSED to JsonObject(dismissed.mapValues { JsonPrimitive(it.value) }))
            )
            val response = network.api.patchLibraryPrefs(patch)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** customShelves is a flat JSON array under library-prefs -- array order is display order,
     *  there's no separate ordering field (mirrors useCustomShelves.ts). */
    suspend fun getCustomShelves(): List<CustomShelf> = withContext(Dispatchers.IO) {
        try {
            val prefs = network.api.getLibraryPrefs().body()?.libraryPrefs ?: return@withContext emptyList()
            val shelves = prefs.jsonObject[KEY_CUSTOM_SHELVES] ?: return@withContext emptyList()
            json.decodeFromJsonElement(ListSerializer(CustomShelf.serializer()), shelves)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Always writes the whole list back -- matches the site's own pattern of mutating a local
     *  copy of the array and PATCHing it in full rather than diffing individual shelves. */
    suspend fun setCustomShelves(shelves: List<CustomShelf>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val element = json.encodeToJsonElement(ListSerializer(CustomShelf.serializer()), shelves)
            val patch = JsonObject(mapOf(KEY_CUSTOM_SHELVES to element))
            val response = network.api.patchLibraryPrefs(patch)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
