package com.ishireader.app.data.repository

import com.ishireader.app.data.local.CachedUserStatsDao
import com.ishireader.app.data.local.CachedUserStatsEntity
import com.ishireader.app.data.model.UserStats
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whole-library aggregate is server-computed (there's no local mutation path for it, unlike
 * position/prefs), so "offline" here just means falling back to the last successful fetch instead
 * of showing nothing -- the numbers themselves resync automatically next time this is called with
 * connectivity (StatsDialog re-fetches every time it's opened, see MainTabsScreen).
 */
class StatsRepository(
    private val network: NetworkModule,
    private val cachedUserStatsDao: CachedUserStatsDao
) {

    suspend fun getStats(): ApiResult<UserStats> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.stats()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                cachedUserStatsDao.set(body.toEntity())
                ApiResult.Success(body)
            } else {
                cachedStats()?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure("Couldn't load stats (${response.code()})")
            }
        } catch (e: Exception) {
            cachedStats()?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(e.message ?: "Network error", isNetworkError = true)
        }
    }

    private suspend fun cachedStats(): UserStats? = cachedUserStatsDao.get()?.toUserStats()
}

private fun UserStats.toEntity() = CachedUserStatsEntity(
    booksInLibrary = booksInLibrary,
    booksStarted = booksStarted,
    booksFinished = booksFinished,
    totalReadingSeconds = totalReadingSeconds,
    totalWordsRead = totalWordsRead,
    averageWpm = averageWpm,
    currentStreakDays = currentStreakDays,
    highlightsCount = highlightsCount,
    bookmarksCount = bookmarksCount,
    notesCount = notesCount,
    audiobooksInLibrary = audiobooksInLibrary,
    audiobooksStarted = audiobooksStarted,
    audiobooksFinished = audiobooksFinished,
    totalListeningSeconds = totalListeningSeconds
)

private fun CachedUserStatsEntity.toUserStats() = UserStats(
    booksInLibrary = booksInLibrary,
    booksStarted = booksStarted,
    booksFinished = booksFinished,
    totalReadingSeconds = totalReadingSeconds,
    totalWordsRead = totalWordsRead,
    averageWpm = averageWpm,
    currentStreakDays = currentStreakDays,
    highlightsCount = highlightsCount,
    bookmarksCount = bookmarksCount,
    notesCount = notesCount,
    audiobooksInLibrary = audiobooksInLibrary,
    audiobooksStarted = audiobooksStarted,
    audiobooksFinished = audiobooksFinished,
    totalListeningSeconds = totalListeningSeconds
)
