package com.ishireader.app.data.repository

import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val KEY_CONTINUE_READING_DISMISSED = "continueReadingDismissed"

/**
 * Only handles the one library-prefs field the Home screen's Continue Reading shelf needs
 * (a book URL -> lastReadAt-at-dismissal-time map, matching useCustomShelves.ts's convention of
 * mirroring client state to this same freeform endpoint). Other fields (theme, accentColor,
 * customShelves, shelfPrefs/shelfOrder) belong to later phases (custom shelves, settings).
 */
class LibraryPrefsRepository(private val network: NetworkModule) {

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
}
