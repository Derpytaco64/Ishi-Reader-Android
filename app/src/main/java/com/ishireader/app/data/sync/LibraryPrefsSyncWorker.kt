package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.CachedLibraryPrefsDao
import com.ishireader.app.data.local.CachedLibraryPrefsEntity
import com.ishireader.app.data.local.PendingLibraryPrefsPatchDao
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.repository.LibraryPrefsRepository
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Drains the library-prefs outbox left behind by [com.ishireader.app.data.repository.LibraryPrefsRepository.patchPrefs]
 * when its own inline push attempt failed -- pushes the whole accumulated pending patch (accent
 * color, home shelf order/visibility, custom shelves, continue-reading dismissals, whatever's
 * pending) and clears it on success, mirroring [PositionSyncWorker].
 */
class LibraryPrefsSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val cachedLibraryPrefsDao: CachedLibraryPrefsDao,
    private val pendingPatchDao: PendingLibraryPrefsPatchDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!network.isConfigured) return Result.retry()

        // Shared with LibraryPrefsRepository.patchPrefs's inline push -- without this lock, a
        // patch captured here could go stale mid-flight if a newer inline edit pushes and clears
        // the outbox first, and this worker's now-stale (smaller) push would silently overwrite
        // that newer edit back out since every field here is a full-value replace, not a diff.
        return LibraryPrefsRepository.outboxMutex.withLock {
            val pending = pendingPatchDao.get() ?: return@withLock Result.success()
            val patch = runCatching { Json.parseToJsonElement(pending.patchJson).jsonObject }.getOrNull()
                ?: run { pendingPatchDao.clear(); return@withLock Result.success() }

            try {
                val response = network.api.patchLibraryPrefs(patch)
                if (response.isSuccessful) {
                    // Re-merge into the cache in case a newer GET landed in between and didn't see
                    // this patch yet (fetchPrefsBlob already re-applies pending on top too, but this
                    // keeps the cache itself consistent once the outbox is actually cleared).
                    val cached = cachedLibraryPrefsDao.get()?.prefsJson
                        ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                        ?: JsonObject(emptyMap())
                    cachedLibraryPrefsDao.set(CachedLibraryPrefsEntity(prefsJson = JsonObject(cached + patch).toString()))
                    pendingPatchDao.clear()
                    Result.success()
                } else {
                    Result.retry()
                }
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
