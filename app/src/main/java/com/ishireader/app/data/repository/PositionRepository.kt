package com.ishireader.app.data.repository

import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.PositionEntity
import com.ishireader.app.data.local.totalProgressionOf
import com.ishireader.app.data.sync.PositionReconciler
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Local-first: reads and writes go straight to Room, which is the on-device source of truth for
 * reading position -- never the network directly, so opening/advancing a book never blocks on or
 * fails because of connectivity. [setPosition] queues the write for background sync instead of
 * pushing it inline; see SyncScheduler/PositionSyncWorker for how (and when) that reaches the
 * server, including the pending-local-write-always-wins conflict rule.
 */
class PositionRepository(
    private val positionDao: PositionDao,
    private val syncScheduler: SyncScheduler,
    private val reconciler: PositionReconciler
) {

    fun observePosition(manifestUrl: String): Flow<JsonElement?> =
        positionDao.observe(manifestUrl).map { it?.toLocatorJson() }

    /** Returns the best-known Locator JSON for this book, or null if it's never been opened
     *  anywhere. Always tries a quick server refresh first (see [refreshFromServer]) so callers
     *  showing many books at once (Home's Continue Reading, book detail) reflect a position set
     *  elsewhere -- web, another device -- not just whatever this device happened to write itself.
     *  Safe offline: the refresh times out quickly and this falls back to whatever's in Room. */
    suspend fun getPosition(manifestUrl: String): JsonElement? = withContext(Dispatchers.IO) {
        refreshFromServer(manifestUrl)
        positionDao.get(manifestUrl)?.toLocatorJson()
    }

    /** Saves a Locator (as produced by the Readium navigator's `Locator.toJSON()`). */
    suspend fun setPosition(manifestUrl: String, locator: JsonElement) = withContext(Dispatchers.IO) {
        val locatorJson = locator.toString()
        positionDao.upsert(
            PositionEntity(
                manifestUrl = manifestUrl,
                locatorJson = locatorJson,
                progression = totalProgressionOf(locator),
                updatedAtMillis = System.currentTimeMillis(),
                pendingSync = true
            )
        )
        syncScheduler.schedulePositionSync()
    }

    /** Best-effort, time-boxed check against the server -- used when opening a book so a position
     *  saved elsewhere (web, another device) shows up on the very first open, not just the second.
     *  A book that's never been read on this device has no local row for the background sync
     *  worker to pick up (it only drains *pending* rows), so without this eager check the reader
     *  would silently start at position 0 until some other write created one to sync from. Safe to
     *  call while offline: on timeout or failure this just leaves Room (and the reader) as-is. */
    suspend fun refreshFromServer(manifestUrl: String): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(5_000) { reconciler.reconcile(manifestUrl) } ?: false
    }

    /** manifestUrl -> when this device last saved a position for it, for every book ever opened
     *  here. Book.lastReadAt (from /api/books) is server-truth -- the position file's mtime --
     *  snapshotted into the Room book-list cache as of the last successful fetch; a book read
     *  while offline advances this table but can't touch that snapshot, so Home's Continue
     *  Reading/Last Series Read would otherwise look frozen until the next successful sync. See
     *  HomeViewModel, which takes the max of the two per book instead of trusting lastReadAt alone. */
    suspend fun localLastReadTimestamps(): Map<String, Long> = withContext(Dispatchers.IO) {
        positionDao.getAll().associate { it.manifestUrl to it.updatedAtMillis }
    }
}

private fun PositionEntity.toLocatorJson(): JsonElement? =
    runCatching { Json.parseToJsonElement(locatorJson) }.getOrNull()
