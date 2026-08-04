package com.ishireader.app.data.repository

import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.PositionEntity
import com.ishireader.app.data.local.totalProgressionOf
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Local-first: reads and writes go straight to Room, which is the on-device source of truth for
 * reading position -- never the network directly, so opening/advancing a book never blocks on or
 * fails because of connectivity. [setPosition] queues the write for background sync instead of
 * pushing it inline; see SyncScheduler/PositionSyncWorker for how (and when) that reaches the
 * server, including the further-progress-wins conflict rule.
 */
class PositionRepository(
    private val positionDao: PositionDao,
    private val syncScheduler: SyncScheduler
) {

    fun observePosition(manifestUrl: String): Flow<JsonElement?> =
        positionDao.observe(manifestUrl).map { it?.toLocatorJson() }

    /** Returns the last saved Locator JSON for this book, or null if it's never been opened. */
    suspend fun getPosition(manifestUrl: String): JsonElement? = withContext(Dispatchers.IO) {
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
}

private fun PositionEntity.toLocatorJson(): JsonElement? =
    runCatching { Json.parseToJsonElement(locatorJson) }.getOrNull()
