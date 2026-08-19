package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.local.AnnotationOutboxEntity
import com.ishireader.app.data.model.BookmarkUpsertRequest
import com.ishireader.app.data.model.CompletedListenUpsertRequest
import com.ishireader.app.data.model.HighlightUpsertRequest
import com.ishireader.app.data.model.NoteUpsertRequest
import com.ishireader.app.data.model.StoredBookmark
import com.ishireader.app.data.model.StoredCompletedListen
import com.ishireader.app.data.model.StoredHighlight
import com.ishireader.app.data.model.StoredNote
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.json.Json

/**
 * Drains the shared highlight/bookmark/note outbox (see [AnnotationOutboxEntity]) left behind
 * whenever [LocalFirstAnnotationStore]'s own inline push attempt couldn't reach the server --
 * replays each row's upsert/delete directly against the API (routed by [AnnotationOutboxEntity.
 * kind]) and clears it on success, mirroring [PositionSyncWorker]/[LibraryPrefsSyncWorker]. The
 * local cache each store reads from was already updated optimistically at write time, so this
 * worker only needs to settle the outbox, not touch the cache.
 */
class AnnotationsSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val outboxDao: AnnotationOutboxDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!network.isConfigured) return Result.retry()

        val pending = outboxDao.getAll()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (row in pending) {
            val ok = try {
                when (row.kind) {
                    AnnotationKind.HIGHLIGHT -> syncHighlight(row)
                    AnnotationKind.BOOKMARK -> syncBookmark(row)
                    AnnotationKind.NOTE -> syncNote(row)
                    AnnotationKind.COMPLETED_LISTEN -> syncCompletedListen(row)
                    else -> true
                }
            } catch (e: Exception) {
                false
            }
            if (ok) outboxDao.remove(row.kind, row.manifestUrl, row.itemId) else anyFailed = true
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun syncHighlight(row: AnnotationOutboxEntity): Boolean =
        if (row.opType == "UPSERT") {
            val item = row.itemJson?.let { Json.decodeFromString(StoredHighlight.serializer(), it) } ?: return true
            network.api.upsertHighlight(HighlightUpsertRequest(row.manifestUrl, item)).isSuccessful
        } else {
            network.api.deleteHighlight(row.manifestUrl, row.itemId).isSuccessful
        }

    private suspend fun syncBookmark(row: AnnotationOutboxEntity): Boolean =
        if (row.opType == "UPSERT") {
            val item = row.itemJson?.let { Json.decodeFromString(StoredBookmark.serializer(), it) } ?: return true
            network.api.upsertBookmark(BookmarkUpsertRequest(row.manifestUrl, item)).isSuccessful
        } else {
            network.api.deleteBookmark(row.manifestUrl, row.itemId).isSuccessful
        }

    private suspend fun syncNote(row: AnnotationOutboxEntity): Boolean =
        if (row.opType == "UPSERT") {
            val item = row.itemJson?.let { Json.decodeFromString(StoredNote.serializer(), it) } ?: return true
            network.api.upsertNote(NoteUpsertRequest(row.manifestUrl, item)).isSuccessful
        } else {
            network.api.deleteNote(row.manifestUrl, row.itemId).isSuccessful
        }

    private suspend fun syncCompletedListen(row: AnnotationOutboxEntity): Boolean =
        if (row.opType == "UPSERT") {
            val item = row.itemJson?.let { Json.decodeFromString(StoredCompletedListen.serializer(), it) } ?: return true
            network.api.upsertCompletedListen(CompletedListenUpsertRequest(row.manifestUrl, item)).isSuccessful
        } else {
            network.api.deleteCompletedListen(row.manifestUrl, row.itemId).isSuccessful
        }
}
