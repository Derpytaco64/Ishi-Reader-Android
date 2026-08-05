package com.ishireader.app.data.sync

import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.PositionEntity
import com.ishireader.app.data.local.totalProgressionOf
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.json.Json

/**
 * Compares one book's local position against the server's and keeps whichever is further along
 * (per the offline-sync plan's conflict rule), writing the result to Room. Shared by
 * [PositionSyncWorker] (draining the whole pending outbox in the background) and by
 * ReaderActivity's eager, best-effort check on open -- a book that's never been read on this
 * device has no local row at all, so the background worker (which only looks at *pending* rows)
 * never picks it up; without an eager check, a freshly-downloaded book always opens at position 0
 * until the *next* open, after a local write finally exists to sync from.
 */
class PositionReconciler(
    private val positionDao: PositionDao,
    private val network: NetworkModule
) {

    suspend fun reconcile(manifestUrl: String): Boolean {
        val local = positionDao.get(manifestUrl)
        return try {
            val response = network.api.getPosition(manifestUrl)
            val serverLocator = response.takeIf { it.isSuccessful }?.body()?.locator
            val serverProgression = totalProgressionOf(serverLocator)

            if (serverLocator != null && serverProgression != null &&
                (local?.progression == null || serverProgression > local.progression)
            ) {
                // Server is further along -- adopt it locally instead of overwriting server progress.
                positionDao.upsert(
                    PositionEntity(
                        manifestUrl = manifestUrl,
                        locatorJson = serverLocator.toString(),
                        progression = serverProgression,
                        updatedAtMillis = System.currentTimeMillis(),
                        pendingSync = false
                    )
                )
                true
            } else if (local != null && local.pendingSync) {
                // Local is further along (or the comparison is inconclusive) -- push it.
                val locatorElement = Json.parseToJsonElement(local.locatorJson)
                val postResponse = network.api.setPosition(PositionRequest(manifestUrl, locatorElement))
                if (postResponse.isSuccessful) {
                    positionDao.upsert(local.copy(pendingSync = false))
                }
                postResponse.isSuccessful
            } else {
                // Nothing pending locally and the server isn't ahead -- already in sync.
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
