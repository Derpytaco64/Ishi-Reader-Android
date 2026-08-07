package com.ishireader.app.data.repository

import com.ishireader.app.data.model.CompletedListenUpsertRequest
import com.ishireader.app.data.model.ListeningTimeData
import com.ishireader.app.data.model.ListeningTimeRequest
import com.ishireader.app.data.model.StoredCompletedListen
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Network-first, same fidelity level as ReadingTimerRepository -- no local Room outbox yet, a
 *  dropped flush just loses up to one PERSIST_INTERVAL's worth of listened seconds. */
class ListeningTimeRepository(private val network: NetworkModule) {

    suspend fun getListeningTime(manifestUrl: String): ApiResult<ListeningTimeData?> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getListeningTime(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.data)
            else ApiResult.Failure("Couldn't load listening time (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    /** Whole-file overwrite, not an append -- same convention as setReadingTimeSeconds. */
    suspend fun setListeningTime(manifestUrl: String, accumulatedSeconds: Double, startedAt: Double?): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = network.api.setListeningTime(ListeningTimeRequest(manifestUrl, accumulatedSeconds, startedAt))
                if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save listening time (${response.code()})")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        }

    suspend fun getCompletedListens(manifestUrl: String): ApiResult<List<StoredCompletedListen>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getCompletedListens(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body.items)
            else ApiResult.Failure("Couldn't load completed listens (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun saveCompletedListen(manifestUrl: String, item: StoredCompletedListen): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.upsertCompletedListen(CompletedListenUpsertRequest(manifestUrl, item))
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't save completed listen (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun deleteCompletedListen(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.deleteCompletedListen(manifestUrl, id)
            if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure("Couldn't delete completed listen (${response.code()})")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
