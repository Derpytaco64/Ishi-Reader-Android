package com.ishireader.app.data.repository

import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.DailyReadingHistoryRequest
import com.ishireader.app.data.model.ReadingSpeedSample
import com.ishireader.app.data.model.ReadingSpeedSamplesRequest
import com.ishireader.app.data.model.ReadingTimeRequest
import com.ishireader.app.data.model.WordCountRequest
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network-first, same fidelity level as NotesRepository/CompletedReadsRepository (no local Room
 * outbox yet) -- offline buffering for these is deferred to the "finishing offline mode" pass,
 * matching how notes/completed-reads already behave in this app today. A dropped flush here just
 * means up to one PERSIST_INTERVAL's worth of active-seconds is lost if the network is down when
 * the app backgrounds, not silent data corruption.
 */
class ReadingTimerRepository(private val network: NetworkModule) {

    suspend fun getReadingTimeSeconds(manifestUrl: String): ApiResult<Double?> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getReadingTime(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.seconds)
            else ApiResult.Failure("Couldn't load reading time (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Whole-file overwrite, not an append. */
    suspend fun setReadingTimeSeconds(manifestUrl: String, seconds: Double): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.setReadingTime(ReadingTimeRequest(manifestUrl, seconds))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save reading time (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getWordCount(manifestUrl: String): ApiResult<Double?> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getWordCount(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.wordCount)
            else ApiResult.Failure("Couldn't load word count (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Persisted once, forever -- callers should only POST this the first time a book's word
     *  count is computed locally, never recompute/overwrite afterward. */
    suspend fun setWordCount(manifestUrl: String, wordCount: Double): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.setWordCount(WordCountRequest(manifestUrl, wordCount))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save word count (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** GET-only -- the server computes and caches this on a cache miss (mirrors
     *  fetchPageCountFromServer), so simply calling this is what makes the value exist. */
    suspend fun getPageCount(manifestUrl: String): ApiResult<Int?> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getPageCount(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.pageCount)
            else ApiResult.Failure("Couldn't load page count (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Global per-user buffer, not scoped to a single book -- carries the live WPM estimate
     *  across book switches, same as the website. */
    suspend fun getReadingSpeedSamples(): ApiResult<List<ReadingSpeedSample>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getReadingSpeedSamples()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.samples)
            else ApiResult.Failure("Couldn't load reading speed samples (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setReadingSpeedSamples(samples: List<ReadingSpeedSample>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.setReadingSpeedSamples(ReadingSpeedSamplesRequest(samples))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save reading speed samples (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getDailyReadingHistory(manifestUrl: String): ApiResult<List<DailyReadingBucket>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getDailyReadingHistory(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.buckets)
            else ApiResult.Failure("Couldn't load daily reading history (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun setDailyReadingHistory(manifestUrl: String, buckets: List<DailyReadingBucket>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.setDailyReadingHistory(DailyReadingHistoryRequest(manifestUrl, buckets))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save daily reading history (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
