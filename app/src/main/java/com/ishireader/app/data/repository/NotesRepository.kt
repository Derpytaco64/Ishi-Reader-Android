package com.ishireader.app.data.repository

import com.ishireader.app.data.local.AnnotationCacheDao
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.model.NoteUpsertRequest
import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.AnnotationKind
import com.ishireader.app.data.sync.LocalFirstAnnotationStore
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin wrapper around [LocalFirstAnnotationStore] -- same offline-safe queue+cache treatment as
 *  [AnnotationsRepository]'s highlights/bookmarks, see that store's doc comment. */
class NotesRepository(
    network: NetworkModule,
    cacheDao: AnnotationCacheDao,
    outboxDao: AnnotationOutboxDao,
    syncScheduler: SyncScheduler
) {
    private val notes = LocalFirstAnnotationStore(
        kind = AnnotationKind.NOTE,
        serializer = StoredNote.serializer(),
        idOf = { it.id },
        cacheDao = cacheDao,
        outboxDao = outboxDao,
        syncScheduler = syncScheduler,
        fetchFromServer = { manifestUrl ->
            try {
                val response = network.api.getNotes(manifestUrl)
                val body = response.body()
                if (response.isSuccessful && body != null) ApiResult.Success(body.items)
                else ApiResult.Failure("Couldn't load notes (${response.code()})")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        },
        pushUpsert = { manifestUrl, item -> network.api.upsertNote(NoteUpsertRequest(manifestUrl, item)).isSuccessful },
        pushDelete = { manifestUrl, id -> network.api.deleteNote(manifestUrl, id).isSuccessful }
    )

    suspend fun getNotes(manifestUrl: String): ApiResult<List<StoredNote>> = withContext(Dispatchers.IO) {
        notes.getAll(manifestUrl)
    }

    /** Same upsert route as create -- editing is just re-saving with a fresh updatedAt. */
    suspend fun saveNote(manifestUrl: String, item: StoredNote): ApiResult<Unit> = withContext(Dispatchers.IO) {
        notes.save(manifestUrl, item)
        ApiResult.Success(Unit)
    }

    suspend fun deleteNote(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        notes.delete(manifestUrl, id)
        ApiResult.Success(Unit)
    }
}
