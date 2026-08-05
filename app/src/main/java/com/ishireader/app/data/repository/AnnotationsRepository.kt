package com.ishireader.app.data.repository

import com.ishireader.app.data.model.BookmarkUpsertRequest
import com.ishireader.app.data.model.HighlightUpsertRequest
import com.ishireader.app.data.model.StoredBookmark
import com.ishireader.app.data.model.StoredHighlight
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Highlights and bookmarks together -- both are simple locator-tagged markers (no editable body
 *  like a note), always fetched/shown together in the annotations panel, so one repository. */
class AnnotationsRepository(private val network: NetworkModule) {

    suspend fun getHighlights(manifestUrl: String): ApiResult<List<StoredHighlight>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getHighlights(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.items)
            else ApiResult.Failure("Couldn't load highlights (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Upsert-by-id -- also how a highlight's color gets changed (delete+recreate isn't needed). */
    suspend fun saveHighlight(manifestUrl: String, item: StoredHighlight): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.upsertHighlight(HighlightUpsertRequest(manifestUrl, item))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save highlight (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteHighlight(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.deleteHighlight(manifestUrl, id)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't delete highlight (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getBookmarks(manifestUrl: String): ApiResult<List<StoredBookmark>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getBookmarks(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.items)
            else ApiResult.Failure("Couldn't load bookmarks (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun saveBookmark(manifestUrl: String, item: StoredBookmark): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.upsertBookmark(BookmarkUpsertRequest(manifestUrl, item))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save bookmark (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteBookmark(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.deleteBookmark(manifestUrl, id)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't delete bookmark (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
