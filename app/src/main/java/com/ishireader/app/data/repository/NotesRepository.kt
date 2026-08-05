package com.ishireader.app.data.repository

import com.ishireader.app.data.model.NoteUpsertRequest
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

    /** Upsert-by-id -- also how an existing note's text gets edited (same route as create, no
     *  separate PUT on the server). */
    suspend fun saveNote(manifestUrl: String, item: StoredNote): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.upsertNote(NoteUpsertRequest(manifestUrl, item))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save note (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteNote(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.deleteNote(manifestUrl, id)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't delete note (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
