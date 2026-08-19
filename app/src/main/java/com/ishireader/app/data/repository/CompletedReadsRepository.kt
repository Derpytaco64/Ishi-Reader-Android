package com.ishireader.app.data.repository

import com.ishireader.app.data.local.AnnotationCacheDao
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.model.CompletedReadTimeUpsertRequest
import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.AnnotationKind
import com.ishireader.app.data.sync.LocalFirstAnnotationStore
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local-first via [LocalFirstAnnotationStore] (see [AnnotationKind.COMPLETED_READ]) -- this was
 * the one piece of reading-timer data left network-first with no offline cache after
 * ReadingTimerRepository's own fix, which left the "reading sessions" list on BookDetailScreen
 * going blank offline even once time/pace/daily-history were showing correctly again. A completed
 * read only ever needs upsert/delete-by-id, the same shape as highlights/bookmarks/notes/completed
 * listens, so it reuses that store instead of a bespoke cache+outbox like ReadingTimerRepository's
 * merge-needing fields.
 */
class CompletedReadsRepository(
    network: NetworkModule,
    cacheDao: AnnotationCacheDao,
    outboxDao: AnnotationOutboxDao,
    syncScheduler: SyncScheduler
) {
    private val store = LocalFirstAnnotationStore(
        kind = AnnotationKind.COMPLETED_READ,
        serializer = StoredCompletedReadTime.serializer(),
        idOf = { it.id },
        cacheDao = cacheDao,
        outboxDao = outboxDao,
        syncScheduler = syncScheduler,
        fetchFromServer = { manifestUrl ->
            try {
                val response = network.api.getCompletedReadTimes(manifestUrl)
                val body = response.body()
                if (response.isSuccessful && body != null) ApiResult.Success(body.items)
                else ApiResult.Failure("Couldn't load completed reads (${response.code()})")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        },
        pushUpsert = { manifestUrl, item -> network.api.upsertCompletedReadTime(CompletedReadTimeUpsertRequest(manifestUrl, item)).isSuccessful },
        pushDelete = { manifestUrl, id -> network.api.deleteCompletedReadTime(manifestUrl, id).isSuccessful }
    )

    suspend fun getCompletedReadTimes(manifestUrl: String): ApiResult<List<StoredCompletedReadTime>> =
        withContext(Dispatchers.IO) { store.getAll(manifestUrl) }

    /** Archives a finished reading run -- the server upserts by id, so this also covers editing
     *  one if a caller ever needs to (not currently exposed in the UI, mirrors the website). */
    suspend fun saveCompletedReadTime(manifestUrl: String, item: StoredCompletedReadTime): ApiResult<Unit> = withContext(Dispatchers.IO) {
        store.save(manifestUrl, item)
        ApiResult.Success(Unit)
    }

    suspend fun deleteCompletedReadTime(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        store.delete(manifestUrl, id)
        ApiResult.Success(Unit)
    }
}
