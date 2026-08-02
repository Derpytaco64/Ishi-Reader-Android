package com.ishireader.app.data.repository

import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

class PositionRepository(private val network: NetworkModule) {

    /** Returns the last saved Locator JSON for this book, or null if it's never been opened. */
    suspend fun getPosition(manifestUrl: String): ApiResult<JsonElement?> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getPosition(manifestUrl)
            if (response.isSuccessful) {
                ApiResult.Success(response.body()?.locator)
            } else {
                ApiResult.Failure("Couldn't load reading position (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Saves a Locator (as produced by the Readium navigator's `Locator.toJSON()`). */
    suspend fun setPosition(manifestUrl: String, locator: JsonElement): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.setPosition(PositionRequest(manifestUrl, locator))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure("Couldn't save reading position (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
