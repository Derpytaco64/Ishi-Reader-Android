package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.PositionEntity
import com.ishireader.app.data.local.totalProgressionOf
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.json.Json

/**
 * Drains the position outbox: for each locally-pending book, compares local progress against
 * whatever the server currently has and keeps whichever is further along (ties/unknown
 * progression -- e.g. a format with no totalProgression -- favor the local write), per the
 * offline-sync plan's conflict rule. A failed comparison/push leaves the row pending and returns
 * [Result.retry], so WorkManager reschedules with backoff rather than losing the change.
 */
class PositionSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val positionDao: PositionDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The process may have been started solely to run this worker, before any screen has
        // re-configured the server URL from saved prefs -- retry rather than crash on network.api.
        if (!network.isConfigured) return Result.retry()

        val pending = positionDao.getPending()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (entity in pending) {
            if (!syncOne(entity)) anyFailed = true
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun syncOne(local: PositionEntity): Boolean {
        return try {
            val response = network.api.getPosition(local.manifestUrl)
            val serverLocator = response.takeIf { it.isSuccessful }?.body()?.locator
            val serverProgression = totalProgressionOf(serverLocator)

            if (serverLocator != null && serverProgression != null &&
                (local.progression == null || serverProgression > local.progression)
            ) {
                // Server is further along -- adopt it locally instead of overwriting server progress.
                positionDao.upsert(
                    local.copy(
                        locatorJson = serverLocator.toString(),
                        progression = serverProgression,
                        pendingSync = false
                    )
                )
                true
            } else {
                // Local is further along (or the comparison is inconclusive) -- push it.
                val locatorElement = Json.parseToJsonElement(local.locatorJson)
                val postResponse = network.api.setPosition(PositionRequest(local.manifestUrl, locatorElement))
                if (postResponse.isSuccessful) {
                    positionDao.upsert(local.copy(pendingSync = false))
                }
                postResponse.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
