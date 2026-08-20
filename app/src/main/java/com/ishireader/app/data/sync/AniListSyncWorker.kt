package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.CachedAniListEntryDao
import com.ishireader.app.data.local.CachedAniListEntryEntity
import com.ishireader.app.data.local.PendingAniListPatchDao
import com.ishireader.app.data.model.AniListMedia
import com.ishireader.app.data.model.AniListMediaListEntry
import com.ishireader.app.data.model.AniListTitle
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.repository.AniListRepository
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Drains the per-mediaId AniList outbox (see [com.ishireader.app.data.local.PendingAniListPatchEntity])
 * left behind whenever [AniListRepository.patchEntry]'s own inline push attempt couldn't reach the
 * server. Unlike [LibraryPrefsSyncWorker]'s single row, this walks a list -- one row per linked
 * series with an unsynced status/score/progress/repeat/date change -- and pushes each
 * independently, so one row's failure (e.g. AniList itself rejecting a stale mediaId) doesn't block
 * the others from syncing.
 */
class AniListSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val pendingPatchDao: PendingAniListPatchDao,
    private val cachedAniListEntryDao: CachedAniListEntryDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        if (!network.isConfigured) return Result.retry()

        // Shared with AniListRepository.patchEntry's inline push -- same reasoning as
        // LibraryPrefsSyncWorker's own lock: without it, a row captured here could race a newer
        // inline patch that pushes and clears the same row first, and this worker's now-stale patch
        // would overwrite that newer edit back out.
        return AniListRepository.outboxMutex.withLock {
            val pending = pendingPatchDao.getAll()
            if (pending.isEmpty()) return@withLock Result.success()

            var anyFailed = false
            for (row in pending) {
                val patch = runCatching { Json.parseToJsonElement(row.patchJson).jsonObject }.getOrNull()
                if (patch == null) {
                    pendingPatchDao.remove(row.mediaId)
                    continue
                }

                val ok = try {
                    val body = JsonObject(patch + mapOf("mediaId" to JsonPrimitive(row.mediaId)))
                    val response = network.api.saveAniListEntry(body)
                    val entry = response.body()?.entry
                    if (response.isSuccessful && entry != null) {
                        cacheSavedEntry(row.mediaId, entry)
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }

                if (ok) pendingPatchDao.remove(row.mediaId) else anyFailed = true
            }

            if (anyFailed) Result.retry() else Result.success()
        }
    }

    private suspend fun cacheSavedEntry(mediaId: Int, entry: AniListMediaListEntry) {
        val existing = cachedAniListEntryDao.get(mediaId)?.mediaJson
            ?.let { runCatching { json.decodeFromString(AniListMedia.serializer(), it) }.getOrNull() }
        val updated = (existing ?: AniListMedia(id = mediaId, chapters = null, title = AniListTitle())).copy(mediaListEntry = entry)
        cachedAniListEntryDao.set(CachedAniListEntryEntity(mediaId, json.encodeToString(AniListMedia.serializer(), updated), System.currentTimeMillis()))
    }
}
