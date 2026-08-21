package com.ishireader.app.data.repository

import com.ishireader.app.data.local.ComicReadingProgressionCacheEntity
import com.ishireader.app.data.local.ComicReadingProgressionDao
import com.ishireader.app.data.model.ReadingProgressionResponse
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Caches Ishi-Read's /api/books/reading-progression response (reading direction + synthesized
 * chapter TOC source for comics -- see ComicToc.kt) so a comic opened offline still gets a
 * reading-direction default and a chapter title pill, instead of silently getting neither. Same
 * "quick server refresh, fall back to the local cache" shape as PositionRepository.getPosition:
 * safe offline (the refresh is time-boxed and any failure just falls through to Room), and a
 * comic that's never been opened with connectivity has nothing to fall back to, same as a
 * never-synced position.
 */
class ComicReadingProgressionRepository(
    private val network: NetworkModule,
    private val dao: ComicReadingProgressionDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getReadingProgression(manifestUrl: String): ReadingProgressionResponse? = withContext(Dispatchers.IO) {
        val fresh = runCatching {
            withTimeoutOrNull(5_000) { network.api.getReadingProgression(manifestUrl) }
        }.getOrNull()
        val body = fresh?.takeIf { it.isSuccessful }?.body()
        if (body != null) {
            dao.upsert(
                ComicReadingProgressionCacheEntity(
                    manifestUrl = manifestUrl,
                    responseJson = json.encodeToString(body),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            return@withContext body
        }

        dao.get(manifestUrl)?.let { cached ->
            runCatching { json.decodeFromString<ReadingProgressionResponse>(cached.responseJson) }.getOrNull()
        }
    }
}
