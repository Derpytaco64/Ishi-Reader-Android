package com.ishireader.app.data.repository

import com.ishireader.app.data.local.AnnotationCacheDao
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.model.BookmarkUpsertRequest
import com.ishireader.app.data.model.HighlightUpsertRequest
import com.ishireader.app.data.model.StoredBookmark
import com.ishireader.app.data.model.StoredHighlight
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.AnnotationKind
import com.ishireader.app.data.sync.LocalFirstAnnotationStore
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Highlights and bookmarks together -- both are simple locator-tagged markers (no editable body
 *  like a note), always fetched/shown together in the annotations panel, so one repository. Each
 *  is a thin wrapper around [LocalFirstAnnotationStore] -- see its doc comment for why this is now
 *  offline-safe (queued + retried) rather than the old fire-and-forget POST. */
class AnnotationsRepository(
    network: NetworkModule,
    cacheDao: AnnotationCacheDao,
    outboxDao: AnnotationOutboxDao,
    syncScheduler: SyncScheduler
) {
    private val highlights = LocalFirstAnnotationStore(
        kind = AnnotationKind.HIGHLIGHT,
        serializer = StoredHighlight.serializer(),
        idOf = { it.id },
        cacheDao = cacheDao,
        outboxDao = outboxDao,
        syncScheduler = syncScheduler,
        fetchFromServer = { manifestUrl ->
            try {
                val response = network.api.getHighlights(manifestUrl)
                val body = response.body()
                if (response.isSuccessful && body != null) ApiResult.Success(body.items)
                else ApiResult.Failure("Couldn't load highlights (${response.code()})")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        },
        pushUpsert = { manifestUrl, item -> network.api.upsertHighlight(HighlightUpsertRequest(manifestUrl, item)).isSuccessful },
        pushDelete = { manifestUrl, id -> network.api.deleteHighlight(manifestUrl, id).isSuccessful }
    )

    private val bookmarks = LocalFirstAnnotationStore(
        kind = AnnotationKind.BOOKMARK,
        serializer = StoredBookmark.serializer(),
        idOf = { it.id },
        cacheDao = cacheDao,
        outboxDao = outboxDao,
        syncScheduler = syncScheduler,
        fetchFromServer = { manifestUrl ->
            try {
                val response = network.api.getBookmarks(manifestUrl)
                val body = response.body()
                if (response.isSuccessful && body != null) ApiResult.Success(body.items)
                else ApiResult.Failure("Couldn't load bookmarks (${response.code()})")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        },
        pushUpsert = { manifestUrl, item -> network.api.upsertBookmark(BookmarkUpsertRequest(manifestUrl, item)).isSuccessful },
        pushDelete = { manifestUrl, id -> network.api.deleteBookmark(manifestUrl, id).isSuccessful }
    )

    suspend fun getHighlights(manifestUrl: String): ApiResult<List<StoredHighlight>> = withContext(Dispatchers.IO) {
        highlights.getAll(manifestUrl)
    }

    /** Upsert-by-id -- also how a highlight's color gets changed (delete+recreate isn't needed). */
    suspend fun saveHighlight(manifestUrl: String, item: StoredHighlight): ApiResult<Unit> = withContext(Dispatchers.IO) {
        highlights.save(manifestUrl, item)
        ApiResult.Success(Unit)
    }

    suspend fun deleteHighlight(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        highlights.delete(manifestUrl, id)
        ApiResult.Success(Unit)
    }

    suspend fun getBookmarks(manifestUrl: String): ApiResult<List<StoredBookmark>> = withContext(Dispatchers.IO) {
        bookmarks.getAll(manifestUrl)
    }

    suspend fun saveBookmark(manifestUrl: String, item: StoredBookmark): ApiResult<Unit> = withContext(Dispatchers.IO) {
        bookmarks.save(manifestUrl, item)
        ApiResult.Success(Unit)
    }

    suspend fun deleteBookmark(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        bookmarks.delete(manifestUrl, id)
        ApiResult.Success(Unit)
    }
}
