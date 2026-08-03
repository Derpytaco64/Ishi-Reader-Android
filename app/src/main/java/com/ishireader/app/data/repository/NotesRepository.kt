package com.ishireader.app.data.repository

import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotesRepository(private val network: NetworkModule) {

    suspend fun getNotes(manifestUrl: String): ApiResult<List<StoredNote>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getNotes(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.items)
            } else {
                ApiResult.Failure("Couldn't load notes (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
