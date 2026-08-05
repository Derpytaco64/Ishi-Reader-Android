package com.ishireader.app.data.sync

import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.PositionEntity
import com.ishireader.app.data.local.totalProgressionOf
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.json.Json

/**
 * Compares one book's local position against the server's and keeps whichever was saved more
 * recently, writing the result to Room. Shared by [PositionSyncWorker] (draining the whole
 * pending outbox in the background) and by ReaderActivity's eager, best-effort check on open --
 * a book that's never been read on this device has no local row at all, so the background worker
 * (which only looks at *pending* rows) never picks it up; without an eager check, a
 * freshly-downloaded book always opens at position 0 until the *next* open, after a local write
 * finally exists to sync from.
 *
 * Recency, not furthest-progress, is the tie-breaker: this used to adopt whichever side had the
 * higher `locations.totalProgression`, which meant deliberately paging backwards to re-read a
 * chapter never stuck -- the next sync just re-adopted the server's older, further-along save and
 * silently snapped the reader back. Comparing PositionResponse.updatedAt (the position file's
 * server-side mtime) against PositionEntity.updatedAtMillis fixes that, at the cost of depending
 * on the two devices' clocks roughly agreeing -- acceptable since there's no vector-clock-style
 * alternative without much more machinery.
 */
class PositionReconciler(
    private val positionDao: PositionDao,
    private val network: NetworkModule
) {

    suspend fun reconcile(manifestUrl: String): Boolean {
        val local = positionDao.get(manifestUrl)
        return try {
            val response = network.api.getPosition(manifestUrl)
            val body = response.takeIf { it.isSuccessful }?.body()
            val serverLocator = body?.locator
            val serverUpdatedAt = body?.updatedAt

            if (serverLocator != null && serverUpdatedAt != null &&
                (local == null || serverUpdatedAt > local.updatedAtMillis)
            ) {
                // Server was saved more recently -- adopt it locally instead of overwriting it.
                positionDao.upsert(
                    PositionEntity(
                        manifestUrl = manifestUrl,
                        locatorJson = serverLocator.toString(),
                        progression = totalProgressionOf(serverLocator),
                        updatedAtMillis = serverUpdatedAt,
                        pendingSync = false
                    )
                )
                true
            } else if (local != null && local.pendingSync) {
                // Local is more recent (or the server has nothing saved yet) -- push it.
                val locatorElement = Json.parseToJsonElement(local.locatorJson)
                val postResponse = network.api.setPosition(PositionRequest(manifestUrl, locatorElement))
                if (postResponse.isSuccessful) {
                    positionDao.upsert(local.copy(pendingSync = false))
                }
                postResponse.isSuccessful
            } else {
                // Nothing pending locally and the server isn't newer -- already in sync.
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
