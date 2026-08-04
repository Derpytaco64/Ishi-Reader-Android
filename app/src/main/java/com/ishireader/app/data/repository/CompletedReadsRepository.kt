package com.ishireader.app.data.repository

import com.ishireader.app.data.model.StoredCompletedReadTime
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompletedReadsRepository(private val network: NetworkModule) {

    suspend fun getCompletedReadTimes(manifestUrl: String): ApiResult<List<StoredCompletedReadTime>> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.getCompletedReadTimes(manifestUrl)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.items)
            } else {
                ApiResult.Failure("Couldn't load completed reads (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
