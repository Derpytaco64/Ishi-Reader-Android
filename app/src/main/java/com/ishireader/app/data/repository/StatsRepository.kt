package com.ishireader.app.data.repository

import com.ishireader.app.data.model.UserStats
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatsRepository(private val network: NetworkModule) {

    suspend fun getStats(): ApiResult<UserStats> = withContext(Dispatchers.IO) {
        try {
            val response = network.api.stats()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure("Couldn't load stats (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
