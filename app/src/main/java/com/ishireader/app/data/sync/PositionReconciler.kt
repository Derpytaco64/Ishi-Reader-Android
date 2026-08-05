package com.ishireader.app.data.sync

import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.PositionEntity
import com.ishireader.app.data.local.totalProgressionOf
import com.ishireader.app.data.model.PositionRequest
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.json.Json

/**
 * Compares one book's local position against the server's and decides which one sticks, writing
 * the result to Room. Shared by [PositionSyncWorker] (draining the whole pending outbox in the
 * background) and by ReaderActivity's eager, best-effort check on open -- a book that's never
 * been read on this device has no local row at all, so the background worker (which only looks
 * at *pending* rows) never picks it up; without an eager check, a freshly-downloaded book always
 * opens at position 0 until the *next* open, after a local write finally exists to sync from.
 *
 * The rule is: a pending local write always wins, no comparison needed. This used to adopt
 * whichever side had the higher `locations.totalProgression`, which meant deliberately paging
 * backwards to re-read a chapter never stuck -- the next sync just re-adopted the server's older,
 * further-along save and silently snapped the reader back. A later attempt fixed that by
 * comparing save timestamps instead (server mtime vs. [PositionEntity.updatedAtMillis]), but that
 * quietly depends on the phone's and server's clocks agreeing -- if the phone's clock runs behind
 * the server's even slightly, *every* reconcile call decides the server is "newer" and adopts it,
 * so a pending local write is silently dropped without ever reaching the server. Since the
 * server's position file also drives Book.lastReadAt (see Ishi-Read's getLastReadAt), that bug
 * presented as reading positions -- and Home's Last Series Read/Continue Reading, which are
 * derived from lastReadAt -- appearing permanently stuck while online. [PositionEntity.pendingSync]
 * is already the right, clock-free signal for "this device has an intentional unsynced write":
 * checking it first, unconditionally, means the only remaining question is whether *this* device
 * has something to push, never whose clock is right.
 */
class PositionReconciler(
    private val positionDao: PositionDao,
    private val network: NetworkModule
) {

    suspend fun reconcile(manifestUrl: String): Boolean {
        val local = positionDao.get(manifestUrl)
        return try {
            if (local != null && local.pendingSync) {
                val locatorElement = Json.parseToJsonElement(local.locatorJson)
                val postResponse = network.api.setPosition(PositionRequest(manifestUrl, locatorElement))
                if (postResponse.isSuccessful) {
                    positionDao.upsert(local.copy(pendingSync = false))
                }
                postResponse.isSuccessful
            } else {
                // Nothing pending on this device -- adopt whatever the server has, if anything
                // (read on another device or the website since this device's last sync).
                val response = network.api.getPosition(manifestUrl)
                val serverLocator = response.takeIf { it.isSuccessful }?.body()?.locator
                if (serverLocator != null) {
                    positionDao.upsert(
                        PositionEntity(
                            manifestUrl = manifestUrl,
                            locatorJson = serverLocator.toString(),
                            progression = totalProgressionOf(serverLocator),
                            updatedAtMillis = System.currentTimeMillis(),
                            pendingSync = false
                        )
                    )
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
